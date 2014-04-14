package com.atlassian.maven.plugins.jgitflow.manager;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.JGitFlowReporter;
import com.atlassian.jgitflow.core.ReleaseMergeResult;
import com.atlassian.jgitflow.core.exception.BranchOutOfDateException;
import com.atlassian.jgitflow.core.exception.HotfixBranchExistsException;
import com.atlassian.jgitflow.core.exception.JGitFlowException;
import com.atlassian.jgitflow.core.exception.JGitFlowGitAPIException;
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
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.RefSpec;

/**
 * @since version
 */
public class DefaultFlowHotfixManager extends AbstractFlowReleaseManager
{
    @Override
    public void start(ReleaseContext ctx, List<MavenProject> originalProjects, MavenSession session) throws JGitFlowReleaseException
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

            String hotfixLabel = startHotfix(flow, config, ctx, originalProjects, session);
            updateHotfixPomsWithSnapshot(hotfixLabel, flow, ctx, config, originalProjects, session);

            if(ctx.isPushHotfixes())
            {
                final String prefixedBranchName = flow.getHotfixBranchPrefix() + hotfixLabel;
                RefSpec branchSpec = new RefSpec(prefixedBranchName);
                flow.git().push().setRemote("origin").setRefSpecs(branchSpec).call();
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
        catch (GitAPIException e)
        {
            throw new JGitFlowReleaseException("Error starting hotfix: " + e.getMessage(), e);
        }
        finally
        {
            if(null != flow)
            {
                flow.getReporter().flush();
            }
        }

        //do this separately since we just warn
        try
        {
            savePreHotfixDevelopVersions(flow, originalProjects, session, config);
        }
        catch (Exception e)
        {
            //TODO: warn that the develop versions will be foobarred after hotfix finish
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
            finishHotfix(flow, config, ctx, originalProjects, session);
        }
        catch (JGitFlowException e)
        {
            throw new JGitFlowReleaseException("Error finishing hotfix: " + e.getMessage(), e);
        }
        catch (IOException e)
        {
            throw new JGitFlowReleaseException("Error finishing hotfix: " + e.getMessage(), e);
        }
        finally
        {
            if(null != flow)
            {
                flow.getReporter().flush();
            }
        }
    }

    private String startHotfix(JGitFlow flow, MavenJGitFlowConfiguration config, ReleaseContext ctx, List<MavenProject> originalProjects, MavenSession session) throws JGitFlowReleaseException
    {
        String hotfixLabel = "";
        try
        {
            //make sure we're on master
            flow.git().checkout().setName(flow.getMasterBranchName()).call();

            //reload the reactor projects for master
            MavenSession masterSession = getSessionForBranch(flow, flow.getMasterBranchName(), originalProjects, session);
            List<MavenProject> masterProjects = masterSession.getSortedProjects();

            checkPomForRelease(masterProjects);

            if (!ctx.isAllowSnapshots())
            {
                List<String> snapshots = projectHelper.checkForNonReactorSnapshots("master", masterProjects);
                if (!snapshots.isEmpty())
                {
                    String details = Joiner.on(ls).join(snapshots);
                    throw new UnresolvedSnapshotsException("Cannot start a hotfix due to snapshot dependencies:" + ls + details);
                }
            }

            if (ctx.isPushHotfixes() || !ctx.isNoTag())
            {
                projectHelper.ensureOrigin(ctx.getDefaultOriginUrl(), flow);
            }


            hotfixLabel = getHotfixLabel("hotfixlabel", ctx, masterProjects, config);
            flow.hotfixStart(hotfixLabel)
                .setAllowUntracked(ctx.isAllowUntracked())
                .setPush(ctx.isPushHotfixes())
                .setStartCommit(ctx.getStartCommit())
                .setScmMessagePrefix(ctx.getScmCommentPrefix())
                .call();
        }
        catch (GitAPIException e)
        {
            throw new JGitFlowReleaseException("Error starting hotfix: " + e.getMessage(), e);
        }
        catch (HotfixBranchExistsException e)
        {
            throw new JGitFlowReleaseException("Error starting hotfix: " + e.getMessage(), e);
        }
        catch (JGitFlowException e)
        {
            throw new JGitFlowReleaseException("Error starting hotfix: " + e.getMessage(), e);
        }
        catch (IOException e)
        {
            throw new JGitFlowReleaseException("Error starting hotfix: " + e.getMessage(), e);
        }
        catch (ReactorReloadException e)
        {
            throw new JGitFlowReleaseException("Error starting hotfix: " + e.getMessage(), e);
        }

        return hotfixLabel;
    }

