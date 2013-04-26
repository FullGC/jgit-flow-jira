package com.atlassian.maven.plugins.jgitflow.helper;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.text.MessageFormat;
import java.util.*;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.exception.JGitFlowIOException;
import com.atlassian.jgitflow.core.util.GitHelper;
import com.atlassian.maven.plugins.jgitflow.PrettyPrompter;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.JGitFlowReleaseException;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.model.*;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;
import org.apache.maven.scm.provider.ScmUrlUtils;
import org.apache.maven.shared.release.util.ReleaseUtil;
import org.apache.maven.shared.release.version.HotfixVersionInfo;
import org.apache.maven.shared.release.versions.DefaultVersionInfo;
import org.apache.maven.shared.release.versions.VersionParseException;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.*;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

import static com.atlassian.maven.plugins.jgitflow.rewrite.ProjectChangeUtils.getNamespaceOrNull;
import static com.google.common.collect.Lists.newArrayList;

/**
 * @since version
 */
public class DefaultProjectHelper extends AbstractLogEnabled implements ProjectHelper
{
    private static final String ls = System.getProperty("line.separator");
    
    private PrettyPrompter prompter;
    private ArtifactFactory artifactFactory;
    private Map<String,String> originalVersions;
    private Map<String,String> releaseVersions;
    private Map<String,String> developmentVersions;
    private Map<String,String> hotfixVersions;
    
