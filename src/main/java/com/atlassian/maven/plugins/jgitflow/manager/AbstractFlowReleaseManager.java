package com.atlassian.maven.plugins.jgitflow.manager;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.exception.HotfixBranchExistsException;
import com.atlassian.jgitflow.core.exception.JGitFlowException;
import com.atlassian.jgitflow.core.exception.JGitFlowGitAPIException;
import com.atlassian.jgitflow.core.exception.ReleaseBranchExistsException;
import com.atlassian.jgitflow.core.util.GitHelper;
import com.atlassian.maven.plugins.jgitflow.MavenJGitFlowConfiguration;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.JGitFlowReleaseException;
import com.atlassian.maven.plugins.jgitflow.exception.ProjectRewriteException;
import com.atlassian.maven.plugins.jgitflow.exception.ReactorReloadException;
import com.atlassian.maven.plugins.jgitflow.exception.UnresolvedSnapshotsException;
import com.atlassian.maven.plugins.jgitflow.helper.MavenExecutionHelper;
import com.atlassian.maven.plugins.jgitflow.helper.ProjectHelper;
import com.atlassian.maven.plugins.jgitflow.rewrite.MavenProjectRewriter;
import com.atlassian.maven.plugins.jgitflow.rewrite.ProjectChangeset;

import com.google.common.base.Joiner;

import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.release.ReleaseExecutionException;
import org.apache.maven.shared.release.exec.MavenExecutorException;
import org.apache.maven.shared.release.util.ReleaseUtil;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.RefSpec;

import static com.atlassian.maven.plugins.jgitflow.rewrite.ArtifactReleaseVersionChange.artifactReleaseVersionChange;
import static com.atlassian.maven.plugins.jgitflow.rewrite.ParentReleaseVersionChange.parentReleaseVersionChange;
import static com.atlassian.maven.plugins.jgitflow.rewrite.ProjectReleaseVersionChange.projectReleaseVersionChange;
import static com.atlassian.maven.plugins.jgitflow.rewrite.ScmDefaultHeadTagChange.scmDefaultHeadTagChange;
import static com.atlassian.maven.plugins.jgitflow.rewrite.ScmDefaultTagChange.scmDefaultTagChange;

/**
 * @since version
 */
public abstract class AbstractFlowReleaseManager extends AbstractLogEnabled implements FlowReleaseManager
{
    protected static final String ls = System.getProperty("line.separator");
    private static final SecureRandom random = new SecureRandom();
    
    protected ProjectHelper projectHelper;
    protected MavenProjectRewriter projectRewriter;
    protected MavenExecutionHelper mavenExecutionHelper;
    protected MavenJGitFlowConfigManager configManager;

    protected String getReleaseLabel(String key, ReleaseContext ctx, List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        Map<String, String> releaseVersions = projectHelper.getReleaseVersions(key, reactorProjects, ctx);
        MavenProject rootProject = ReleaseUtil.getRootProject(reactorProjects);
        String rootProjectId = ArtifactUtils.versionlessKey(rootProject.getGroupId(), rootProject.getArtifactId());
        return releaseVersions.get(rootProjectId);
    }

    protected String getHotfixLabel(String key, ReleaseContext ctx, List<MavenProject> reactorProjects, MavenJGitFlowConfiguration config) throws JGitFlowReleaseException
    {
        Map<String, String> hotfixVersions = projectHelper.getHotfixVersions(key, reactorProjects, ctx, config.getLastReleaseVersions());
        MavenProject rootProject = ReleaseUtil.getRootProject(reactorProjects);
        String rootProjectId = ArtifactUtils.versionlessKey(rootProject.getGroupId(), rootProject.getArtifactId());
        return hotfixVersions.get(rootProjectId);
    }

    protected String getDevelopmentLabel(String key, ReleaseContext ctx, List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        Map<String, String> developmentVersions = projectHelper.getDevelopmentVersions(key, reactorProjects, ctx);
        MavenProject rootProject = ReleaseUtil.getRootProject(reactorProjects);
        String rootProjectId = ArtifactUtils.versionlessKey(rootProject.getGroupId(), rootProject.getArtifactId());
        return developmentVersions.get(rootProjectId);
    }
    
    protected String getFeatureStartName(ReleaseContext ctx, JGitFlow flow) throws JGitFlowReleaseException
    {
        return projectHelper.getFeatureStartName(ctx, flow);
    }
    
