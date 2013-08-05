package com.atlassian.maven.plugins.jgitflow.manager;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.ReleaseMergeResult;
import com.atlassian.jgitflow.core.exception.*;
import com.atlassian.jgitflow.core.util.GitHelper;
import com.atlassian.maven.plugins.jgitflow.MavenJGitFlowConfiguration;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.JGitFlowReleaseException;
import com.atlassian.maven.plugins.jgitflow.exception.ReactorReloadException;
import com.atlassian.maven.plugins.jgitflow.exception.UnresolvedSnapshotsException;

import com.google.common.base.Joiner;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.release.ReleaseExecutionException;
import org.apache.maven.shared.release.exec.MavenExecutorException;
import org.apache.maven.shared.release.util.ReleaseUtil;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.RefSpec;

/**
 * @since version
 */
public class DefaultFlowReleaseManager extends AbstractFlowReleaseManager
{
    @Override
    public void start(ReleaseContext ctx, List<MavenProject> reactorProjects, MavenSession session) throws JGitFlowReleaseException
    {
        JGitFlow flow = null;
        try
        {
            flow = JGitFlow.getOrInit(ctx.getBaseDir(), ctx.getFlowInitContext());

            writeReportHeader(ctx,flow.getReporter());
            setupSshCredentialProviders(ctx,flow.getReporter());
            
            String releaseLabel = startRelease(flow, ctx, reactorProjects, session);

            updateReleasePomsWithSnapshot(releaseLabel, flow, ctx, reactorProjects, session);

            if(ctx.isPushReleases())
            {
                final String prefixedBranchName = flow.getReleaseBranchPrefix() + releaseLabel;
                RefSpec branchSpec = new RefSpec(prefixedBranchName);
                flow.git().push().setRemote("origin").setRefSpecs(branchSpec).call();
            }
        }
        catch (JGitFlowException e)
        {
            throw new JGitFlowReleaseException("Error starting release: " + e.getMessage(), e);
        }
        catch (GitAPIException e)
        {
            throw new JGitFlowReleaseException("Error starting release: " + e.getMessage(), e);
        }
        finally
        {
            if(null != flow)
            {
                flow.getReporter().flush();
            }
        }
    }


    @Override
    public void finish(ReleaseContext ctx, List<MavenProject> originalProjects, MavenSession session) throws JGitFlowReleaseException
    {
        JGitFlow flow = null;
        MavenJGitFlowConfiguration config = null;
        try
        {
            flow = JGitFlow.getOrInit(ctx.getBaseDir(), ctx.getFlowInitContext());

            writeReportHeader(ctx,flow.getReporter());
            setupSshCredentialProviders(ctx,flow.getReporter());
            
            config = configManager.getConfiguration(flow.git());

            if(ctx.isPushReleases() || !ctx.isNoTag())
            {
                projectHelper.ensureOrigin(ctx.getDefaultOriginUrl(), flow);
            }
            
            finishRelease(flow, config, ctx, originalProjects, session);
        }
        catch (JGitFlowException e)
        {
            throw new JGitFlowReleaseException("Error finishing release: " + e.getMessage(), e);
        }
        catch (IOException e)
        {
            throw new JGitFlowReleaseException("Error finishing release: " + e.getMessage(), e);
        }
        finally
        {
            if(null != flow)
            {
                flow.getReporter().flush();
            }
        }
    }

