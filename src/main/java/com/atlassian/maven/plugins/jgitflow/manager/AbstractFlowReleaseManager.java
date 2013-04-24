package com.atlassian.maven.plugins.jgitflow.manager;

import java.io.IOException;
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
    private static final String ls = System.getProperty("line.separator");
    protected ProjectHelper projectHelper;
    protected MavenProjectRewriter projectRewriter;
    protected MavenExecutionHelper mavenExecutionHelper;
    protected MavenJGitFlowConfigManager configManager;

    protected void startRelease(ReleaseContext ctx, List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {

        checkPomForSnapshot(reactorProjects);

        JGitFlow flow = null;
        String releaseLabel = getReleaseLabel(ctx, reactorProjects);
        try
        {
            flow = JGitFlow.getOrInit(ctx.getBaseDir(), ctx.getFlowInitContext());

            if(ctx.isPush() || !ctx.isNoTag())
            {
                projectHelper.ensureOrigin(reactorProjects, flow);
            }
            
            flow.releaseStart(releaseLabel).call();
        }
        catch (ReleaseBranchExistsException e)
        {
            try
            {
                List<Ref> refs = GitHelper.listBranchesWithPrefix(flow.git(),flow.getReleaseBranchPrefix());
                boolean foundOurRelease = false;
                for(Ref ref : refs)
                {
                    if(ref.getName().equals(Constants.R_HEADS + flow.getReleaseBranchPrefix() + releaseLabel))
                    {
                        foundOurRelease = true;
                        break;
                    }
                }
                
                if(foundOurRelease)
                {
                    //since the release branch already exists, just check it out
                    flow.git().checkout().setName(flow.getReleaseBranchPrefix() + releaseLabel).call();
                }
                else
                {
                    throw new JGitFlowReleaseException("Error starting release: " + e.getMessage(), e);
                }
            }
            catch (GitAPIException e1)
            {
                throw new JGitFlowReleaseException("Error checking out existing release branch: " + e1.getMessage(), e1);
            }
            catch (JGitFlowGitAPIException e1)
            {
                throw new JGitFlowReleaseException("Error checking out existing release branch: " + e1.getMessage(), e1);
            }
        }
        catch (JGitFlowException e)
        {
            throw new JGitFlowReleaseException("Error starting release: " + e.getMessage(), e);
        }

        updatePomsWithReleaseVersion(ctx, reactorProjects);

        projectHelper.commitAllChanges(flow.git(), "updating poms for " + releaseLabel + " release");
    }

    protected void startHotfix(ReleaseContext ctx, List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        checkPomForSnapshot(reactorProjects);

        Map<String, String> originalVersions = projectHelper.getOriginalVersions(reactorProjects);

        JGitFlow flow = null;
        String hotfixLabel = "";
        MavenJGitFlowConfiguration config = null;
        try
        {
            flow = JGitFlow.getOrInit(ctx.getBaseDir(), ctx.getFlowInitContext());

            if(ctx.isPush() || !ctx.isNoTag())
            {
                projectHelper.ensureOrigin(reactorProjects, flow);
            }
            
            config = configManager.getConfiguration(flow.git());

            hotfixLabel = getHotfixLabel(ctx, reactorProjects, config);
            flow.hotfixStart(hotfixLabel).call();
        }
        catch (HotfixBranchExistsException e)
        {
            try
            {
                List<Ref> refs = GitHelper.listBranchesWithPrefix(flow.git(),flow.getHotfixBranchPrefix());
                boolean foundOurRelease = false;
                for(Ref ref : refs)
                {
                    if(ref.getName().equals(Constants.R_HEADS + flow.getHotfixBranchPrefix() + hotfixLabel))
                    {
                        foundOurRelease = true;
                        break;
                    }
                }

                if(foundOurRelease)
                {
                    //since the release branch already exists, just check it out
                    flow.git().checkout().setName(flow.getHotfixBranchPrefix() + hotfixLabel).call();   
                }
                else
                {
                    throw new JGitFlowReleaseException("Error starting hotfix: " + e.getMessage(), e);
                }
                
            }
            catch (GitAPIException e1)
            {
                throw new JGitFlowReleaseException("Error checking out existing hotfix branch: " + e1.getMessage(), e1);
            }
            catch (JGitFlowGitAPIException e1)
            {
                e1.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }
        }
        catch (JGitFlowException e)
        {
            throw new JGitFlowReleaseException("Error starting hotfix: " + e.getMessage(), e);
        }
        catch (IOException e)
        {
            throw new JGitFlowReleaseException("Error starting hotfix: " + e.getMessage(), e);
        }

        updatePomsWithHotfixVersion(ctx, reactorProjects, config);

        projectHelper.commitAllChanges(flow.git(), "updating poms for " + hotfixLabel + " hotfix");

        try
        {
            //save original versions to file so we can use them when we finish
            config.setPreHotfixVersions(originalVersions);
            configManager.saveConfiguration(config, flow.git());
        }
        catch (IOException e)
        {
            //just ignore for now
        }
    }

    protected void finishRelease(ReleaseContext ctx, List<MavenProject> reactorProjects, MavenSession session) throws JGitFlowReleaseException
    {
        checkPomForRelease(reactorProjects);
        JGitFlow flow = null;

        Map<String, String> originalVersions = projectHelper.getOriginalVersions(reactorProjects);
        String releaseLabel = getReleaseLabel(ctx, reactorProjects);
        MavenProject rootProject = ReleaseUtil.getRootProject(reactorProjects);
        
        if(!ctx.isNoBuild())
        {
            try
            {
                mavenExecutionHelper.execute(rootProject, ctx, session);
            }
            catch (MavenExecutorException e)
            {
                throw new JGitFlowReleaseException("Error building: " + e.getMessage(), e);
            }
        }
        
        try
        {
            flow = JGitFlow.getOrInit(ctx.getBaseDir(), ctx.getFlowInitContext());
            
            if(ctx.isPush() || !ctx.isNoTag())
            {
                projectHelper.ensureOrigin(reactorProjects, flow);
            }

            getLogger().info("running jgitflow release finish...");
            flow.releaseFinish(releaseLabel)
                .setPush(ctx.isPush())
                .setKeepBranch(ctx.isKeepBranch())
                .setNoTag(ctx.isNoTag())
                .setSquash(ctx.isSquash())
                .setMessage(ReleaseUtil.interpolate(ctx.getTagMessage(), rootProject.getModel()))
                .call();

            //make sure we're on develop
            flow.git().checkout().setName(flow.getDevelopBranchName()).call();
            
            String developLabel = getDevelopmentLabel(ctx, reactorProjects);
            updatePomsWithDevelopmentVersion(ctx, reactorProjects);

            projectHelper.commitAllChanges(flow.git(), "updating poms for " + developLabel + " development");
            
            if(ctx.isPush())
            {
                RefSpec developSpec = new RefSpec(ctx.getFlowInitContext().getDevelop());
                flow.git().push().setRemote(Constants.DEFAULT_REMOTE_NAME).setRefSpecs(developSpec).call();
            }

            MavenJGitFlowConfiguration config = configManager.getConfiguration(flow.git());
            config.setLastReleaseVersions(originalVersions);
            configManager.saveConfiguration(config,flow.git());
            
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
        catch (IOException e)
        {
            //ignore
        }
    }

    protected void finishHotfix(ReleaseContext ctx, List<MavenProject> reactorProjects, MavenSession session) throws JGitFlowReleaseException
    {
        checkPomForRelease(reactorProjects);
        JGitFlow flow = null;

        MavenProject rootProject = ReleaseUtil.getRootProject(reactorProjects);
        
        if(!ctx.isNoBuild())
        {
            try
            {
                mavenExecutionHelper.execute(rootProject, ctx, session);
            }
            catch (MavenExecutorException e)
            {
                throw new JGitFlowReleaseException("Error building: " + e.getMessage(), e);
            }
        }

        try
        {
            Map<String, String> originalVersions = projectHelper.getOriginalVersions(reactorProjects);
            flow = JGitFlow.getOrInit(ctx.getBaseDir(), ctx.getFlowInitContext());

            MavenJGitFlowConfiguration config = configManager.getConfiguration(flow.git());
            String hotfixLabel = getReleaseLabel(ctx, reactorProjects);
            
            //We need to commit the hotfix versioned poms to develop to avoid a merge conflict
            flow.git().checkout().setName(flow.getDevelopBranchName()).call();
            updatePomsWithReleaseVersion(ctx, reactorProjects);
            flow.git().add().addFilepattern(".").call();
            flow.git().commit().setMessage("updating develop with hotfix versions to avoid merge conflicts").call();
            
            flow.git().checkout().setName(flow.getHotfixBranchPrefix() + hotfixLabel);

            if(ctx.isPush() || !ctx.isNoTag())
            {
                projectHelper.ensureOrigin(reactorProjects, flow);
            }

            getLogger().info("running jgitflow hotfix finish...");
            flow.hotfixFinish(hotfixLabel)
                .setPush(ctx.isPush())
                .setKeepBranch(ctx.isKeepBranch())
                .setNoTag(ctx.isNoTag())
                .setMessage(ReleaseUtil.interpolate(ctx.getTagMessage(), rootProject.getModel()))
                .call();

            //make sure we're on develop
            flow.git().checkout().setName(flow.getDevelopBranchName()).call();

            updatePomsWithPreviousVersions(ctx, reactorProjects, config);

            projectHelper.commitAllChanges(flow.git(), "updating poms for development");

            config.setLastReleaseVersions(originalVersions);
            configManager.saveConfiguration(config,flow.git());
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
        catch (IOException e)
        {
            throw new JGitFlowReleaseException("Error releasing: " + e.getMessage(), e);
        }
    }

    protected String getReleaseLabel(ReleaseContext ctx, List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        Map<String, String> releaseVersions = projectHelper.getReleaseVersions(reactorProjects, ctx);
        MavenProject rootProject = ReleaseUtil.getRootProject(reactorProjects);
        String rootProjectId = ArtifactUtils.versionlessKey(rootProject.getGroupId(), rootProject.getArtifactId());
        return releaseVersions.get(rootProjectId);
    }

    protected String getHotfixLabel(ReleaseContext ctx, List<MavenProject> reactorProjects, MavenJGitFlowConfiguration config) throws JGitFlowReleaseException
    {
        Map<String, String> hotfixVersions = projectHelper.getHotfixVersions(reactorProjects, ctx, config.getLastReleaseVersions());
        MavenProject rootProject = ReleaseUtil.getRootProject(reactorProjects);
        String rootProjectId = ArtifactUtils.versionlessKey(rootProject.getGroupId(), rootProject.getArtifactId());
        return hotfixVersions.get(rootProjectId);
    }

    protected String getDevelopmentLabel(ReleaseContext ctx, List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        Map<String, String> developmentVersions = projectHelper.getDevelopmentVersions(reactorProjects, ctx);
        MavenProject rootProject = ReleaseUtil.getRootProject(reactorProjects);
        String rootProjectId = ArtifactUtils.versionlessKey(rootProject.getGroupId(), rootProject.getArtifactId());
        return developmentVersions.get(rootProjectId);
    }

    protected void updatePomsWithReleaseVersion(ReleaseContext ctx, List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        Map<String, String> originalVersions = projectHelper.getOriginalVersions(reactorProjects);
        Map<String, String> releaseVersions = projectHelper.getReleaseVersions(reactorProjects, ctx);

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

    protected void updatePomsWithPreviousVersions(ReleaseContext ctx, List<MavenProject> reactorProjects, MavenJGitFlowConfiguration config) throws JGitFlowReleaseException
    {
        Map<String, String> originalVersions = projectHelper.getOriginalVersions(reactorProjects);
        Map<String, String> preHotfixVersions = config.getPreHotfixVersions();
        
        if(null == preHotfixVersions || preHotfixVersions.isEmpty())
        {
            //uh, not sure what to do here other than set to next develop version
            updatePomsWithDevelopmentVersion(ctx,reactorProjects);
            
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
    
    protected void updatePomsWithHotfixVersion(ReleaseContext ctx, List<MavenProject> reactorProjects, MavenJGitFlowConfiguration config) throws JGitFlowReleaseException
    {
        Map<String, String> originalVersions = projectHelper.getOriginalVersions(reactorProjects);
        Map<String, String> hotfixVersions = projectHelper.getHotfixVersions(reactorProjects, ctx, config.getLastReleaseVersions());

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

    protected void updatePomsWithDevelopmentVersion(ReleaseContext ctx, List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        Map<String, String> originalVersions = projectHelper.getOriginalVersions(reactorProjects);
        Map<String, String> developmentVersions = projectHelper.getDevelopmentVersions(reactorProjects, ctx);

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
}