    protected String getFeatureFinishName(ReleaseContext ctx, JGitFlow flow) throws JGitFlowReleaseException
    {
        return projectHelper.getFeatureFinishName(ctx, flow);
    }

    protected void updatePomsWithReleaseVersion(String key, ReleaseContext ctx, List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        Map<String, String> originalVersions = projectHelper.getOriginalVersions(key, reactorProjects);
        Map<String, String> releaseVersions = projectHelper.getReleaseVersions(key, reactorProjects, ctx);

        getLogger().info("updating poms for all projects...");
        getLogger().info("turn on debug logging with -X to see exact changes");
        for (MavenProject project : reactorProjects)
        {
            ProjectChangeset changes = new ProjectChangeset()
                    .with(parentReleaseVersionChange(originalVersions, releaseVersions))
                    .with(projectReleaseVersionChange(releaseVersions))
                    .with(artifactReleaseVersionChange(originalVersions, releaseVersions, ctx.isUpdateDependencies()))
                    .with(scmDefaultTagChange(releaseVersions));
            try
            {
                getLogger().info("updating pom for " + project.getName() + "...");

                for (String desc : changes.getChangeDescriptionsOrSummaries())
                {
                    getLogger().debug("  " + desc);
                }

                projectRewriter.applyChanges(project, changes);
            }
            catch (ProjectRewriteException e)
            {
                throw new JGitFlowReleaseException("Error updating poms with release versions", e);
            }
        }
    }

    protected void updatePomsWithVersionCopy(ReleaseContext ctx, List<MavenProject> projectsToUpdate, List<MavenProject> projectsWithVersions) throws JGitFlowReleaseException
    {
        Map<String, String> originalVersions = projectHelper.getOriginalVersions(randomName("copy"), projectsToUpdate);
        Map<String, String> releaseVersions = projectHelper.getOriginalVersions(randomName("copy"), projectsWithVersions);

        getLogger().info("updating poms for all projects...");
        getLogger().info("turn on debug logging with -X to see exact changes");
        for (MavenProject project : projectsToUpdate)
        {
            ProjectChangeset changes = new ProjectChangeset()
                    .with(parentReleaseVersionChange(originalVersions, releaseVersions))
                    .with(projectReleaseVersionChange(releaseVersions))
                    .with(artifactReleaseVersionChange(originalVersions, releaseVersions, ctx.isUpdateDependencies()))
                    .with(scmDefaultTagChange(releaseVersions));
            try
            {
                getLogger().info("updating pom for " + project.getName() + "...");

                for (String desc : changes.getChangeDescriptionsOrSummaries())
                {
                    getLogger().debug("  " + desc);
                }

                projectRewriter.applyChanges(project, changes);
            }
            catch (ProjectRewriteException e)
            {
                throw new JGitFlowReleaseException("Error updating poms with release versions", e);
            }
        }
    }

    protected void updatePomsWithPreviousVersions(String key, ReleaseContext ctx, List<MavenProject> reactorProjects, MavenJGitFlowConfiguration config) throws JGitFlowReleaseException
    {
        Map<String, String> originalVersions = projectHelper.getOriginalVersions(key, reactorProjects);
        Map<String, String> preHotfixVersions = config.getPreHotfixVersions();
        
        if(null == preHotfixVersions || preHotfixVersions.isEmpty())
        {
            //uh, not sure what to do here other than set to next develop version
            updatePomsWithDevelopmentVersion(key, ctx,reactorProjects);
            
            return;
        }

        getLogger().info("updating poms for all projects...");
        getLogger().info("turn on debug logging with -X to see exact changes");
        for (MavenProject project : reactorProjects)
        {
            ProjectChangeset changes = new ProjectChangeset()
                    .with(parentReleaseVersionChange(originalVersions, preHotfixVersions))
                    .with(projectReleaseVersionChange(preHotfixVersions))
                    .with(artifactReleaseVersionChange(originalVersions, preHotfixVersions, ctx.isUpdateDependencies()))
                    .with(scmDefaultTagChange(preHotfixVersions));
            try
            {
                getLogger().info("updating pom for " + project.getName() + "...");

                for (String desc : changes.getChangeDescriptionsOrSummaries())
                {
                    getLogger().debug("  " + desc);
                }

                projectRewriter.applyChanges(project, changes);
            }
            catch (ProjectRewriteException e)
            {
                throw new JGitFlowReleaseException("Error updating poms with development versions", e);
            }
        }
    }
    