    private void finishHotfix(JGitFlow flow, MavenJGitFlowConfiguration config, ReleaseContext ctx, List<MavenProject> originalProjects, MavenSession session) throws JGitFlowReleaseException
    {
        String hotfixLabel = "";

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
                    reporter.errorText("hotfix-finish","local branch '" + flow.getDevelopBranchName() + "' is behind the remote branch");
                    reporter.flush();
                    throw new BranchOutOfDateException("local branch '" + flow.getDevelopBranchName() + "' is behind the remote branch");
                }
            }
            
            //get the hotfix branch
            List<Ref> hotfixBranches = GitHelper.listBranchesWithPrefix(flow.git(), flow.getHotfixBranchPrefix(),flow.getReporter());

            if (hotfixBranches.isEmpty())
            {
                throw new JGitFlowReleaseException("Could not find hotfix branch!");
            }

            //there can be only one
            String rheadPrefix = Constants.R_HEADS + flow.getHotfixBranchPrefix();
            Ref hotfixBranch = hotfixBranches.get(0);
            hotfixLabel = hotfixBranch.getName().substring(hotfixBranch.getName().indexOf(rheadPrefix) + rheadPrefix.length());

            String prefixedBranchName = flow.getHotfixBranchPrefix() + hotfixLabel;

            //make sure we're on the hotfix branch
            flow.git().checkout().setName(prefixedBranchName).call();

            //make sure we're not behind remote
            if(GitHelper.remoteBranchExists(flow.git(), prefixedBranchName, reporter))
            {
                if(GitHelper.localBranchBehindRemote(flow.git(),prefixedBranchName,reporter))
                {
                    reporter.errorText("hotfix-finish","local branch '" + prefixedBranchName + "' is behind the remote branch");
                    reporter.flush();
                    throw new BranchOutOfDateException("local branch '" + prefixedBranchName + "' is behind the remote branch");
                }
            }

            if(GitHelper.remoteBranchExists(flow.git(), flow.getMasterBranchName(), flow.getReporter()))
            {
                if(ctx.isPullMaster())
                {
                    reporter.debugText("finishHotfix", "pulling master before remote behind check");
                    reporter.flush();

                    flow.git().checkout().setName(flow.getMasterBranchName()).call();
                    flow.git().pull().call();
                    flow.git().checkout().setName(prefixedBranchName).call();
                }

                if(GitHelper.localBranchBehindRemote(flow.git(),flow.getMasterBranchName(),flow.getReporter()))
                {
                    reporter.errorText("hotfix-finish","local branch '" + flow.getMasterBranchName() + "' is behind the remote branch");
                    reporter.flush();
                    throw new BranchOutOfDateException("local branch '" + flow.getMasterBranchName() + "' is behind the remote branch");
                }
            }

            //get the reactor projects for hotfix
            MavenSession hotfixSession = getSessionForBranch(flow, prefixedBranchName, originalProjects, session);
            List<MavenProject> hotfixProjects = hotfixSession.getSortedProjects();

            updateHotfixPomsWithRelease(hotfixLabel,flow,ctx,config,originalProjects,session);
            projectHelper.commitAllPoms(flow.git(), originalProjects, ctx.getScmCommentPrefix() + "updating poms for " + hotfixLabel + " hotfix");

            //reload the reactor projects for hotfix
            hotfixSession = getSessionForBranch(flow, prefixedBranchName, originalProjects, session);
            hotfixProjects = hotfixSession.getSortedProjects();
            
            checkPomForRelease(hotfixProjects);

            if (!ctx.isAllowSnapshots())
            {
                List<String> snapshots = projectHelper.checkForNonReactorSnapshots("hotfix", hotfixProjects);
                if (!snapshots.isEmpty())
                {
                    String details = Joiner.on(ls).join(snapshots);
                    throw new UnresolvedSnapshotsException("Cannot finish a hotfix due to snapshot dependencies:" + ls + details);
                }
            }

            MavenProject rootProject = ReleaseUtil.getRootProject(hotfixProjects);

            if (!ctx.isNoBuild())
            {
                try
                {
                    mavenExecutionHelper.execute(rootProject, ctx, hotfixSession);
                }
                catch (MavenExecutorException e)
                {
                    throw new JGitFlowReleaseException("Error building: " + e.getMessage(), e);
                }
            }


            Map<String, String> originalVersions = projectHelper.getOriginalVersions("hotfix", hotfixProjects);

            //We need to commit the hotfix versioned poms to develop to avoid a merge conflict

            //reload the reactor projects for develop
            MavenSession developSession = getSessionForBranch(flow, flow.getDevelopBranchName(), originalProjects, session);
            List<MavenProject> developProjects = developSession.getSortedProjects();

            savePreHotfixDevelopVersions(flow, developProjects, config);

            flow.git().checkout().setName(flow.getDevelopBranchName()).call();
            updatePomsWithVersionCopy(ctx, developProjects, hotfixProjects);
            flow.git().add().addFilepattern(".").call();
            flow.git().commit().setMessage(ctx.getScmCommentPrefix() + "updating develop with hotfix versions to avoid merge conflicts").call();

            flow.git().checkout().setName(prefixedBranchName);

            if (ctx.isPushHotfixes() || !ctx.isNoTag())
            {
                projectHelper.ensureOrigin(ctx.getDefaultOriginUrl(), flow);
            }

            getLogger().info("running jgitflow hotfix finish...");
            
            ReleaseMergeResult mergeResult = flow.hotfixFinish(hotfixLabel)
                .setPush(ctx.isPushHotfixes())
                .setKeepBranch(ctx.isKeepBranch())
                .setNoTag(ctx.isNoTag())
                .setMessage(ReleaseUtil.interpolate(ctx.getTagMessage(), rootProject.getModel()))
                .setAllowUntracked(ctx.isAllowUntracked())
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

                throw new JGitFlowReleaseException("Error while merging hotfix!");
            }

            //make sure we're on develop
            flow.git().checkout().setName(flow.getDevelopBranchName()).call();

            //reload the reactor projects for develop
            developSession = getSessionForBranch(flow, flow.getDevelopBranchName(), originalProjects, session);
            developProjects = developSession.getSortedProjects();
            updatePomsWithPreviousVersions("hotfix", ctx, developProjects, config);

            projectHelper.commitAllPoms(flow.git(), developProjects, ctx.getScmCommentPrefix() + "updating poms for development");

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
        catch (IOException e)
        {
            throw new JGitFlowReleaseException("Error releasing: " + e.getMessage(), e);
        }
        catch (ReactorReloadException e)
        {
            throw new JGitFlowReleaseException("Error releasing: " + e.getMessage(), e);
        }
    }

    private void updateHotfixPoms(String hotfixLabel, JGitFlow flow, ReleaseContext ctx, MavenJGitFlowConfiguration config, List<MavenProject> originalProjects, MavenSession session) throws JGitFlowReleaseException
    {
        try
        {
            //reload the reactor projects for hotfix
            MavenSession hotfixSession = getSessionForBranch(flow, flow.getHotfixBranchPrefix() + hotfixLabel, originalProjects, session);
            List<MavenProject> hotfixProjects = hotfixSession.getSortedProjects();
            updatePomsWithHotfixVersion("hotfixlabel", ctx, hotfixProjects, config);

            projectHelper.commitAllPoms(flow.git(), hotfixProjects, ctx.getScmCommentPrefix() + "updating poms for " + hotfixLabel + " hotfix");
        }
        catch (GitAPIException e)
        {
            throw new JGitFlowReleaseException("Error starting hotfix: " + e.getMessage(), e);
        }
        catch (ReactorReloadException e)
        {
            throw new JGitFlowReleaseException("Error starting hotfix: " + e.getMessage(), e);
        }
        catch (IOException e)
        {
            throw new JGitFlowReleaseException("Error starting hotfix: " + e.getMessage(), e);
        }
    }

    private void updateHotfixPomsWithRelease(String hotfixLabel, JGitFlow flow, ReleaseContext ctx, MavenJGitFlowConfiguration config, List<MavenProject> originalProjects, MavenSession session) throws JGitFlowReleaseException
    {
        try
        {
            //reload the reactor projects for hotfix
            MavenSession hotfixSession = getSessionForBranch(flow, flow.getHotfixBranchPrefix() + hotfixLabel, originalProjects, session);
            List<MavenProject> hotfixProjects = hotfixSession.getSortedProjects();
            updatePomsWithHotfixVersion("hotfixlabel", hotfixLabel, ctx, hotfixProjects, config);

            projectHelper.commitAllPoms(flow.git(), hotfixProjects, ctx.getScmCommentPrefix() + "updating poms for " + hotfixLabel + " hotfix");
        }
        catch (GitAPIException e)
        {
            throw new JGitFlowReleaseException("Error starting hotfix: " + e.getMessage(), e);
        }
        catch (ReactorReloadException e)
        {
            throw new JGitFlowReleaseException("Error starting hotfix: " + e.getMessage(), e);
        }
        catch (IOException e)
        {
            throw new JGitFlowReleaseException("Error starting hotfix: " + e.getMessage(), e);
        }
    }

    private void updateHotfixPomsWithSnapshot(String hotfixLabel, JGitFlow flow, ReleaseContext ctx, MavenJGitFlowConfiguration config, List<MavenProject> originalProjects, MavenSession session) throws JGitFlowReleaseException
    {
        try
        {
            //reload the reactor projects for hotfix
            MavenSession hotfixSession = getSessionForBranch(flow, flow.getHotfixBranchPrefix() + hotfixLabel, originalProjects, session);
            List<MavenProject> hotfixProjects = hotfixSession.getSortedProjects();
            updatePomsWithHotfixSnapshotVersion("hotfixlabel", hotfixLabel, ctx, hotfixProjects, config);

            projectHelper.commitAllPoms(flow.git(), hotfixProjects, ctx.getScmCommentPrefix() + "updating poms for " + hotfixLabel + " hotfix");
        }
        catch (GitAPIException e)
        {
            throw new JGitFlowReleaseException("Error starting hotfix: " + e.getMessage(), e);
        }
        catch (ReactorReloadException e)
        {
            throw new JGitFlowReleaseException("Error starting hotfix: " + e.getMessage(), e);
        }
        catch (IOException e)
        {
            throw new JGitFlowReleaseException("Error starting hotfix: " + e.getMessage(), e);
        }
    }

    private void savePreHotfixDevelopVersions(JGitFlow flow, List<MavenProject> originalProjects, MavenSession session, MavenJGitFlowConfiguration config) throws IOException, ReactorReloadException, GitAPIException
    {
        //reload the reactor projects for develop
        MavenSession developSession = getSessionForBranch(flow, flow.getDevelopBranchName(), originalProjects, session);
        List<MavenProject> developProjects = developSession.getSortedProjects();

        Map<String, String> originalDevelopVersions = projectHelper.getOriginalVersions("develop", developProjects);

        config.setPreHotfixVersions(originalDevelopVersions);
        configManager.saveConfiguration(config, flow.git());
    }

    private void savePreHotfixDevelopVersions(JGitFlow flow, List<MavenProject> developProjects, MavenJGitFlowConfiguration config) throws IOException, GitAPIException
    {
        Map<String, String> originalDevelopVersions = projectHelper.getOriginalVersions("develop", developProjects);

        config.setPreHotfixVersions(originalDevelopVersions);
        configManager.saveConfiguration(config, flow.git());
    }

}
