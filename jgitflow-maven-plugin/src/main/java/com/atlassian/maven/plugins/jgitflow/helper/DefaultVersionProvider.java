package com.atlassian.maven.plugins.jgitflow.helper;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.atlassian.maven.plugins.jgitflow.PrettyPrompter;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.JGitFlowReleaseException;

import com.google.common.collect.ImmutableMap;

import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.release.util.ReleaseUtil;
import org.apache.maven.shared.release.version.HotfixVersionInfo;
import org.apache.maven.shared.release.versions.DefaultVersionInfo;
import org.apache.maven.shared.release.versions.VersionParseException;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.StringUtils;

public class DefaultVersionProvider extends AbstractLogEnabled implements VersionProvider
{
    private final Map<String, Map<String, String>> releaseVersions;
    private final Map<String, Map<String, String>> developmentVersions;
    private final Map<String, Map<String, String>> hotfixVersions;
    private PrettyPrompter prompter;
    
    public DefaultVersionProvider()
    {
        this.releaseVersions = new HashMap<String, Map<String, String>>();
        this.developmentVersions = new HashMap<String, Map<String, String>>();
        this.hotfixVersions = new HashMap<String, Map<String, String>>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> getReleaseVersions(ProjectCacheKey cacheKey, List<MavenProject> reactorProjects, ReleaseContext ctx) throws JGitFlowReleaseException
    {
        //todo: add getOriginalVersions here to pre-pop
        if (!releaseVersions.containsKey(cacheKey.name()))
        {
            Map<String, String> versions = new HashMap<String, String>();

            MavenProject rootProject = ReleaseUtil.getRootProject(reactorProjects);

            if (ctx.isAutoVersionSubmodules() && ArtifactUtils.isSnapshot(rootProject.getVersion()))
            {
                String rootProjectId = ArtifactUtils.versionlessKey(rootProject.getGroupId(), rootProject.getArtifactId());

                String rootReleaseVersion = getReleaseVersion(ctx, rootProject);

                versions.put(rootProjectId, rootReleaseVersion);

                for (MavenProject subProject : reactorProjects)
                {
                    String subProjectId = ArtifactUtils.versionlessKey(subProject.getGroupId(), subProject.getArtifactId());
                    versions.put(subProjectId, rootReleaseVersion);
                }
            }
            else
            {
                for (MavenProject project : reactorProjects)
                {
                    String projectId = ArtifactUtils.versionlessKey(project.getGroupId(), project.getArtifactId());
                    String releaseVersion = getReleaseVersion(ctx, project);
                    versions.put(projectId, releaseVersion);
                }
            }

            releaseVersions.put(cacheKey.name(), versions);
        }

        return ImmutableMap.copyOf(releaseVersions.get(cacheKey.name()));

    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> getHotfixVersions(ProjectCacheKey cacheKey, List<MavenProject> reactorProjects, ReleaseContext ctx, Map<String, String> lastReleaseVersions) throws JGitFlowReleaseException
    {
        //todo: add getOriginalVersions here to pre-pop
        if (!hotfixVersions.containsKey(cacheKey.name()))
        {
            Map<String, String> versions = new HashMap<String, String>();

            MavenProject rootProject = ReleaseUtil.getRootProject(reactorProjects);

            if (ctx.isAutoVersionSubmodules())
            {
                String rootProjectId = ArtifactUtils.versionlessKey(rootProject.getGroupId(), rootProject.getArtifactId());

                String lastRootRelease = "";

                if (null != lastReleaseVersions)
                {
                    lastRootRelease = lastReleaseVersions.get(rootProjectId);
                }

                String rootHotfixVersion = getHotfixVersion(ctx, rootProject, lastRootRelease);

                versions.put(rootProjectId, rootHotfixVersion);

                for (MavenProject subProject : reactorProjects)
                {
                    String subProjectId = ArtifactUtils.versionlessKey(subProject.getGroupId(), subProject.getArtifactId());
                    versions.put(subProjectId, rootHotfixVersion);
                }
            }
            else
            {
                for (MavenProject project : reactorProjects)
                {
                    String projectId = ArtifactUtils.versionlessKey(project.getGroupId(), project.getArtifactId());
                    String lastRelease = "";

                    if (null != lastReleaseVersions)
                    {
                        lastRelease = lastReleaseVersions.get(projectId);
                    }

                    String hotfixVersion = getHotfixVersion(ctx, project, lastRelease);
                    versions.put(projectId, hotfixVersion);
                }
            }

            hotfixVersions.put(cacheKey.name(), versions);
        }

        return ImmutableMap.copyOf(hotfixVersions.get(cacheKey.name()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> getDevelopmentVersions(ProjectCacheKey cacheKey, List<MavenProject> reactorProjects, ReleaseContext ctx) throws JGitFlowReleaseException
    {
        //todo: add getOriginalVersions here to pre-pop
        if (!developmentVersions.containsKey(cacheKey.name()))
        {
            Map<String, String> versions = new HashMap<String, String>();

            MavenProject rootProject = ReleaseUtil.getRootProject(reactorProjects);
            if (ctx.isAutoVersionSubmodules())
            {
                String rootProjectId = ArtifactUtils.versionlessKey(rootProject.getGroupId(), rootProject.getArtifactId());
                String rootDevelopmentVersion = getDevelopmentVersion(ctx, rootProject);

                versions.put(rootProjectId, rootDevelopmentVersion);

                for (MavenProject subProject : reactorProjects)
                {
                    String subProjectId = ArtifactUtils.versionlessKey(subProject.getGroupId(), subProject.getArtifactId());
                    versions.put(subProjectId, rootDevelopmentVersion);
                }
            }
            else
            {
                for (MavenProject project : reactorProjects)
                {
                    String projectId = ArtifactUtils.versionlessKey(project.getGroupId(), project.getArtifactId());
                    String developmentVersion = getDevelopmentVersion(ctx, project);
                    versions.put(projectId, developmentVersion);
                }
            }

            developmentVersions.put(cacheKey.name(), versions);
        }

        return ImmutableMap.copyOf(developmentVersions.get(cacheKey.name()));
    }

    protected String getReleaseVersion(ReleaseContext ctx, MavenProject rootProject) throws JGitFlowReleaseException
    {
        Logger log = getLogger();
        String defaultVersion = rootProject.getVersion();

        if (log.isDebugEnabled())
        {
            log.debug("calculating release version for " + rootProject.getGroupId() + ":" + rootProject.getArtifactId());
            log.debug("defaultVersion is currently: " + defaultVersion);
        }

        if (StringUtils.isNotBlank(ctx.getDefaultReleaseVersion()))
        {
            defaultVersion = ctx.getDefaultReleaseVersion();

            if (log.isDebugEnabled())
            {
                log.debug("(ctx change) defaultVersion is currently: " + defaultVersion);
            }
        }

        String suggestedVersion = null;
        String releaseVersion = defaultVersion;

        if (log.isDebugEnabled())
        {
            log.debug("releaseVersion is currently: " + releaseVersion);
        }

        while (null == releaseVersion || ArtifactUtils.isSnapshot(releaseVersion))
        {
            if (log.isDebugEnabled())
            {
                log.debug("looping until we find a non-snapshot version...");
            }

            DefaultVersionInfo info = null;
            try
            {
                info = new DefaultVersionInfo(rootProject.getVersion());
            }
            catch (VersionParseException e)
            {
                if (ctx.isInteractive())
                {
                    try
                    {
                        info = new DefaultVersionInfo("1.0");
                    }
                    catch (VersionParseException e1)
                    {
                        throw new JGitFlowReleaseException("error parsing 1.0 version!!!", e1);
                    }
                }
                else
                {
                    throw new JGitFlowReleaseException("error parsing release version: " + e.getMessage(), e);
                }
            }

            suggestedVersion = info.getReleaseVersionString();

            if (log.isDebugEnabled())
            {
                log.debug("suggestedVersion: " + suggestedVersion);
            }

            if (ctx.isInteractive())
            {
                String message = MessageFormat.format("What is the release version for \"{0}\"? ({1})", rootProject.getName(), ArtifactUtils.versionlessKey(rootProject.getGroupId(), rootProject.getArtifactId()));
                try
                {
                    releaseVersion = prompter.promptNotBlank(message, suggestedVersion);
                }
                catch (PrompterException e)
                {
                    throw new JGitFlowReleaseException("Error reading version from command line " + e.getMessage(), e);
                }
            }
            else
            {
                releaseVersion = suggestedVersion;

                if (log.isDebugEnabled())
                {
                    log.debug("setting release version to suggested version: " + suggestedVersion);
                }
            }

        }

        return releaseVersion;
    }

    protected String getHotfixVersion(ReleaseContext ctx, MavenProject rootProject, String lastRelease) throws JGitFlowReleaseException
    {
        final Logger log = getLogger();

        final String projectVersion = rootProject.getVersion();

        if (log.isDebugEnabled())
        {
            log.debug("calculating hotfix version for " + rootProject.getGroupId() + ":" + rootProject.getArtifactId());
            log.debug("projectVersion is " + projectVersion);
            log.debug("lastRelease is " + lastRelease);
        }

        // Get a value supplied by external configuration
        String suggestedVersion = ctx.getDefaultReleaseVersion( );
        boolean isUserDefined = false;

        if (StringUtils.isNotBlank(suggestedVersion) && !ArtifactUtils.isSnapshot(suggestedVersion))
        {
            isUserDefined = true;

            if (log.isDebugEnabled())
            {
                log.debug("(user defined) suggestedVersion is currently: " + suggestedVersion);
            }
        }
        else
        {
            suggestedVersion = null;

            if (StringUtils.isNotBlank(lastRelease) && !ArtifactUtils.isSnapshot(lastRelease))
            {
                try
                {
                    DefaultVersionInfo projectInfo = new DefaultVersionInfo(projectVersion);
                    DefaultVersionInfo lastReleaseInfo = new DefaultVersionInfo(lastRelease);

                    String higherVersion = projectVersion;

                    if (projectInfo.isSnapshot())
                    {
                        higherVersion = lastRelease;
                    }
                    else if (projectInfo.compareTo(lastReleaseInfo) < 1)
                    {
                        higherVersion = lastRelease;
                    }

                    final HotfixVersionInfo hotfixInfo = new HotfixVersionInfo(higherVersion);
                    suggestedVersion = hotfixInfo.getHotfixVersionString();

                    if (log.isDebugEnabled())
                    {
                        log.debug("lastRelease is " + lastRelease);
                        log.debug("(lastRelease based) suggestedVersion is currently: " + suggestedVersion);
                    }
                }
                catch (VersionParseException e)
                {
                    //just ignore
                }
            }
            else
            {
                try
                {
                    final HotfixVersionInfo hotfixInfo = new HotfixVersionInfo(projectVersion);
                    suggestedVersion = hotfixInfo.getHotfixVersionString();

                    if (log.isDebugEnabled())
                    {
                        log.debug("(projectVersion based) suggestedVersion is currently: " + suggestedVersion);
                    }
                }
                catch (VersionParseException e)
                {
                    //ignore
                }
            }
        }

        // Fixup project version, if it is a snapshot, in such a case decrement the snapshot version
        while (null == suggestedVersion || ArtifactUtils.isSnapshot(suggestedVersion))
        {
            HotfixVersionInfo info = null;
            try
            {
                info = new HotfixVersionInfo(projectVersion);
            }
            catch (VersionParseException e)
            {
                if (ctx.isInteractive())
                {
                    try
                    {
                        info = new HotfixVersionInfo("2.0");
                    }
                    catch (VersionParseException e1)
                    {
                        throw new JGitFlowReleaseException("error parsing 2.0 version!!!", e1);
                    }
                }
                else
                {
                    throw new JGitFlowReleaseException("error parsing release version: " + e.getMessage(), e);
                }
            }

            suggestedVersion = info.getDecrementedHotfixVersionString();

            if (log.isDebugEnabled())
            {
                log.debug("(projectVersion decremented) suggestedVersion is currently: " + suggestedVersion);
            }
        }

        // For user defined values do not prompt
        String hotfixVersion = isUserDefined ? suggestedVersion : null;

        while (null == hotfixVersion || ArtifactUtils.isSnapshot(hotfixVersion))
        {
            if (ctx.isInteractive())
            {
                String message = MessageFormat.format("What is the hotfix version for \"{0}\"? ({1})", rootProject.getName(), ArtifactUtils.versionlessKey(rootProject.getGroupId(), rootProject.getArtifactId()));
                try
                {
                    hotfixVersion = prompter.promptNotBlank(message, suggestedVersion);
                }
                catch (PrompterException e)
                {
                    throw new JGitFlowReleaseException("Error reading version from command line " + e.getMessage(), e);
                }
            }
            else
            {
                hotfixVersion = suggestedVersion;
            }

        }

        if (log.isDebugEnabled())
        {
            log.debug("hotfixVersion is " + hotfixVersion);
        }

        return hotfixVersion;
    }

    protected String getDevelopmentVersion(ReleaseContext ctx, MavenProject rootProject) throws JGitFlowReleaseException
    {
        String defaultVersion = rootProject.getVersion();

        if (StringUtils.isNotBlank(ctx.getDefaultDevelopmentVersion()))
        {
            defaultVersion = ctx.getDefaultDevelopmentVersion();
        }

        String suggestedVersion = null;
        String developmentVersion = defaultVersion;

        while (null == developmentVersion || !ArtifactUtils.isSnapshot(developmentVersion))
        {
            DefaultVersionInfo info = null;
            try
            {
                info = new DefaultVersionInfo(rootProject.getVersion());
            }
            catch (VersionParseException e)
            {
                if (ctx.isInteractive())
                {
                    try
                    {
                        info = new DefaultVersionInfo("1.0");
                    }
                    catch (VersionParseException e1)
                    {
                        throw new JGitFlowReleaseException("error parsing 1.0 version!!!", e1);
                    }
                }
                else
                {
                    throw new JGitFlowReleaseException("error parsing development version: " + e.getMessage(), e);
                }
            }

            suggestedVersion = info.getNextVersion().getSnapshotVersionString();

            if (ctx.isInteractive())
            {
                String message = MessageFormat.format("What is the development version for \"{0}\"? ({1})", rootProject.getName(), ArtifactUtils.versionlessKey(rootProject.getGroupId(), rootProject.getArtifactId()));
                try
                {
                    developmentVersion = prompter.promptNotBlank(message, suggestedVersion);
                }
                catch (PrompterException e)
                {
                    throw new JGitFlowReleaseException("Error reading version from command line " + e.getMessage(), e);
                }
            }
            else
            {
                developmentVersion = suggestedVersion;
            }

        }

        return developmentVersion;
    }
}
