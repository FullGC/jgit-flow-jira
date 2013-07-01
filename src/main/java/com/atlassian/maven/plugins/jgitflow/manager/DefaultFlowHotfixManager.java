package com.atlassian.maven.plugins.jgitflow.manager;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.atlassian.jgitflow.core.JGitFlow;
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
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;

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
            flow = JGitFlow.getOrInit(ctx.getBaseDir(), ctx.getFlowInitContext());

            writeReportHeader(ctx,flow.getReporter());
            setupSshCredentialProviders(ctx,flow.getReporter());
            
            config = configManager.getConfiguration(flow.git());

            String hotfixLabel = startHotfix(flow, config, ctx, originalProjects, session);
            updateHotfixPomsWithSnapshot(hotfixLabel, flow, ctx, config, originalProjects, session);
        }
        catch (JGitFlowException e)
        {
            throw new JGitFlowReleaseException("Error starting hotfix: " + e.getMessage(), e);
        }
        catch (IOException e)
        {
            throw new JGitFlowReleaseException("Error starting hotfix: " + e.getMessage(), e);
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
            flow = JGitFlow.getOrInit(ctx.getBaseDir(), ctx.getFlowInitContext());

            writeReportHeader(ctx,flow.getReporter());
            setupSshCredentialProviders(ctx,flow.getReporter());
            
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

            if (ctx.isPush() || !ctx.isNoTag())
            {
                projectHelper.ensureOrigin(masterProjects, flow);
            }


            hotfixLabel = getHotfixLabel("hotfixlabel", ctx, masterProjects, config);
            flow.hotfixStart(hotfixLabel).call();
        }
        catch (GitAPIException e)
        {
            throw new JGitFlowReleaseException("Error starting hotfix: " + e.getMessage(), e);
        }
        catch (HotfixBranchExistsException e)
        {
            try
            {
                List<Ref> refs = GitHelper.listBranchesWithPrefix(flow.git(), flow.getHotfixBranchPrefix());
                boolean foundOurRelease = false;
                for (Ref ref : refs)
                {
                    if (ref.getName().equals(Constants.R_HEADS + flow.getHotfixBranchPrefix() + hotfixLabel))
                    {
                        foundOurRelease = true;
                        break;
                    }
                }

                if (foundOurRelease)
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
                throw new JGitFlowReleaseException("Error checking out existing hotfix branch: " + e1.getMessage(), e1);
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
            //get the hotfix branch
            List<Ref> hotfixBranches = GitHelper.listBranchesWithPrefix(flow.git(), flow.getHotfixBranchPrefix());

            if (hotfixBranches.isEmpty())
            {
                throw new JGitFlowReleaseException("Could not find hotfix branch!");
            }

            //there can be only one
            String rheadPrefix = Constants.R_HEADS + flow.getHotfixBranchPrefix();
            Ref hotfixBranch = hotfixBranches.get(0);
            hotfixLabel = hotfixBranch.getName().substring(hotfixBranch.getName().indexOf(rheadPrefix) + rheadPrefix.length());


            //make sure we're on the hotfix branch
            flow.git().checkout().setName(flow.getHotfixBranchPrefix() + hotfixLabel).call();

            //get the reactor projects for hotfix
            MavenSession hotfixSession = getSessionForBranch(flow, flow.getHotfixBranchPrefix() + hotfixLabel, originalProjects, session);
            List<MavenProject> hotfixProjects = hotfixSession.getSortedProjects();

            updateHotfixPomsWithRelease(hotfixLabel,flow,ctx,config,originalProjects,session);
            projectHelper.commitAllChanges(flow.git(), "updating poms for " + hotfixLabel + " hotfix");

            //reload the reactor projects for hotfix
            hotfixSession = getSessionForBranch(flow, flow.getHotfixBranchPrefix() + hotfixLabel, originalProjects, session);
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
            flow.git().commit().setMessage("updating develop with hotfix versions to avoid merge conflicts").call();

            flow.git().checkout().setName(flow.getHotfixBranchPrefix() + hotfixLabel);

            if (ctx.isPush() || !ctx.isNoTag())
            {
                projectHelper.ensureOrigin(hotfixProjects, flow);
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

            //reload the reactor projects for develop
            developSession = getSessionForBranch(flow, flow.getDevelopBranchName(), originalProjects, session);
            developProjects = developSession.getSortedProjects();
            updatePomsWithPreviousVersions("develop", ctx, developProjects, config);

            projectHelper.commitAllChanges(flow.git(), "updating poms for development");

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

            projectHelper.commitAllChanges(flow.git(), "updating poms for " + hotfixLabel + " hotfix");
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

            projectHelper.commitAllChanges(flow.git(), "updating poms for " + hotfixLabel + " hotfix");
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

            projectHelper.commitAllChanges(flow.git(), "updating poms for " + hotfixLabel + " hotfix");
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
