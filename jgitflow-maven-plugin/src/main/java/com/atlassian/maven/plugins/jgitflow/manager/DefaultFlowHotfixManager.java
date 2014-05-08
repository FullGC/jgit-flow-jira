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
import com.atlassian.jgitflow.core.util.GitHelper;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.VersionState;
import com.atlassian.maven.plugins.jgitflow.VersionType;
import com.atlassian.maven.plugins.jgitflow.exception.JGitFlowReleaseException;
import com.atlassian.maven.plugins.jgitflow.exception.ReactorReloadException;
import com.atlassian.maven.plugins.jgitflow.exception.UnresolvedSnapshotsException;
import com.atlassian.maven.plugins.jgitflow.helper.JGitFlowSetupHelper;
import com.atlassian.maven.plugins.jgitflow.helper.MavenExecutionHelper;
import com.atlassian.maven.plugins.jgitflow.helper.PomUpdater;
import com.atlassian.maven.plugins.jgitflow.helper.ProjectHelper;
import com.atlassian.maven.plugins.jgitflow.provider.*;

import com.google.common.base.Joiner;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.release.ReleaseExecutionException;
import org.apache.maven.shared.release.exec.MavenExecutorException;
import org.apache.maven.shared.release.util.ReleaseUtil;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.RefSpec;

/**
 * @since version
 */
@Component(role = FlowReleaseManager.class, hint = "hotfix")
public class DefaultFlowHotfixManager extends AbstractFlowReleaseManager
{
    public static final String ls = System.getProperty("line.separator");

    @Requirement
    private JGitFlowSetupHelper setupHelper;

    @Requirement
    private MavenExecutionHelper mavenExecutionHelper;

    @Requirement
    private ProjectHelper projectHelper;

    @Requirement
    private BranchLabelProvider labelProvider;

    @Requirement
    private PomUpdater pomUpdater;

    @Requirement
    private VersionProvider versionProvider;

    @Requirement
    private JGitFlowProvider jGitFlowProvider;

    @Requirement
    private ContextProvider contextProvider;