    protected void updatePomsWithHotfixVersion(String key, ReleaseContext ctx, List<MavenProject> reactorProjects, MavenJGitFlowConfiguration config) throws JGitFlowReleaseException
    {
        Map<String, String> originalVersions = projectHelper.getOriginalVersions(key, reactorProjects);
        Map<String, String> hotfixVersions = projectHelper.getHotfixVersions(key, reactorProjects, ctx, config.getLastReleaseVersions());

        getLogger().info("updating poms for all projects...");
        getLogger().info("turn on debug logging with -X to see exact changes");
        for (MavenProject project : reactorProjects)
        {
            ProjectChangeset changes = new ProjectChangeset()
                    .with(parentReleaseVersionChange(originalVersions, hotfixVersions))
                    .with(projectReleaseVersionChange(hotfixVersions))
                    .with(artifactReleaseVersionChange(originalVersions, hotfixVersions, ctx.isUpdateDependencies()))
                    .with(scmDefaultTagChange(hotfixVersions));
            try
            {
                getLogger().info("updating pom for " + project.getName() + "...");

                for (String desc : changes.getChangeDescriptionsOrSummaries())
                {
                    getLogger().debug("  " + desc);
                }

                projectRewriter.applyChanges(project, changes);
            }
            catch (ProjectRewriteException e)
            {
                throw new JGitFlowReleaseException("Error updating poms with hotfix versions", e);
            }
        }
    }

    protected void updatePomsWithDevelopmentVersion(String key, ReleaseContext ctx, List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        Map<String, String> originalVersions = projectHelper.getOriginalVersions(key, reactorProjects);
        Map<String, String> developmentVersions = projectHelper.getDevelopmentVersions(key, reactorProjects, ctx);

        getLogger().info("updating poms for all projects...");
        getLogger().info("turn on debug logging with -X to see exact changes");
        for (MavenProject project : reactorProjects)
        {
            ProjectChangeset changes = new ProjectChangeset()
                    .with(parentReleaseVersionChange(originalVersions, developmentVersions))
                    .with(projectReleaseVersionChange(developmentVersions))
                    .with(artifactReleaseVersionChange(originalVersions, developmentVersions, ctx.isUpdateDependencies()))
                    .with(scmDefaultHeadTagChange());
            try
            {
                getLogger().info("updating pom for " + project.getName() + "...");

                for (String desc : changes.getChangeDescriptionsOrSummaries())
                {
                    getLogger().debug("  " + desc);
                }

                projectRewriter.applyChanges(project, changes);
            }
            catch (ProjectRewriteException e)
            {
                throw new JGitFlowReleaseException("Error updating poms with development versions", e);
            }
        }
    }

    protected void checkPomForSnapshot(List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        getLogger().info("Checking for SNAPSHOT version in projects...");
        boolean hasSnapshotProject = false;
        for (MavenProject project : reactorProjects)
        {
            if (ArtifactUtils.isSnapshot(project.getVersion()))
            {
                hasSnapshotProject = true;
                break;
            }
        }

        if (!hasSnapshotProject)
        {
            throw new JGitFlowReleaseException("Unable to find SNAPSHOT version in reactor projects!");
        }
    }

    protected void checkPomForRelease(List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        getLogger().info("Checking for release version in projects...");
        boolean hasSnapshotProject = false;
        for (MavenProject project : reactorProjects)
        {
            if (ArtifactUtils.isSnapshot(project.getVersion()))
            {
                hasSnapshotProject = true;
                break;
            }
        }

        if (hasSnapshotProject)
        {
            throw new JGitFlowReleaseException("Some reactor projects contain SNAPSHOT versions!");
        }
    }
    
    protected MavenSession getSessionForBranch(JGitFlow flow, String branchName, List<MavenProject> originalProjects, MavenSession oldSession) throws GitAPIException, ReactorReloadException, IOException
    {
        String originalBranch = flow.git().getRepository().getBranch();
        
        flow.git().checkout().setName(branchName).call();

        //reload the reactor projects
        MavenProject rootProject = ReleaseUtil.getRootProject(originalProjects);
        MavenSession newSession = mavenExecutionHelper.reloadReactor(rootProject,oldSession);
        
        flow.git().checkout().setName(originalBranch).call();
        
        return newSession;
    }

    protected String randomName(String base)
    {
        long n = random.nextLong();
        n = (n == Long.MIN_VALUE) ? 0 : Math.abs(n);

        return base + Long.toString(n);
    }
}
