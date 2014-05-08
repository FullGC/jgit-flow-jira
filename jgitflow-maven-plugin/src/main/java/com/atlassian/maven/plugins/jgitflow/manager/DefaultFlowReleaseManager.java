package com.atlassian.maven.plugins.jgitflow.manager;

import java.io.IOException;
import java.util.List;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.JGitFlowReporter;
import com.atlassian.jgitflow.core.ReleaseMergeResult;
import com.atlassian.jgitflow.core.exception.BranchOutOfDateException;
import com.atlassian.jgitflow.core.exception.JGitFlowException;
import com.atlassian.jgitflow.core.exception.ReleaseBranchExistsException;
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
import com.atlassian.maven.plugins.jgitflow.provider.BranchLabelProvider;
import com.atlassian.maven.plugins.jgitflow.provider.ContextProvider;
import com.atlassian.maven.plugins.jgitflow.provider.JGitFlowProvider;
import com.atlassian.maven.plugins.jgitflow.provider.ProjectCacheKey;

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
@Component(role = FlowReleaseManager.class, hint = "release")
public class DefaultFlowReleaseManager extends AbstractFlowReleaseManager
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
    private JGitFlowProvider jGitFlowProvider;

    @Requirement
    private ContextProvider contextProvider;

    @Override
    public void start(List<MavenProject> reactorProjects, MavenSession session) throws JGitFlowReleaseException
    {
        JGitFlow flow = null;
        ReleaseContext ctx = contextProvider.getContext();
        try
        {
            flow = jGitFlowProvider.gitFlow();

            setupHelper.runCommonSetup();

            String releaseLabel = startRelease(reactorProjects, session);

            updateReleasePomsWithSnapshot(releaseLabel, reactorProjects, session);

            if (ctx.isPushReleases())
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

            if (ctx.isPushReleases() || !ctx.isNoTag())
            {
                setupHelper.ensureOrigin();
            }

            finishRelease(originalProjects, session);
        }
        catch (JGitFlowException e)
        {
            throw new JGitFlowReleaseException("Error finishing release: " + e.getMessage(), e);
        }
        finally
        {
            if (null != flow)
            {
                flow.getReporter().flush();
            }
        }
    }

    private String startRelease(List<MavenProject> originalProjects, MavenSession session) throws JGitFlowReleaseException
    {
        String releaseLabel = "";

        try
        {
            ReleaseContext ctx = contextProvider.getContext();
            JGitFlow flow = jGitFlowProvider.gitFlow();

            //make sure we're on develop
            flow.git().checkout().setName(flow.getDevelopBranchName()).call();

            //reload the reactor projects for develop
            MavenSession developSession = mavenExecutionHelper.getSessionForBranch(flow.getDevelopBranchName(), ReleaseUtil.getRootProject(originalProjects), session);
            List<MavenProject> developProjects = developSession.getSortedProjects();

            projectHelper.checkPomForVersionState(VersionState.SNAPSHOT, developProjects);

            if (!ctx.isAllowSnapshots())
            {
                List<String> snapshots = projectHelper.checkForNonReactorSnapshots(ProjectCacheKey.DEVELOP_BRANCH, developProjects);
                if (!snapshots.isEmpty())
                {
                    String details = Joiner.on(ls).join(snapshots);
                    throw new UnresolvedSnapshotsException("Cannot start a release due to snapshot dependencies:" + ls + details);
                }
            }

            if (ctx.isRemoteAllowed())
            {
                setupHelper.ensureOrigin();
            }

            releaseLabel = labelProvider.getVersionLabel(VersionType.RELEASE, ProjectCacheKey.RELEASE_START_LABEL, developProjects);

            flow.releaseStart(releaseLabel)
                .setAllowUntracked(ctx.isAllowUntracked())
                .setPush(ctx.isPushReleases())
                .setStartCommit(ctx.getStartCommit())
                .setScmMessagePrefix(ctx.getScmCommentPrefix())
                .setScmMessageSuffix(ctx.getScmCommentSuffix())
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

    private void finishRelease(List<MavenProject> originalProjects, MavenSession session) throws JGitFlowReleaseException
    {
        String releaseLabel = "";

        try
        {
            ReleaseContext ctx = contextProvider.getContext();
            JGitFlow flow = jGitFlowProvider.gitFlow();
            JGitFlowReporter reporter = flow.getReporter();
            MavenProject originalRootProject = ReleaseUtil.getRootProject(originalProjects);

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
                    reporter.errorText("release-finish", "local branch '" + flow.getDevelopBranchName() + "' is behind the remote branch");
                    reporter.flush();
                    throw new BranchOutOfDateException("local branch '" + flow.getDevelopBranchName() + "' is behind the remote branch");
                }
            }

            //get the release branch
            List<Ref> releaseBranches = GitHelper.listBranchesWithPrefix(flow.git(), flow.getReleaseBranchPrefix(), flow.getReporter());

            if (releaseBranches.isEmpty())
            {
                throw new JGitFlowReleaseException("Could not find release branch!");
            }

            //there can be only one
            String rheadPrefix = Constants.R_HEADS + flow.getReleaseBranchPrefix();
            Ref releaseBranch = releaseBranches.get(0);

            if (null == releaseBranch)
            {
                throw new JGitFlowReleaseException("Could not find release branch!");
            }

            releaseLabel = releaseBranch.getName().substring(releaseBranch.getName().indexOf(rheadPrefix) + rheadPrefix.length());

            String prefixedBranchName = flow.getReleaseBranchPrefix() + releaseLabel;

            //make sure we're on the release branch
            if (getLogger().isDebugEnabled())
            {
                getLogger().debug("checking out release branch: " + prefixedBranchName);
            }

            flow.git().checkout().setName(prefixedBranchName).call();

            //make sure we're not behind remote
            if (GitHelper.remoteBranchExists(flow.git(), prefixedBranchName, reporter))
            {
                if (GitHelper.localBranchBehindRemote(flow.git(), prefixedBranchName, reporter))
                {
                    reporter.errorText("release-finish", "local branch '" + prefixedBranchName + "' is behind the remote branch");
                    reporter.flush();
                    throw new BranchOutOfDateException("local branch '" + prefixedBranchName + "' is behind the remote branch");
                }
            }

            if (GitHelper.remoteBranchExists(flow.git(), flow.getMasterBranchName(), flow.getReporter()))
            {
                if (ctx.isPullMaster())
                {
                    reporter.debugText("finishRelease", "pulling master before remote behind check");
                    reporter.flush();

                    flow.git().checkout().setName(flow.getMasterBranchName()).call();
                    flow.git().pull().call();
                    flow.git().checkout().setName(prefixedBranchName).call();
                }

                if (GitHelper.localBranchBehindRemote(flow.git(), flow.getMasterBranchName(), flow.getReporter()))
                {
                    reporter.errorText("release-finish", "local branch '" + flow.getMasterBranchName() + "' is behind the remote branch");
                    reporter.flush();
                    throw new BranchOutOfDateException("local branch '" + flow.getMasterBranchName() + "' is behind the remote branch");
                }
            }

            //get the reactor projects for release
            MavenSession releaseSession = mavenExecutionHelper.getSessionForBranch(prefixedBranchName, originalRootProject, session);
            List<MavenProject> releaseProjects = releaseSession.getSortedProjects();

            if (getLogger().isDebugEnabled())
            {
                getLogger().debug("updating release poms with release...");
            }
            updateReleasePomsWithRelease(releaseLabel, originalProjects, session);
            projectHelper.commitAllPoms(flow.git(), originalProjects, ctx.getScmCommentPrefix() + "updating poms for " + releaseLabel + " release" + ctx.getScmCommentSuffix());

            //reload the reactor projects for release
            releaseSession = mavenExecutionHelper.getSessionForBranch(prefixedBranchName, originalRootProject, session);
            releaseProjects = releaseSession.getSortedProjects();

            projectHelper.checkPomForVersionState(VersionState.RELEASE, releaseProjects);

            if (!ctx.isAllowSnapshots())
            {
                List<String> snapshots = projectHelper.checkForNonReactorSnapshots(ProjectCacheKey.RELEASE_BRANCH, releaseProjects);
                if (!snapshots.isEmpty())
                {
                    String details = Joiner.on(ls).join(snapshots);
                    throw new UnresolvedSnapshotsException("Cannot finish a release due to snapshot dependencies:" + ls + details);
                }
            }

            MavenProject rootProject = ReleaseUtil.getRootProject(releaseProjects);

            if (!ctx.isNoBuild())
            {
                if (getLogger().isDebugEnabled())
                {
                    getLogger().debug("building project...");
                }
                try
                {
                    mavenExecutionHelper.execute(rootProject, releaseSession);
                }
                catch (MavenExecutorException e)
                {
                    throw new JGitFlowReleaseException("Error building: " + e.getMessage(), e);
                }
            }

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

                throw new JGitFlowReleaseException("Error while merging release!");
            }

            //make sure we're on develop
            flow.git().checkout().setName(flow.getDevelopBranchName()).call();

            //reload the reactor projects for develop
            MavenSession developSession = mavenExecutionHelper.getSessionForBranch(flow.getDevelopBranchName(), originalRootProject, session);
            List<MavenProject> developProjects = developSession.getSortedProjects();

            String developLabel = labelProvider.getVersionLabel(VersionType.DEVELOPMENT, ProjectCacheKey.DEVELOP_BRANCH, developProjects);

            pomUpdater.updatePomsWithNextDevelopmentVersion(ProjectCacheKey.DEVELOP_BRANCH, developProjects);

            projectHelper.commitAllPoms(flow.git(), developProjects, ctx.getScmCommentPrefix() + "updating poms for " + developLabel + " development" + ctx.getScmCommentSuffix());

            if (ctx.isPushReleases())
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
        catch (ReactorReloadException e)
        {
            throw new JGitFlowReleaseException("Error releasing: " + e.getMessage(), e);
        }
        catch (IOException e)
        {
            throw new JGitFlowReleaseException("Error releasing: " + e.getMessage(), e);
        }
    }

    private void updateReleasePomsWithSnapshot(String releaseLabel, List<MavenProject> originalProjects, MavenSession session) throws JGitFlowReleaseException
    {
        try
        {
            ReleaseContext ctx = contextProvider.getContext();
            JGitFlow flow = jGitFlowProvider.gitFlow();
            //reload the reactor projects for release
            MavenSession releaseSession = mavenExecutionHelper.getSessionForBranch(flow.getReleaseBranchPrefix() + releaseLabel, ReleaseUtil.getRootProject(originalProjects), session);
            List<MavenProject> releaseProjects = releaseSession.getSortedProjects();

            // updatePomsWithReleaseSnapshotVersion(ProjectCacheKey.RELEASE_START_LABEL, releaseLabel, ctx, releaseProjects);
            pomUpdater.addSnapshotToPomVersions(ProjectCacheKey.RELEASE_START_LABEL, VersionType.RELEASE, releaseLabel, ctx.getReleaseBranchVersionSuffix(), releaseProjects);

            projectHelper.commitAllPoms(flow.git(), releaseProjects, ctx.getScmCommentPrefix() + "updating poms for " + releaseLabel + " release" + ctx.getScmCommentSuffix());
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
        catch (JGitFlowException e)
        {
            throw new JGitFlowReleaseException("Error starting release: " + e.getMessage(), e);
        }
    }

    private void updateReleasePomsWithRelease(String releaseLabel, List<MavenProject> originalProjects, MavenSession session) throws JGitFlowReleaseException
    {
        try
        {
            ReleaseContext ctx = contextProvider.getContext();
            JGitFlow flow = jGitFlowProvider.gitFlow();
            //reload the reactor projects for release
            MavenSession releaseSession = mavenExecutionHelper.getSessionForBranch(flow.getReleaseBranchPrefix() + releaseLabel, ReleaseUtil.getRootProject(originalProjects), session);
            List<MavenProject> releaseProjects = releaseSession.getSortedProjects();

            pomUpdater.removeSnapshotFromPomVersions(ProjectCacheKey.RELEASE_FINISH_LABEL, releaseLabel, ctx.getReleaseBranchVersionSuffix(), releaseProjects);

            projectHelper.commitAllPoms(flow.git(), releaseProjects, ctx.getScmCommentPrefix() + "updating poms for " + releaseLabel + " release" + ctx.getScmCommentSuffix());
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
        catch (JGitFlowException e)
        {
            throw new JGitFlowReleaseException("Error starting release: " + e.getMessage(), e);
        }
    }

}