    @Override
    public String getReleaseVersion(ReleaseContext ctx, MavenProject rootProject) throws JGitFlowReleaseException
    {
        String defaultVersion = rootProject.getVersion();
        
        if (StringUtils.isNotBlank(ctx.getDefaultReleaseVersion()))
        {
            defaultVersion = ctx.getDefaultReleaseVersion();
        }

        String suggestedVersion = null;
        String releaseVersion = defaultVersion;
        
        while(null == releaseVersion || ArtifactUtils.isSnapshot(releaseVersion))
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
                    throw new JGitFlowReleaseException("error parsing release version: " + e.getMessage(),e);
                }
            }
            
            suggestedVersion = info.getReleaseVersionString();

            if(ctx.isInteractive())
            {
                String message = MessageFormat.format("What is the release version for \"{0}\"? ({1})",rootProject.getName(), ArtifactUtils.versionlessKey(rootProject.getGroupId(), rootProject.getArtifactId()));
                try
                {
                    releaseVersion = prompter.promptNotBlank(message,suggestedVersion);
                }
                catch (PrompterException e)
                {
                    throw new JGitFlowReleaseException("Error reading version from command line " + e.getMessage(),e);
                }
            }
            else
            {
                releaseVersion = suggestedVersion;
            }
            
        }
        
        return releaseVersion;
    }

    @Override
    public String getHotfixVersion(ReleaseContext ctx, MavenProject rootProject, String lastRelease) throws JGitFlowReleaseException
    {
        String defaultVersion = rootProject.getVersion();

        HotfixVersionInfo hotfixInfo = null;
        if (StringUtils.isNotBlank(lastRelease) && !ArtifactUtils.isSnapshot(lastRelease))
        {
            try
            {
                hotfixInfo = new HotfixVersionInfo(lastRelease);
                defaultVersion = hotfixInfo.getHotfixVersionString();
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
                hotfixInfo = new HotfixVersionInfo(rootProject.getVersion());
                defaultVersion = hotfixInfo.getDecrementedHotfixVersionString();
            }
            catch (VersionParseException e)
            {
                //ignore
            }
        }

        String suggestedVersion = defaultVersion;
        String hotfixVersion = null;

        while(null == suggestedVersion || ArtifactUtils.isSnapshot(suggestedVersion))
        {
            HotfixVersionInfo info = null;
            try
            {
                info = new HotfixVersionInfo(rootProject.getVersion());
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
                    throw new JGitFlowReleaseException("error parsing release version: " + e.getMessage(),e);
                }
            }

            suggestedVersion = info.getDecrementedHotfixVersionString();
        }

        while(null == hotfixVersion || ArtifactUtils.isSnapshot(hotfixVersion))
        {
            if(ctx.isInteractive())
            {
                String message = MessageFormat.format("What is the hotfix version for \"{0}\"? ({1})",rootProject.getName(), ArtifactUtils.versionlessKey(rootProject.getGroupId(), rootProject.getArtifactId()));
                try
                {
                    hotfixVersion = prompter.promptNotBlank(message,suggestedVersion);
                }
                catch (PrompterException e)
                {
                    throw new JGitFlowReleaseException("Error reading version from command line " + e.getMessage(),e);
                }
            }
            else
            {
                hotfixVersion = suggestedVersion;
            }

        }

        return hotfixVersion;
    }
    
    @Override
    public String getDevelopmentVersion(ReleaseContext ctx, MavenProject rootProject) throws JGitFlowReleaseException
    {
        String defaultVersion = rootProject.getVersion();

        if (StringUtils.isNotBlank(ctx.getDefaultDevelopmentVersion()))
        {
            defaultVersion = ctx.getDefaultDevelopmentVersion();
        }

        String suggestedVersion = null;
        String developmentVersion = defaultVersion;

        while(null == developmentVersion || !ArtifactUtils.isSnapshot(developmentVersion))
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
                    throw new JGitFlowReleaseException("error parsing development version: " + e.getMessage(),e);
                }
            }

            suggestedVersion = info.getNextVersion().getSnapshotVersionString();

            if(ctx.isInteractive())
            {
                String message = MessageFormat.format("What is the development version for \"{0}\"? ({1})",rootProject.getName(), ArtifactUtils.versionlessKey(rootProject.getGroupId(), rootProject.getArtifactId()));
                try
                {
                    developmentVersion = prompter.promptNotBlank(message,suggestedVersion);
                }
                catch (PrompterException e)
                {
                    throw new JGitFlowReleaseException("Error reading version from command line " + e.getMessage(),e);
                }
            }
            else
            {
                developmentVersion = suggestedVersion;
            }

        }

        return developmentVersion;
    }

    @Override
    public Map<String, String> getOriginalVersions(List<MavenProject> reactorProjects)
    {
        if(null == originalVersions)
        {
            this.originalVersions = new HashMap<String, String>();
            
            for(MavenProject project : reactorProjects)
            {
                originalVersions.put(ArtifactUtils.versionlessKey(project.getGroupId(),project.getArtifactId()),project.getVersion());
            }
        }
        
        return ImmutableMap.copyOf(originalVersions);
    }

    @Override
    public Map<String, String> getReleaseVersions(List<MavenProject> reactorProjects, ReleaseContext ctx) throws JGitFlowReleaseException
    {
        if(null == releaseVersions)
        {
            this.releaseVersions = new HashMap<String, String>();

            MavenProject rootProject = ReleaseUtil.getRootProject(reactorProjects);
            
            if(ctx.isAutoVersionSubmodules() && ArtifactUtils.isSnapshot(rootProject.getVersion()))
            {
                String rootProjectId = ArtifactUtils.versionlessKey(rootProject.getGroupId(),rootProject.getArtifactId());
                String rootReleaseVersion = getReleaseVersion(ctx,rootProject);
                
                releaseVersions.put(rootProjectId,rootReleaseVersion);
                
                for(MavenProject subProject : reactorProjects)
                {
                    String subProjectId = ArtifactUtils.versionlessKey(subProject.getGroupId(),subProject.getArtifactId());
                    releaseVersions.put(subProjectId,rootReleaseVersion);
                }
            }
            else
            {
                for (MavenProject project : reactorProjects)
                {
                    String projectId = ArtifactUtils.versionlessKey(project.getGroupId(),project.getArtifactId());
                    String releaseVersion = getReleaseVersion(ctx,project);
                    releaseVersions.put(projectId,releaseVersion);
                }
            }
        }
        
        return ImmutableMap.copyOf(releaseVersions);
        
    }

    @Override
    public Map<String, String> getHotfixVersions(List<MavenProject> reactorProjects, ReleaseContext ctx, Map<String,String> lastReleaseVersions) throws JGitFlowReleaseException
    {
        if(null == hotfixVersions)
        {
            this.hotfixVersions = new HashMap<String, String>();

            MavenProject rootProject = ReleaseUtil.getRootProject(reactorProjects);
            
            if(ctx.isAutoVersionSubmodules())
            {
                String rootProjectId = ArtifactUtils.versionlessKey(rootProject.getGroupId(),rootProject.getArtifactId());

                String lastRootRelease = "";

                if(null != lastReleaseVersions)
                {
                    lastRootRelease = lastReleaseVersions.get(rootProjectId);
                }
                
                String rootHotfixVersion = getHotfixVersion(ctx,rootProject,lastRootRelease);

                hotfixVersions.put(rootProjectId,rootHotfixVersion);

                for(MavenProject subProject : reactorProjects)
                {
                    String subProjectId = ArtifactUtils.versionlessKey(subProject.getGroupId(),subProject.getArtifactId());
                    hotfixVersions.put(subProjectId,rootHotfixVersion);
                }
            }
            else
            {
                for (MavenProject project : reactorProjects)
                {
                    String projectId = ArtifactUtils.versionlessKey(project.getGroupId(),project.getArtifactId());
                    String lastRelease = "";

                    if(null != lastReleaseVersions)
                    {
                        lastRelease = lastReleaseVersions.get(projectId);
                    }

                    String hotfixVersion = getHotfixVersion(ctx, project, lastRelease);
                    hotfixVersions.put(projectId,hotfixVersion);
                }
            }
        }

        return ImmutableMap.copyOf(hotfixVersions);
    }

    @Override
    public Map<String, String> getDevelopmentVersions(List<MavenProject> reactorProjects, ReleaseContext ctx) throws JGitFlowReleaseException
    {
        if(null == developmentVersions)
        {
            this.developmentVersions = new HashMap<String, String>();

            MavenProject rootProject = ReleaseUtil.getRootProject(reactorProjects);
            if(ctx.isAutoVersionSubmodules())
            {
                String rootProjectId = ArtifactUtils.versionlessKey(rootProject.getGroupId(),rootProject.getArtifactId());
                String rootDevelopmentVersion = getDevelopmentVersion(ctx,rootProject);

                developmentVersions.put(rootProjectId,rootDevelopmentVersion);

                for(MavenProject subProject : reactorProjects)
                {
                    String subProjectId = ArtifactUtils.versionlessKey(subProject.getGroupId(),subProject.getArtifactId());
                    developmentVersions.put(subProjectId,rootDevelopmentVersion);
                }
            }
            else
            {
                for (MavenProject project : reactorProjects)
                {
                    String projectId = ArtifactUtils.versionlessKey(project.getGroupId(),project.getArtifactId());
                    String developmentVersion = getDevelopmentVersion(ctx, project);
                    developmentVersions.put(projectId,developmentVersion);
                }
            }
        }

        return ImmutableMap.copyOf(developmentVersions);
    }

    @Override
    public void ensureOrigin(List<MavenProject> reactorProjects, JGitFlow flow) throws JGitFlowReleaseException
    {
        getLogger().info("ensuring origin exists...");
        boolean foundGitScm = false;
        for (MavenProject project : reactorProjects)
        {
            Scm scm = project.getScm();
            if(null != scm)
            {
                File pomFile = project.getFile();

                if(null == pomFile || !pomFile.exists() || !pomFile.canRead())
                {
                    String pomPath = (null == pomFile) ? "null" : pomFile.getAbsolutePath();

                    throw new JGitFlowReleaseException("pom file must be readable! " + pomPath);
                }

                String cleanScmUrl = "not defined";
                try
                {
                    String content = ReleaseUtil.readXmlFile(pomFile, ls);
                    SAXBuilder builder = new SAXBuilder();
                    Document document = builder.build(new StringReader( content ));
                    Element root = document.getRootElement();

                    Element scmElement = root.getChild("scm", getNamespaceOrNull(root));

                    if(null != scmElement)
                    {
                        String scmUrl = (null != scm.getDeveloperConnection()) ? scm.getDeveloperConnection() : scm.getConnection();

                        cleanScmUrl = scmUrl.substring(8);

                        if(!Strings.isNullOrEmpty(scmUrl) && "git".equals(ScmUrlUtils.getProvider(scmUrl)))
                        {
                            foundGitScm = true;
                            StoredConfig config = flow.git().getRepository().getConfig();
                            String originUrl = config.getString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME,"url");
                            if(Strings.isNullOrEmpty(originUrl) || !cleanScmUrl.equals(originUrl))
                            {
                                getLogger().info("adding origin from scm...");
                                config.setString(ConfigConstants.CONFIG_REMOTE_SECTION,Constants.DEFAULT_REMOTE_NAME,"url",cleanScmUrl);
                                config.setString(ConfigConstants.CONFIG_REMOTE_SECTION,Constants.DEFAULT_REMOTE_NAME,"fetch","+refs/heads/*:refs/remotes/origin/*");
                                config.setString(ConfigConstants.CONFIG_BRANCH_SECTION,flow.getMasterBranchName(),"remote",Constants.DEFAULT_REMOTE_NAME);
                                config.setString(ConfigConstants.CONFIG_BRANCH_SECTION,flow.getMasterBranchName(),"merge",Constants.R_HEADS + flow.getMasterBranchName());
                                config.setString(ConfigConstants.CONFIG_BRANCH_SECTION,flow.getDevelopBranchName(),"remote",Constants.DEFAULT_REMOTE_NAME);
                                config.setString(ConfigConstants.CONFIG_BRANCH_SECTION,flow.getDevelopBranchName(),"merge",Constants.R_HEADS + flow.getDevelopBranchName());
                                config.save();

                                try
                                {
                                    config.load();
                                    flow.git().fetch().setRemote(Constants.DEFAULT_REMOTE_NAME).call();
                                }
                                catch (Exception e)
                                {
                                    throw new JGitFlowReleaseException("error configuring remote git repo with url: " + cleanScmUrl, e);
                                }

                                getLogger().info("pulling changes from new origin...");
                                Ref originMaster = GitHelper.getRemoteBranch(flow.git(), flow.getMasterBranchName());
                                
                                if(null != originMaster)
                                {
                                    Ref localMaster = GitHelper.getLocalBranch(flow.git(),flow.getMasterBranchName());
                                    RefUpdate update = flow.git().getRepository().updateRef(localMaster.getName());
                                    update.setNewObjectId(originMaster.getObjectId());
                                    update.forceUpdate();
                                }

                                Ref originDevelop = GitHelper.getRemoteBranch(flow.git(),flow.getDevelopBranchName());
                                
                                if(null != originDevelop)
                                {
                                    Ref localDevelop = GitHelper.getLocalBranch(flow.git(),flow.getDevelopBranchName());
                                    RefUpdate updateDevelop = flow.git().getRepository().updateRef(localDevelop.getName());
                                    updateDevelop.setNewObjectId(originDevelop.getObjectId());
                                    updateDevelop.forceUpdate();
                                }

                                commitAllChanges(flow.git(),"committing changes from new origin");
                            }
                        }
                    }
                }
                catch (IOException e)
                {
                    throw new JGitFlowReleaseException("error configuring remote git repo with url: " + cleanScmUrl, e);
                }
                catch (JDOMException e)
                {
                    throw new JGitFlowReleaseException("error configuring remote git repo with url: " + cleanScmUrl, e);
                }
                catch (JGitFlowIOException e)
                {
                    throw new JGitFlowReleaseException("error configuring remote git repo with url: " + cleanScmUrl, e);
                }
            }
        }

        if(!foundGitScm)
        {
            throw new JGitFlowReleaseException("No GIT Scm url found in reactor projects!");
        }
    }

    @Override
    public void commitAllChanges(Git git, String message) throws JGitFlowReleaseException
    {
        try
        {
            git.add().addFilepattern(".").call();
            git.commit().setMessage(message).call();
        }
        catch (GitAPIException e)
        {
            throw new JGitFlowReleaseException("error committing pom changes: " + e.getMessage(), e);
        }

    }

    @Override
    public List<String> checkForNonReactorSnapshots(List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        List<String> snapshots = newArrayList();
        
        getLogger().info("Checking dependencies and plugins for snapshots ...");
        Map<String, String> originalReactorVersions = getOriginalVersions(reactorProjects);
        
        for(MavenProject project : reactorProjects)
        {
            snapshots.addAll(checkProjectForNonReactorSnapshots(project, originalReactorVersions));
        }
        
        return snapshots;
    }

    private List<String> checkProjectForNonReactorSnapshots(MavenProject project, Map<String, String> originalReactorVersions) throws JGitFlowReleaseException
    {
        List<String> snapshots = newArrayList();
        
        Map<String, Artifact> artifactMap = ArtifactUtils.artifactMapByVersionlessId(project.getArtifacts());
        if ( project.getParentArtifact() != null )
        {
            String parentSnap = checkArtifact(getArtifactFromMap(project.getParentArtifact(), artifactMap), originalReactorVersions, AT_PARENT);
            if(!Strings.isNullOrEmpty(parentSnap))
            {
                snapshots.add(parentSnap);
            }
        }
        
        //Dependencies
        try
        {
            Set<Artifact> dependencyArtifacts = project.createArtifacts( artifactFactory, null, null );
            snapshots.addAll(checkArtifacts(dependencyArtifacts, originalReactorVersions, AT_DEPENDENCY));
        }
        catch ( InvalidDependencyVersionException e )
        {
            throw new JGitFlowReleaseException("Failed to create dependency artifacts", e);
        }
        
        //Dependency Management
        DependencyManagement dmgnt = project.getDependencyManagement();
        if(null != dmgnt)
        {
            List<Dependency> mgntDependencies = dmgnt.getDependencies();
            if(null != mgntDependencies)
            {
                for(Dependency dep : mgntDependencies)
                {
                    String depSnap = checkArtifact(dep.getGroupId(), dep.getArtifactId(), dep.getVersion(), originalReactorVersions, AT_DEPENDENCY_MGNT);
                    if(!Strings.isNullOrEmpty(depSnap))
                    {
                        snapshots.add(depSnap);
                    }
                }
            }
        }
                
        //Plugins
        Set<Artifact> pluginArtifacts = project.getPluginArtifacts();
        snapshots.addAll(checkArtifacts(pluginArtifacts,originalReactorVersions,AT_PLUGIN));

        //Plugin Management
        PluginManagement pmgnt = project.getPluginManagement();
        if(null != pmgnt)
        {
            List<Plugin> mgntPlugins = pmgnt.getPlugins();
            
            for(Plugin plugin : mgntPlugins)
            {
                String pluginSnap = checkArtifact(plugin.getGroupId(), plugin.getArtifactId(), plugin.getVersion(), originalReactorVersions, AT_PLUGIN_MGNT);
                if(!Strings.isNullOrEmpty(pluginSnap))
                {
                    snapshots.add(pluginSnap);
                }
            }
        }

        //Reports
        Set<Artifact> reportArtifacts = project.getReportArtifacts();
        snapshots.addAll(checkArtifacts(reportArtifacts,originalReactorVersions,AT_REPORT));

        //Extensions
        Set<Artifact> extensionArtifacts = project.getExtensionArtifacts();
        snapshots.addAll(checkArtifacts(extensionArtifacts,originalReactorVersions,AT_EXTENSIONS));
        
        return snapshots;
    }

    private List<String> checkArtifacts(Set<Artifact> artifacts, Map<String, String> originalReactorVersions, String type)
    {
        List<String> snapshots = newArrayList();
        
        for(Artifact artifact : artifacts)
        {
            String snap = checkArtifact(artifact, originalReactorVersions, type);

            if(!Strings.isNullOrEmpty(snap))
            {
                snapshots.add(snap);
            }
        }
        
        return snapshots;
    }
    
    private String checkArtifact(Artifact artifact, Map<String, String> originalReactorVersions, String type)
    {
        String versionlessArtifactKey = ArtifactUtils.versionlessKey(artifact.getGroupId(), artifact.getArtifactId());

        boolean isSnapshot = (artifact.isSnapshot() && !artifact.getBaseVersion().equals(originalReactorVersions.get(versionlessArtifactKey)));
        
        if(isSnapshot)
        {
            return type + " - " + versionlessArtifactKey;
        }
        
        return null;
    }

    private String checkArtifact(String groupId, String artifactId, String version, Map<String, String> originalReactorVersions, String type)
    {
        String versionlessArtifactKey = ArtifactUtils.versionlessKey(groupId, artifactId);

        boolean isSnapshot = (ArtifactUtils.isSnapshot(version) && !version.equals(originalReactorVersions.get(versionlessArtifactKey)));

        if(isSnapshot)
        {
            return type + " - " + versionlessArtifactKey;
        }

        return null;
    }

    private Artifact getArtifactFromMap(Artifact originalArtifact, Map<String,Artifact> artifactMap) 
    {
        String versionlessId = ArtifactUtils.versionlessKey(originalArtifact);
        Artifact checkArtifact = artifactMap.get(versionlessId);

        if (checkArtifact == null)
        {
            checkArtifact = originalArtifact;
        }
        return checkArtifact;
    }
}
