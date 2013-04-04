package com.atlassian.maven.plugins.jgitflow.manager;

import java.util.List;
import java.util.Map;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.exception.JGitFlowException;
import com.atlassian.jgitflow.core.exception.ReleaseBranchExistsException;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.JGitFlowReleaseException;
import com.atlassian.maven.plugins.jgitflow.exception.ProjectRewriteException;
import com.atlassian.maven.plugins.jgitflow.helper.MavenExecutionHelper;
import com.atlassian.maven.plugins.jgitflow.helper.ProjectHelper;
import com.atlassian.maven.plugins.jgitflow.rewrite.MavenProjectRewriter;
import com.atlassian.maven.plugins.jgitflow.rewrite.ProjectChangeset;

import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.release.ReleaseExecutionException;
import org.apache.maven.shared.release.exec.MavenExecutorException;
import org.apache.maven.shared.release.util.ReleaseUtil;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import static com.atlassian.maven.plugins.jgitflow.rewrite.ArtifactReleaseVersionChange.artifactReleaseVersionChange;
import static com.atlassian.maven.plugins.jgitflow.rewrite.ParentReleaseVersionChange.parentReleaseVersionChange;
import static com.atlassian.maven.plugins.jgitflow.rewrite.ProjectReleaseVersionChange.projectReleaseVersionChange;
import static com.atlassian.maven.plugins.jgitflow.rewrite.ScmDefaultTagChange.scmDefaultTagChange;

/**
 * @since version
 */
public abstract class AbstractFlowReleaseManager extends AbstractLogEnabled implements FlowReleaseManager
{
    protected ProjectHelper projectHelper;
    protected MavenProjectRewriter projectRewriter;
    protected MavenExecutionHelper mavenExecutionHelper;

    protected void start(ReleaseContext ctx, List<MavenProject> reactorProjects, boolean isHotfix) throws JGitFlowReleaseException
    {
        String typeLabel = (isHotfix) ? "hotfix" : "release";
        
        checkPomForSnapshot(reactorProjects);

        JGitFlow flow = null;
        String releaseLabel = getReleaseLabel(ctx,reactorProjects);
        try
        {
            flow = JGitFlow.getOrInit(ctx.getBaseDir(),ctx.getFlowInitContext());
            
            if(!isHotfix)
            {
                flow.releaseStart(releaseLabel).call();
            }
            else
            {
                flow.hotfixStart(releaseLabel).call();
            }
        }
        catch (ReleaseBranchExistsException e)
        {
            //since the release branch already exists, just check it out
            String branchPrefix = (isHotfix) ? flow.getHotfixBranchPrefix() : flow.getReleaseBranchPrefix();
            try
            {
                flow.git().checkout().setName(branchPrefix + releaseLabel).call();
            }
            catch (GitAPIException e1)
            {
                throw new JGitFlowReleaseException("Error checking out existing " + typeLabel + " branch: " + e1.getMessage(), e1);
            }
        }
        catch (JGitFlowException e)
        {
            throw new JGitFlowReleaseException("Error starting " + typeLabel + ": " + e.getMessage(), e);
        }

        updatePomsWithReleaseVersion(ctx, reactorProjects);

        commitAllChanges(flow.git(),"updating poms for " + releaseLabel + " " + typeLabel);
    }
    
    protected void finish(ReleaseContext ctx, List<MavenProject> reactorProjects, MavenSession session, boolean isHotfix) throws JGitFlowReleaseException
    {
        checkPomForRelease(reactorProjects);
        JGitFlow flow = null;

        String releaseLabel = getReleaseLabel(ctx,reactorProjects);
        MavenProject rootProject = ReleaseUtil.getRootProject(reactorProjects);
        try
        {
            mavenExecutionHelper.execute(rootProject, ctx, session);
        }
        catch (MavenExecutorException e)
        {
            throw new JGitFlowReleaseException("Error building: " + e.getMessage(), e);
        }

        try
        {
            flow = JGitFlow.getOrInit(ctx.getBaseDir(),ctx.getFlowInitContext());
            // TODO: if we're set to push, make sure SCM is correct, and make sure we have an ORIGIN
            if(!isHotfix)
            {
                flow.releaseFinish(releaseLabel)
                    .setPush(ctx.isPush())
                    .setKeepBranch(ctx.isKeepBranch())
                    .setNoTag(ctx.isNoTag())
                    .setSquash(ctx.isSquash())
                    .setMessage(ReleaseUtil.interpolate(ctx.getTagMessage(),rootProject.getModel()))
                    .call();

                //make sure we're on develop
                flow.git().checkout().setName(flow.getDevelopBranchName()).call();
    
                String developLabel = getDevelopmentLabel(ctx,reactorProjects);
                updatePomsWithDevelopmentVersion(ctx, reactorProjects);
    
                commitAllChanges(flow.git(),"updating poms for " + developLabel + " development");
            }
            else
            {
                flow.hotfixFinish(releaseLabel)
                    .setPush(ctx.isPush())
                    .setKeepBranch(ctx.isKeepBranch())
                    .setNoTag(ctx.isNoTag())
                    .setMessage(ReleaseUtil.interpolate(ctx.getTagMessage(),rootProject.getModel()))
                    .call();

                //make sure we're on develop
                flow.git().checkout().setName(flow.getDevelopBranchName()).call();
            }

        }
        catch (JGitFlowException e)
        {
            throw new JGitFlowReleaseException("Error releasing: " + e.getMessage(), e);
        }
        catch (GitAPIException e)
        {
            throw new JGitFlowReleaseException("Error releasing: " + e.getMessage(), e);
        }
        catch (ReleaseExecutionException e)
        {
            throw new JGitFlowReleaseException("Error releasing: " + e.getMessage(), e);
        }
    }
    