    private String startRelease(JGitFlow flow, ReleaseContext ctx, List<MavenProject> originalProjects, MavenSession session) throws JGitFlowReleaseException
    {
        String releaseLabel = "";
        
        try
        {
            //make sure we're on develop
            flow.git().checkout().setName(flow.getDevelopBranchName()).call();

            //reload the reactor projects for develop
            MavenSession developSession = getSessionForBranch(flow, flow.getDevelopBranchName(), originalProjects, session);
            List<MavenProject> developProjects = developSession.getSortedProjects();
            
            checkPomForSnapshot(developProjects);
    
            if(!ctx.isAllowSnapshots())
            {
                List<String> snapshots = projectHelper.checkForNonReactorSnapshots("develop", developProjects);
                if(!snapshots.isEmpty())
                {
                    String details = Joiner.on(ls).join(snapshots);
                    throw new UnresolvedSnapshotsException("Cannot start a release due to snapshot dependencies:" + ls + details);
                }
            }
    
            if(ctx.isPushReleases() || !ctx.isNoTag())
            {
                projectHelper.ensureOrigin(ctx.getDefaultOriginUrl(), flow);
            }

            releaseLabel = getReleaseLabel("releaseStartLabel", ctx, developProjects);
    
            flow.releaseStart(releaseLabel)
                .setAllowUntracked(ctx.isAllowUntracked())
                .setPush(ctx.isPushReleases())
                .setStartCommit(ctx.getStartCommit())
                .call();
        }
        catch (GitAPIException e)
        {
            throw new JGitFlowReleaseException("Error starting release: " + e.getMessage(), e);
        }
        catch (ReleaseBranchExistsException e)
        {
            throw new JGitFlowReleaseException("Error starting release: " + e.getMessage(), e);
        }
        catch (JGitFlowException e)
        {
            throw new JGitFlowReleaseException("Error starting release: " + e.getMessage(), e);
        }
        catch (ReactorReloadException e)
        {
            throw new JGitFlowReleaseException("Error starting release: " + e.getMessage(), e);
        }
        catch (IOException e)
        {
            throw new JGitFlowReleaseException("Error starting release: " + e.getMessage(), e);
        }
        
        return releaseLabel;
    }

