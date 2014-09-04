package com.atlassian.maven.plugins.jgitflow.helper;

import java.util.List;
import java.util.Map;

import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.VersionType;
import com.atlassian.maven.plugins.jgitflow.exception.MavenJGitFlowException;
import com.atlassian.maven.plugins.jgitflow.exception.ProjectRewriteException;
import com.atlassian.maven.plugins.jgitflow.provider.ContextProvider;
import com.atlassian.maven.plugins.jgitflow.provider.ProjectCacheKey;
import com.atlassian.maven.plugins.jgitflow.provider.VersionProvider;
import com.atlassian.maven.plugins.jgitflow.rewrite.ProjectChangeset;
import com.atlassian.maven.plugins.jgitflow.rewrite.ProjectRewriter;

import com.google.common.base.Function;
import com.google.common.collect.Maps;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.AbstractLogEnabled;

import static com.atlassian.maven.plugins.jgitflow.rewrite.ArtifactReleaseVersionChange.artifactReleaseVersionChange;
import static com.atlassian.maven.plugins.jgitflow.rewrite.ParentReleaseVersionChange.parentReleaseVersionChange;
import static com.atlassian.maven.plugins.jgitflow.rewrite.ProjectReleaseVersionChange.projectReleaseVersionChange;

@Component(role = PomUpdater.class)
public class DefaultPomUpdater extends AbstractLogEnabled implements PomUpdater
{
    public static final String VERSION_DELIMITER = "-";
    @Requirement
    private VersionProvider versionProvider;

    @Requirement
    private ProjectRewriter projectRewriter;

    @Requirement
    private ContextProvider contextProvider;

    @Requirement
    private BranchHelper branchHelper;

    @Override
    public void removeSnapshotFromPomVersions(ProjectCacheKey cacheKey, final String versionSuffix, List<MavenProject> reactorProjects) throws MavenJGitFlowException
    {
        String fullBranchName = branchHelper.getCurrentBranchName();

        getLogger().info("(" + fullBranchName + ") removing snapshot from pom versions...");
        ReleaseContext ctx = contextProvider.getContext();

        Map<String, String> originalVersions = versionProvider.getOriginalVersions(cacheKey, reactorProjects);

        final String delimitedVersionSuffix = getDelimitedVersionSuffix(versionSuffix);

        Map<String, String> finalVersions = Maps.transformValues(originalVersions, new Function<String, String>()
        {
            @Override
            public String apply(String input)
            {
                if (input.endsWith(delimitedVersionSuffix + "-SNAPSHOT"))
                {
                    return StringUtils.substringBeforeLast(input, delimitedVersionSuffix + "-SNAPSHOT");
                }
                else
                {
                    return input;
                }
            }
        });

        doUpdate(reactorProjects, originalVersions, finalVersions, ctx.isUpdateDependencies());
    }

    @Override
    public void addSnapshotToPomVersions(ProjectCacheKey cacheKey, final VersionType versionType, final String versionSuffix, List<MavenProject> reactorProjects) throws MavenJGitFlowException
    {
        String fullBranchName = branchHelper.getCurrentBranchName();

        getLogger().info("(" + fullBranchName + ") adding snapshot to pom versions...");

        final ReleaseContext ctx = contextProvider.getContext();

        Map<String, String> originalVersions = versionProvider.getOriginalVersions(cacheKey, reactorProjects);

        Map<String, String> nonSnapshotVersions = versionProvider.getNextVersionsForType(versionType, cacheKey, reactorProjects);

        final String delimitedVersionSuffix = getDelimitedVersionSuffix(versionSuffix);

        Map<String, String> snapshotVersions = Maps.transformValues(nonSnapshotVersions, new Function<String, String>()
        {
            @Override
            public String apply(String input)
            {
                return input + delimitedVersionSuffix + "-SNAPSHOT";
            }
        });

        doUpdate(reactorProjects, originalVersions, snapshotVersions, ctx.isUpdateDependencies());
    }

    @Override
    public void copyPomVersionsFromProject(List<MavenProject> projectsToCopy, List<MavenProject> projectsToUpdate) throws MavenJGitFlowException
    {
        String fullBranchName = branchHelper.getCurrentBranchName();

        getLogger().info("(" + fullBranchName + ") copying pom versions...");

        ReleaseContext ctx = contextProvider.getContext();

        Map<String, String> originalVersions = versionProvider.getOriginalVersions(projectsToUpdate);
        Map<String, String> versionsToCopy = versionProvider.getOriginalVersions(projectsToCopy);

        doUpdate(projectsToUpdate, originalVersions, versionsToCopy, ctx.isUpdateDependencies());
    }

    @Override
    public void copyPomVersionsFromMap(Map<String, String> versionsToCopy, List<MavenProject> projectsToUpdate) throws MavenJGitFlowException
    {
        getLogger().info("copying pom versions...");

        ReleaseContext ctx = contextProvider.getContext();
        Map<String, String> originalVersions = versionProvider.getOriginalVersions(projectsToUpdate);

        doUpdate(projectsToUpdate, originalVersions, versionsToCopy, ctx.isUpdateDependencies());
    }

