package com.atlassian.maven.plugins.jgitflow.manager;

import java.util.List;
import java.util.Map;

import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.JGitFlowReleaseException;
import com.atlassian.maven.plugins.jgitflow.exception.ProjectRewriteException;
import com.atlassian.maven.plugins.jgitflow.helper.ProjectHelper;
import com.atlassian.maven.plugins.jgitflow.rewrite.MavenProjectRewriter;
import com.atlassian.maven.plugins.jgitflow.rewrite.ProjectChangeset;

import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.project.MavenProject;
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
    
    protected String getReleaseLabel(ReleaseContext ctx, List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        Map<String,String> releaseVersions = projectHelper.getReleaseVersions(reactorProjects,ctx);
        MavenProject rootProject = ReleaseUtil.getRootProject(reactorProjects);
        String rootProjectId = ArtifactUtils.versionlessKey(rootProject.getGroupId(),rootProject.getArtifactId());
        return releaseVersions.get(rootProjectId);
    }
    
    protected void updatePoms(ReleaseContext ctx, List<MavenProject> reactorProjects) throws JGitFlowReleaseException
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

    protected void checkPom(List<MavenProject> reactorProjects) throws JGitFlowReleaseException
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