    private void finishRelease(JGitFlow flow, MavenJGitFlowConfiguration config, ReleaseContext ctx, List<MavenProject> originalProjects, MavenSession session) throws JGitFlowReleaseException
    {
        String releaseLabel = "";
        
        try
        {
            //get the release branch
            List<Ref> releaseBranches = GitHelper.listBranchesWithPrefix(flow.git(), flow.getReleaseBranchPrefix());

            if (releaseBranches.isEmpty())
            {
                throw new JGitFlowReleaseException("Could not find release branch!");
            }

            //there can be only one
            String rheadPrefix = Constants.R_HEADS + flow.getReleaseBranchPrefix();
            Ref releaseBranch = releaseBranches.get(0);
            releaseLabel = releaseBranch.getName().substring(releaseBranch.getName().indexOf(rheadPrefix) + rheadPrefix.length());
            
            //make sure we're on the release branch
            if(getLogger().isDebugEnabled())
            {
                getLogger().debug("checking out release branch: " + flow.getReleaseBranchPrefix() + releaseLabel);
            }
            
            flow.git().checkout().setName(flow.getReleaseBranchPrefix() + releaseLabel).call();

            //get the reactor projects for release
            MavenSession releaseSession = getSessionForBranch(flow, flow.getReleaseBranchPrefix() + releaseLabel, originalProjects, session);
            List<MavenProject> releaseProjects = releaseSession.getSortedProjects();

            if(getLogger().isDebugEnabled())
            {
                getLogger().debug("updating release poms with release...");
            }
            updateReleasePomsWithRelease(releaseLabel,flow,ctx,originalProjects,session);
            projectHelper.commitAllPoms(flow.git(), originalProjects, "updating poms for " + releaseLabel + " release");

            //reload the reactor projects for release
            releaseSession = getSessionForBranch(flow, flow.getReleaseBranchPrefix() + releaseLabel, originalProjects, session);
            releaseProjects = releaseSession.getSortedProjects();
            
            checkPomForRelease(releaseProjects);

            if(!ctx.isAllowSnapshots())
            {
                List<String> snapshots = projectHelper.checkForNonReactorSnapshots("release", releaseProjects);
                if(!snapshots.isEmpty())
                {
                    String details = Joiner.on(ls).join(snapshots);
                    throw new UnresolvedSnapshotsException("Cannot finish a release due to snapshot dependencies:" + ls + details);
                }
            }

            MavenProject rootProject = ReleaseUtil.getRootProject(releaseProjects);

            if(!ctx.isNoBuild())
            {
                if(getLogger().isDebugEnabled())
                {
                    getLogger().debug("building project...");
                }
                try
                {
                    mavenExecutionHelper.execute(rootProject, ctx, releaseSession);
                }
                catch (MavenExecutorException e)
                {
                    throw new JGitFlowReleaseException("Error building: " + e.getMessage(), e);
                }
            }

            Map<String, String> originalVersions = projectHelper.getOriginalVersions("release", releaseProjects);

            getLogger().info("running jgitflow release finish...");
            ReleaseMergeResult mergeResult = flow.releaseFinish(releaseLabel)
                .setPush(ctx.isPushReleases())
                .setKeepBranch(ctx.isKeepBranch())
                .setNoTag(ctx.isNoTag())
                .setSquash(ctx.isSquash())
                .setMessage(ReleaseUtil.interpolate(ctx.getTagMessage(), rootProject.getModel()))
                .setAllowUntracked(ctx.isAllowUntracked())
                .setNoMerge(ctx.isNoReleaseMerge())
                .call();
            
            if(!mergeResult.wasSuccessful())
            {
                if(mergeResult.masterHasProblems())
                {
                    getLogger().error("Error merging into " + flow.getMasterBranchName() + ":");
                    getLogger().error(mergeResult.getMasterResult().toString());
                    getLogger().error("see .git/jgitflow.log for more info");
                }
                
                if(mergeResult.developHasProblems())
                {
                    getLogger().error("Error merging into " + flow.getDevelopBranchName() + ":");
                    getLogger().error(mergeResult.getDevelopResult().toString());
                    getLogger().error("see .git/jgitflow.log for more info");
                }
                
                throw new JGitFlowReleaseException("Error while merging release!");
            }

            //make sure we're on develop
            flow.git().checkout().setName(flow.getDevelopBranchName()).call();

            //reload the reactor projects for develop
            MavenSession developSession = getSessionForBranch(flow, flow.getDevelopBranchName(), originalProjects, session);
            List<MavenProject> developProjects = developSession.getSortedProjects();

            String developLabel = getDevelopmentLabel("develop", ctx, developProjects);
            updatePomsWithDevelopmentVersion("develop", ctx, developProjects);

            projectHelper.commitAllPoms(flow.git(), developProjects, "updating poms for " + developLabel + " development");

            if(ctx.isPushReleases())
            {
                RefSpec developSpec = new RefSpec(ctx.getFlowInitContext().getDevelop());
                flow.git().push().setRemote(Constants.DEFAULT_REMOTE_NAME).setRefSpecs(developSpec).call();
            }

            config.setLastReleaseVersions(originalVersions);
            configManager.saveConfiguration(config, flow.git());
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
        catch (ReactorReloadException e)
        {
            throw new JGitFlowReleaseException("Error releasing: " + e.getMessage(), e);
        }
        catch (IOException e)
        {
            throw new JGitFlowReleaseException("Error releasing: " + e.getMessage(), e);
        }
    }

    private void updateReleasePomsWithSnapshot(String releaseLabel, JGitFlow flow, ReleaseContext ctx, List<MavenProject> originalProjects, MavenSession session) throws JGitFlowReleaseException
    {
        try
        {
            //reload the reactor projects for release
            MavenSession releaseSession = getSessionForBranch(flow, flow.getReleaseBranchPrefix() + releaseLabel, originalProjects, session);
            List<MavenProject> releaseProjects = releaseSession.getSortedProjects();
            updatePomsWithReleaseSnapshotVersion("releaseStartLabel", releaseLabel, ctx, releaseProjects);

            projectHelper.commitAllPoms(flow.git(), releaseProjects, "updating poms for " + releaseLabel + " release");
        }
        catch (GitAPIException e)
        {
            throw new JGitFlowReleaseException("Error starting release: " + e.getMessage(), e);
        }
        catch (ReactorReloadException e)
        {
            throw new JGitFlowReleaseException("Error starting release: " + e.getMessage(), e);
        }
        catch (IOException e)
        {
            throw new JGitFlowReleaseException("Error starting release: " + e.getMessage(), e);
        }
    }

    private void updateReleasePomsWithRelease(String releaseLabel, JGitFlow flow, ReleaseContext ctx, List<MavenProject> originalProjects, MavenSession session) throws JGitFlowReleaseException
    {
        try
        {
            //reload the reactor projects for release
            MavenSession releaseSession = getSessionForBranch(flow, flow.getReleaseBranchPrefix() + releaseLabel, originalProjects, session);
            List<MavenProject> releaseProjects = releaseSession.getSortedProjects();
            updatePomsWithReleaseVersion("releaseFinishLabel", releaseLabel, ctx, releaseProjects);

            projectHelper.commitAllPoms(flow.git(), releaseProjects,"updating poms for " + releaseLabel + " release");
        }
        catch (GitAPIException e)
        {
            throw new JGitFlowReleaseException("Error starting release: " + e.getMessage(), e);
        }
        catch (ReactorReloadException e)
        {
            throw new JGitFlowReleaseException("Error starting release: " + e.getMessage(), e);
        }
        catch (IOException e)
        {
            throw new JGitFlowReleaseException("Error starting release: " + e.getMessage(), e);
        }
    }

}