    protected String getReleaseLabel(ReleaseContext ctx, List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        Map<String,String> releaseVersions = projectHelper.getReleaseVersions(reactorProjects,ctx);
        MavenProject rootProject = ReleaseUtil.getRootProject(reactorProjects);
        String rootProjectId = ArtifactUtils.versionlessKey(rootProject.getGroupId(),rootProject.getArtifactId());
        return releaseVersions.get(rootProjectId);
    }

    protected String getDevelopmentLabel(ReleaseContext ctx, List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        Map<String,String> developmentVersions = projectHelper.getDevelopmentVersions(reactorProjects, ctx);
        MavenProject rootProject = ReleaseUtil.getRootProject(reactorProjects);
        String rootProjectId = ArtifactUtils.versionlessKey(rootProject.getGroupId(),rootProject.getArtifactId());
        return developmentVersions.get(rootProjectId);
    }
    
    protected void updatePomsWithReleaseVersion(ReleaseContext ctx, List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        Map<String,String> originalVersions = projectHelper.getOriginalVersions(reactorProjects);
        Map<String,String> releaseVersions = projectHelper.getReleaseVersions(reactorProjects,ctx);

        getLogger().info("updating poms for all projects...");
        getLogger().info("turn on debug logging with -X to see exact changes");
        for(MavenProject project : reactorProjects)
        {
            ProjectChangeset changes = new ProjectChangeset()
                    .with(parentReleaseVersionChange(originalVersions, releaseVersions))
                    .with(projectReleaseVersionChange(releaseVersions))
                    .with(artifactReleaseVersionChange(originalVersions,releaseVersions,ctx.isUpdateDependencies()))
                    .with(scmDefaultTagChange(releaseVersions));
            try
            {
                getLogger().info("updating pom for " + project.getName() + "...");

                for (String desc : changes.getChangeDescriptionsOrSummaries())
                {
                    getLogger().debug("  " + desc);
                }

                projectRewriter.applyChanges(project,changes);
            }
            catch (ProjectRewriteException e)
            {
                throw new JGitFlowReleaseException("Error updating poms with release versions", e);
            }
        }
    }

    protected void updatePomsWithDevelopmentVersion(ReleaseContext ctx, List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        Map<String,String> originalVersions = projectHelper.getOriginalVersions(reactorProjects);
        Map<String,String> developmentVersions = projectHelper.getDevelopmentVersions(reactorProjects, ctx);

        getLogger().info("updating poms for all projects...");
        getLogger().info("turn on debug logging with -X to see exact changes");
        for(MavenProject project : reactorProjects)
        {
            ProjectChangeset changes = new ProjectChangeset()
                    .with(parentReleaseVersionChange(originalVersions, developmentVersions))
                    .with(projectReleaseVersionChange(developmentVersions))
                    .with(artifactReleaseVersionChange(originalVersions,developmentVersions,ctx.isUpdateDependencies()))
                    .with(scmDefaultTagChange(developmentVersions));
            try
            {
                getLogger().info("updating pom for " + project.getName() + "...");

                for (String desc : changes.getChangeDescriptionsOrSummaries())
                {
                    getLogger().debug("  " + desc);
                }

                projectRewriter.applyChanges(project,changes);
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

    protected void commitAllChanges(Git git, String message) throws JGitFlowReleaseException
    {
        try
        {
            git.add().addFilepattern(".").call();
            git.commit().setMessage(message).call();
        }
        catch (GitAPIException e)
        {
            throw new JGitFlowReleaseException("error committing pom changes: " + e.getMessage(),e);
        }

    }
}