    @Override
    public void updatePomsWithNextDevelopmentVersion(ProjectCacheKey cacheKey, List<MavenProject> reactorProjects) throws MavenJGitFlowException
    {
        String fullBranchName = branchHelper.getCurrentBranchName();

        getLogger().info("(" + fullBranchName + ") updating poms with next development version...");

        ReleaseContext ctx = contextProvider.getContext();
        Map<String, String> originalVersions = versionProvider.getOriginalVersions(cacheKey, reactorProjects);
        Map<String, String> developmentVersions = versionProvider.getNextDevelopmentVersions(cacheKey, reactorProjects);

        doUpdate(reactorProjects, originalVersions, developmentVersions, ctx.isUpdateDependencies());
    }

    @Override
    public void addFeatureVersionToSnapshotVersions(ProjectCacheKey cacheKey, final String featureVersion, List<MavenProject> reactorProjects) throws MavenJGitFlowException
    {
        String fullBranchName = branchHelper.getCurrentBranchName();

        getLogger().info("(" + fullBranchName + ") adding feature versions to poms...");
        ReleaseContext ctx = contextProvider.getContext();

        Map<String, String> originalVersions = versionProvider.getOriginalVersions(cacheKey, reactorProjects);

        Map<String, String> featureSuffixedVersions = Maps.transformValues(originalVersions, new Function<String, String>()
        {
            @Override
            public String apply(String input)
            {
                if (input.endsWith("-SNAPSHOT"))
                {
                    return StringUtils.substringBeforeLast(input, "-SNAPSHOT") + "-" + featureVersion + "-SNAPSHOT";
                }
                else
                {
                    return input;
                }
            }
        });

        doUpdate(reactorProjects, originalVersions, featureSuffixedVersions, ctx.isUpdateDependencies());
    }

    @Override
    public void removeFeatureVersionFromSnapshotVersions(ProjectCacheKey cacheKey, final String featureVersion, List<MavenProject> reactorProjects) throws MavenJGitFlowException
    {
        String fullBranchName = branchHelper.getCurrentBranchName();

        getLogger().info("(" + fullBranchName + ") removing feature versions to poms...");
        ReleaseContext ctx = contextProvider.getContext();

        Map<String, String> originalVersions = versionProvider.getOriginalVersions(cacheKey, reactorProjects);

        final String featureSuffix = "-" + featureVersion + "-SNAPSHOT";

        Map<String, String> featureVersions = Maps.transformValues(originalVersions, new Function<String, String>()
        {
            @Override
            public String apply(String input)
            {
                if (input.endsWith(featureSuffix))
                {
                    return StringUtils.substringBeforeLast(input, featureSuffix) + "-SNAPSHOT";
                }
                else
                {
                    return input;
                }
            }
        });

        doUpdate(reactorProjects, originalVersions, featureVersions, ctx.isUpdateDependencies());
    }

    @Override
    public void removeSnapshotFromFeatureVersions(ProjectCacheKey cacheKey, final String featureVersion, List<MavenProject> reactorProjects) throws MavenJGitFlowException
    {
        ReleaseContext ctx = contextProvider.getContext();

        Map<String, String> originalVersions = versionProvider.getOriginalVersions(cacheKey, reactorProjects);

        Map<String, String> featureVersions = Maps.transformValues(originalVersions, new Function<String, String>()
        {
            @Override
            public String apply(String input)
            {
                if (input.endsWith("-SNAPSHOT"))
                {
                    return StringUtils.substringBeforeLast(input, "-SNAPSHOT") + "-" + featureVersion;
                }
                else
                {
                    return input;
                }
            }
        });

        doUpdate(reactorProjects, originalVersions, featureVersions, ctx.isUpdateDependencies());
    }

    protected void doUpdate(List<MavenProject> reactorProjects, Map<String, String> originalVersions, Map<String, String> finalVersions, boolean updateDependencies) throws MavenJGitFlowException
    {
        String fullBranchName = branchHelper.getCurrentBranchName();

        getLogger().info("(" + fullBranchName + ") updating poms for all projects...");
        if (!getLogger().isDebugEnabled())
        {
            getLogger().info("turn on debug logging with -X to see exact changes");
        }
        for (MavenProject project : reactorProjects)
        {
            ProjectChangeset changes = new ProjectChangeset()
                    .with(parentReleaseVersionChange(originalVersions, finalVersions))
                    .with(projectReleaseVersionChange(finalVersions))
                    .with(artifactReleaseVersionChange(originalVersions, finalVersions, updateDependencies));
            try
            {
                getLogger().info("(" + fullBranchName + ") updating pom for " + project.getName() + "...");

                projectRewriter.applyChanges(project, changes);

                logChanges(changes);
            }
            catch (ProjectRewriteException e)
            {
                throw new MavenJGitFlowException("Error updating poms with final versions", e);
            }
        }
    }

    protected void logChanges(ProjectChangeset changes)
    {
        if (getLogger().isDebugEnabled())
        {
            for (String desc : changes.getChangeDescriptionsOrSummaries())
            {
                getLogger().debug("  " + desc);
            }
        }
    }

    private String getDelimitedVersionSuffix(String versionSuffix)
    {
        return (StringUtils.isNotBlank(versionSuffix) && !versionSuffix.startsWith(VERSION_DELIMITER)) ? VERSION_DELIMITER + versionSuffix : versionSuffix;
    }
}
