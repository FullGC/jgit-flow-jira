package com.atlassian.maven.plugins.jgitflow.manager;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.JGitFlowReporter;
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
            flow = JGitFlow.forceInit(ctx.getBaseDir(), ctx.getFlowInitContext(), ctx.getDefaultOriginUrl());

            projectHelper.fixCygwinIfNeeded(flow);
            
            writeReportHeader(ctx,flow.getReporter());
            setupCredentialProviders(ctx,flow.getReporter());
            
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
            flow = JGitFlow.forceInit(ctx.getBaseDir(), ctx.getFlowInitContext(), ctx.getDefaultOriginUrl());

            projectHelper.fixCygwinIfNeeded(flow);
            
            writeReportHeader(ctx,flow.getReporter());
            setupCredentialProviders(ctx,flow.getReporter());
            
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
                .setScmMessagePrefix(ctx.getScmCommentPrefix())
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
            JGitFlowReporter reporter = flow.getReporter();
            
            //do a pull if needed
            if(GitHelper.remoteBranchExists(flow.git(), flow.getDevelopBranchName(), flow.getReporter()))
            {
                if(ctx.isPullDevelop())
                {
                    reporter.debugText("finishRelease", "pulling develop before remote behind check");
                    reporter.flush();

                    flow.git().checkout().setName(flow.getDevelopBranchName()).call();
                    flow.git().pull().call();
                }

                if(GitHelper.localBranchBehindRemote(flow.git(),flow.getDevelopBranchName(),flow.getReporter()))
                {
                    reporter.errorText("release-finish","local branch '" + flow.getDevelopBranchName() + "' is behind the remote branch");
                    reporter.flush();
                    throw new BranchOutOfDateException("local branch '" + flow.getDevelopBranchName() + "' is behind the remote branch");
                }
            }
            
            //get the release branch
            List<Ref> releaseBranches = GitHelper.listBranchesWithPrefix(flow.git(), flow.getReleaseBranchPrefix(),flow.getReporter());

            if (releaseBranches.isEmpty())
            {
                throw new JGitFlowReleaseException("Could not find release branch!");
            }

            //there can be only one
            String rheadPrefix = Constants.R_HEADS + flow.getReleaseBranchPrefix();
            Ref releaseBranch = releaseBranches.get(0);
            
            if(null == releaseBranch)
            {
                throw new JGitFlowReleaseException("Could not find release branch!");
            }
            
            releaseLabel = releaseBranch.getName().substring(releaseBranch.getName().indexOf(rheadPrefix) + rheadPrefix.length());
            
            String prefixedBranchName = flow.getReleaseBranchPrefix() + releaseLabel;
            
            //make sure we're on the release branch
            if(getLogger().isDebugEnabled())
            {
                getLogger().debug("checking out release branch: " + prefixedBranchName);
            }
            
            flow.git().checkout().setName(prefixedBranchName).call();

            //make sure we're not behind remote
            if(GitHelper.remoteBranchExists(flow.git(), prefixedBranchName, reporter))
            {
                if(GitHelper.localBranchBehindRemote(flow.git(),prefixedBranchName,reporter))
                {
                    reporter.errorText("release-finish","local branch '" + prefixedBranchName + "' is behind the remote branch");
                    reporter.flush();
                    throw new BranchOutOfDateException("local branch '" + prefixedBranchName + "' is behind the remote branch");
                }
            }

            if(GitHelper.remoteBranchExists(flow.git(), flow.getMasterBranchName(), flow.getReporter()))
            {
                if(ctx.isPullMaster())
                {
                    reporter.debugText("finishRelease", "pulling master before remote behind check");
                    reporter.flush();
                    
                    flow.git().checkout().setName(flow.getMasterBranchName()).call();
                    flow.git().pull().call();
                    flow.git().checkout().setName(prefixedBranchName).call();
                }
                
                if(GitHelper.localBranchBehindRemote(flow.git(),flow.getMasterBranchName(),flow.getReporter()))
                {
                    reporter.errorText("release-finish","local branch '" + flow.getMasterBranchName() + "' is behind the remote branch");
                    reporter.flush();
                    throw new BranchOutOfDateException("local branch '" + flow.getMasterBranchName() + "' is behind the remote branch");
                }
            }

            //get the reactor projects for release
            MavenSession releaseSession = getSessionForBranch(flow, prefixedBranchName, originalProjects, session);
            List<MavenProject> releaseProjects = releaseSession.getSortedProjects();

            if(getLogger().isDebugEnabled())
            {
                getLogger().debug("updating release poms with release...");
            }
            updateReleasePomsWithRelease(releaseLabel,flow,ctx,originalProjects,session);
            projectHelper.commitAllPoms(flow.git(), originalProjects, ctx.getScmCommentPrefix() + "updating poms for " + releaseLabel + " release");

            //reload the reactor projects for release
            releaseSession = getSessionForBranch(flow, prefixedBranchName, originalProjects, session);
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
                .setScmMessagePrefix(ctx.getScmCommentPrefix())
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

            projectHelper.commitAllPoms(flow.git(), developProjects, ctx.getScmCommentPrefix() + "updating poms for " + developLabel + " development");

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

            projectHelper.commitAllPoms(flow.git(), releaseProjects, ctx.getScmCommentPrefix() + "updating poms for " + releaseLabel + " release");
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

            projectHelper.commitAllPoms(flow.git(), releaseProjects,ctx.getScmCommentPrefix() + "updating poms for " + releaseLabel + " release");
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