    @Override
    public void start(List<MavenProject> originalProjects, MavenSession session) throws JGitFlowReleaseException
    {
        JGitFlow flow = null;
        try
        {
            ReleaseContext ctx = contextProvider.getContext();
            flow = jGitFlowProvider.gitFlow();

            setupHelper.runCommonSetup();

            String hotfixLabel = startHotfix(originalProjects, session);
            updateHotfixPomsWithSnapshot(hotfixLabel, originalProjects, session);

            if (ctx.isPushHotfixes())
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
        catch (GitAPIException e)
        {
            throw new JGitFlowReleaseException("Error starting hotfix: " + e.getMessage(), e);
        }
        finally
        {
            if (null != flow)
            {
                flow.getReporter().flush();
            }
        }
    }

    @Override
    public void finish(List<MavenProject> originalProjects, MavenSession session) throws JGitFlowReleaseException
    {
        JGitFlow flow = null;

        try
        {
            ReleaseContext ctx = contextProvider.getContext();
            flow = jGitFlowProvider.gitFlow();

            setupHelper.runCommonSetup();

            finishHotfix(originalProjects, session);
        }
        catch (JGitFlowException e)
        {
            throw new JGitFlowReleaseException("Error finishing hotfix: " + e.getMessage(), e);
        }
        finally
        {
            if (null != flow)
            {
                flow.getReporter().flush();
            }
        }
    }

    private String startHotfix(List<MavenProject> originalProjects, MavenSession session) throws JGitFlowReleaseException
    {
        String hotfixLabel = "";
        try
        {
            ReleaseContext ctx = contextProvider.getContext();
            JGitFlow flow = jGitFlowProvider.gitFlow();
            //make sure we're on master
            flow.git().checkout().setName(flow.getMasterBranchName()).call();

            //reload the reactor projects for master
            MavenSession masterSession = mavenExecutionHelper.getSessionForBranch(flow.getMasterBranchName(), ReleaseUtil.getRootProject(originalProjects), session);
            List<MavenProject> masterProjects = masterSession.getSortedProjects();

            projectHelper.checkPomForVersionState(VersionState.RELEASE, masterProjects);

            if (!ctx.isAllowSnapshots())
            {
                List<String> snapshots = projectHelper.checkForNonReactorSnapshots(ProjectCacheKey.MASTER_BRANCH, masterProjects);
                if (!snapshots.isEmpty())
                {
                    String details = Joiner.on(ls).join(snapshots);
                    throw new UnresolvedSnapshotsException("Cannot start a hotfix due to snapshot dependencies:" + ls + details);
                }
            }

            if (ctx.isPushHotfixes() || !ctx.isNoTag())
            {
                setupHelper.ensureOrigin();
            }

            hotfixLabel = labelProvider.getVersionLabel(VersionType.HOTFIX, ProjectCacheKey.HOTFIX_LABEL, masterProjects);
            flow.hotfixStart(hotfixLabel)
                .setAllowUntracked(ctx.isAllowUntracked())
                .setPush(ctx.isPushHotfixes())
                .setStartCommit(ctx.getStartCommit())
                .setScmMessagePrefix(ctx.getScmCommentPrefix())
                .setScmMessageSuffix(ctx.getScmCommentSuffix())
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

    private void finishHotfix(List<MavenProject> originalProjects, MavenSession session) throws JGitFlowReleaseException
    {
        String hotfixLabel = "";

        try
        {
            ReleaseContext ctx = contextProvider.getContext();
            JGitFlow flow = jGitFlowProvider.gitFlow();
            JGitFlowReporter reporter = flow.getReporter();

            //do a pull if needed
            if (GitHelper.remoteBranchExists(flow.git(), flow.getDevelopBranchName(), flow.getReporter()))
            {
                if (ctx.isPullDevelop())
                {
                    reporter.debugText("finishRelease", "pulling develop before remote behind check");
                    reporter.flush();

                    flow.git().checkout().setName(flow.getDevelopBranchName()).call();
                    flow.git().pull().call();
                }

                if (GitHelper.localBranchBehindRemote(flow.git(), flow.getDevelopBranchName(), flow.getReporter()))
                {
                    reporter.errorText("hotfix-finish", "local branch '" + flow.getDevelopBranchName() + "' is behind the remote branch");
                    reporter.flush();
                    throw new BranchOutOfDateException("local branch '" + flow.getDevelopBranchName() + "' is behind the remote branch");
                }
            }

            //get the hotfix branch
            List<Ref> hotfixBranches = GitHelper.listBranchesWithPrefix(flow.git(), flow.getHotfixBranchPrefix(), flow.getReporter());

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
            if (GitHelper.remoteBranchExists(flow.git(), prefixedBranchName, reporter))
            {
                if (GitHelper.localBranchBehindRemote(flow.git(), prefixedBranchName, reporter))
                {
                    reporter.errorText("hotfix-finish", "local branch '" + prefixedBranchName + "' is behind the remote branch");
                    reporter.flush();
                    throw new BranchOutOfDateException("local branch '" + prefixedBranchName + "' is behind the remote branch");
                }
            }

            if (GitHelper.remoteBranchExists(flow.git(), flow.getMasterBranchName(), flow.getReporter()))
            {
                if (ctx.isPullMaster())
                {
                    reporter.debugText("finishHotfix", "pulling master before remote behind check");
                    reporter.flush();

                    flow.git().checkout().setName(flow.getMasterBranchName()).call();
                    flow.git().pull().call();
                    flow.git().checkout().setName(prefixedBranchName).call();
                }

                if (GitHelper.localBranchBehindRemote(flow.git(), flow.getMasterBranchName(), flow.getReporter()))
                {
                    reporter.errorText("hotfix-finish", "local branch '" + flow.getMasterBranchName() + "' is behind the remote branch");
                    reporter.flush();
                    throw new BranchOutOfDateException("local branch '" + flow.getMasterBranchName() + "' is behind the remote branch");
                }
            }

            //get the reactor projects for hotfix
            MavenSession hotfixSession = mavenExecutionHelper.getSessionForBranch(prefixedBranchName, ReleaseUtil.getRootProject(originalProjects), session);
            List<MavenProject> hotfixProjects = hotfixSession.getSortedProjects();

            updateHotfixPomsWithRelease(hotfixLabel, originalProjects, session);
            projectHelper.commitAllPoms(flow.git(), originalProjects, ctx.getScmCommentPrefix() + "updating poms for " + hotfixLabel + " hotfix" + ctx.getScmCommentSuffix());

            //reload the reactor projects for hotfix
            hotfixSession = mavenExecutionHelper.getSessionForBranch(prefixedBranchName, ReleaseUtil.getRootProject(originalProjects), session);
            hotfixProjects = hotfixSession.getSortedProjects();

            projectHelper.checkPomForVersionState(VersionState.RELEASE, hotfixProjects);

            if (!ctx.isAllowSnapshots())
            {
                List<String> snapshots = projectHelper.checkForNonReactorSnapshots(ProjectCacheKey.HOTFIX_BRANCH, hotfixProjects);
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
                    mavenExecutionHelper.execute(rootProject, hotfixSession);
                }
                catch (MavenExecutorException e)
                {
                    throw new JGitFlowReleaseException("Error building: " + e.getMessage(), e);
                }
            }

            //We need to commit the hotfix versioned poms to develop to avoid a merge conflict

            //reload the reactor projects for develop
            MavenSession developSession = mavenExecutionHelper.getSessionForBranch(flow.getDevelopBranchName(), ReleaseUtil.getRootProject(originalProjects), session);
            List<MavenProject> developProjects = developSession.getSortedProjects();

            Map<String, String> developVersions = versionProvider.getOriginalVersions(ProjectCacheKey.DEVELOP_BRANCH, developProjects);

            flow.git().checkout().setName(flow.getDevelopBranchName()).call();

            pomUpdater.copyPomVersionsFromProject(developProjects, hotfixProjects);

            flow.git().add().addFilepattern(".").call();
            flow.git().commit().setMessage(ctx.getScmCommentPrefix() + "updating develop with hotfix versions to avoid merge conflicts").call();

            flow.git().checkout().setName(prefixedBranchName);

            if (ctx.isPushHotfixes() || !ctx.isNoTag())
            {
                setupHelper.ensureOrigin();
            }

            getLogger().info("running jgitflow hotfix finish...");

            ReleaseMergeResult mergeResult = flow.hotfixFinish(hotfixLabel)
                                                 .setPush(ctx.isPushHotfixes())
                                                 .setKeepBranch(ctx.isKeepBranch())
                                                 .setNoTag(ctx.isNoTag())
                                                 .setMessage(ReleaseUtil.interpolate(ctx.getTagMessage(), rootProject.getModel()))
                                                 .setAllowUntracked(ctx.isAllowUntracked())
                                                 .setScmMessagePrefix(ctx.getScmCommentPrefix())
                                                 .setScmMessageSuffix(ctx.getScmCommentSuffix())
                                                 .call();

            if (!mergeResult.wasSuccessful())
            {
                if (mergeResult.masterHasProblems())
                {
                    getLogger().error("Error merging into " + flow.getMasterBranchName() + ":");
                    getLogger().error(mergeResult.getMasterResult().toString());
                    getLogger().error("see .git/jgitflow.log for more info");
                }

                if (mergeResult.developHasProblems())
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
            developSession = mavenExecutionHelper.getSessionForBranch(flow.getDevelopBranchName(), ReleaseUtil.getRootProject(originalProjects), session);
            developProjects = developSession.getSortedProjects();
            pomUpdater.copyPomVersionsFromMap(developProjects, developVersions);

            projectHelper.commitAllPoms(flow.git(), developProjects, ctx.getScmCommentPrefix() + "updating poms for development" + ctx.getScmCommentSuffix());

            if (ctx.isPushHotfixes())
            {
                RefSpec developSpec = new RefSpec(ctx.getFlowInitContext().getDevelop());
                flow.git().push().setRemote(Constants.DEFAULT_REMOTE_NAME).setRefSpecs(developSpec).call();
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
        catch (IOException e)
        {
            throw new JGitFlowReleaseException("Error releasing: " + e.getMessage(), e);
        }
        catch (ReactorReloadException e)
        {
            throw new JGitFlowReleaseException("Error releasing: " + e.getMessage(), e);
        }
    }

    private void updateHotfixPomsWithRelease(String hotfixLabel, List<MavenProject> originalProjects, MavenSession session) throws JGitFlowReleaseException
    {
        try
        {
            ReleaseContext ctx = contextProvider.getContext();
            JGitFlow flow = jGitFlowProvider.gitFlow();
            //reload the reactor projects for hotfix
            MavenSession hotfixSession = mavenExecutionHelper.getSessionForBranch(flow.getHotfixBranchPrefix() + hotfixLabel, ReleaseUtil.getRootProject(originalProjects), session);
            List<MavenProject> hotfixProjects = hotfixSession.getSortedProjects();

            pomUpdater.removeSnapshotFromPomVersions(ProjectCacheKey.HOTFIX_LABEL, hotfixLabel, "", hotfixProjects);

            projectHelper.commitAllPoms(flow.git(), hotfixProjects, ctx.getScmCommentPrefix() + "updating poms for " + hotfixLabel + " hotfix" + ctx.getScmCommentSuffix());
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
        catch (JGitFlowException e)
        {
            throw new JGitFlowReleaseException("Error starting hotfix: " + e.getMessage(), e);
        }
    }

    private void updateHotfixPomsWithSnapshot(String hotfixLabel, List<MavenProject> originalProjects, MavenSession session) throws JGitFlowReleaseException
    {
        try
        {
            ReleaseContext ctx = contextProvider.getContext();
            JGitFlow flow = jGitFlowProvider.gitFlow();
            //reload the reactor projects for hotfix
            MavenSession hotfixSession = mavenExecutionHelper.getSessionForBranch(flow.getHotfixBranchPrefix() + hotfixLabel, ReleaseUtil.getRootProject(originalProjects), session);
            List<MavenProject> hotfixProjects = hotfixSession.getSortedProjects();

            //updatePomsWithHotfixSnapshotVersion(ProjectCacheKey.HOTFIX_LABEL, hotfixLabel, ctx, hotfixProjects, config);
            pomUpdater.removeSnapshotFromPomVersions(ProjectCacheKey.HOTFIX_LABEL, hotfixLabel, "", hotfixProjects);

            projectHelper.commitAllPoms(flow.git(), hotfixProjects, ctx.getScmCommentPrefix() + "updating poms for " + hotfixLabel + " hotfix" + ctx.getScmCommentSuffix());
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
        catch (JGitFlowException e)
        {
            throw new JGitFlowReleaseException("Error starting hotfix: " + e.getMessage(), e);
        }
    }

}
