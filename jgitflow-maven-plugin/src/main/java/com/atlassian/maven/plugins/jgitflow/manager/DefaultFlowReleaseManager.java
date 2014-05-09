package com.atlassian.maven.plugins.jgitflow.manager;

import java.io.IOException;
import java.util.List;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.JGitFlowReporter;
import com.atlassian.jgitflow.core.ReleaseMergeResult;
import com.atlassian.jgitflow.core.exception.BranchOutOfDateException;
import com.atlassian.jgitflow.core.exception.JGitFlowException;
import com.atlassian.jgitflow.core.util.GitHelper;
import com.atlassian.maven.plugins.jgitflow.BranchType;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.VersionState;
import com.atlassian.maven.plugins.jgitflow.VersionType;
import com.atlassian.maven.plugins.jgitflow.exception.MavenJGitFlowException;
import com.atlassian.maven.plugins.jgitflow.exception.ReactorReloadException;
import com.atlassian.maven.plugins.jgitflow.exception.UnresolvedSnapshotsException;
import com.atlassian.maven.plugins.jgitflow.extension.ReleaseStartPluginExtension;
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
public class DefaultFlowReleaseManager extends AbstractProductionBranchManager
{
    @Requirement
    private ReleaseStartPluginExtension extension;

    public DefaultFlowReleaseManager()
    {
        super(BranchType.RELEASE);
    }

    @Override
    public void start(ReleaseContext ctx, List<MavenProject> reactorProjects, MavenSession session) throws MavenJGitFlowException
    {
        JGitFlow flow = null;

        try
        {
            String releaseLabel = getStartLabelAndRunPreflight(ctx, reactorProjects, session);

            flow = jGitFlowProvider.gitFlow();
            
            extension.init();

            flow.releaseStart(releaseLabel)
                .setAllowUntracked(ctx.isAllowUntracked())
                .setPush(ctx.isPushReleases())
                .setStartCommit(ctx.getStartCommit())
                .setScmMessagePrefix(ctx.getScmCommentPrefix())
                .setScmMessageSuffix(ctx.getScmCommentSuffix())
                .setExtension(extension)
                .call();
        }
        catch (JGitFlowException e)
        {
            throw new MavenJGitFlowException("Error starting release: " + e.getMessage(), e);
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
    public void finish(ReleaseContext ctx, List<MavenProject> originalProjects, MavenSession session) throws MavenJGitFlowException
    {
        JGitFlow flow = null;

        setupProviders(ctx, session, originalProjects);

        try
        {
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
            throw new MavenJGitFlowException("Error finishing release: " + e.getMessage(), e);
        }
        finally
        {
            if (null != flow)
            {
                flow.getReporter().flush();
            }
        }
    }

    private void finishRelease(List<MavenProject> originalProjects, MavenSession session) throws MavenJGitFlowException
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
                throw new MavenJGitFlowException("Could not find release branch!");
            }

            //there can be only one
            String rheadPrefix = Constants.R_HEADS + flow.getReleaseBranchPrefix();
            Ref releaseBranch = releaseBranches.get(0);

            if (null == releaseBranch)
            {
                throw new MavenJGitFlowException("Could not find release branch!");
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
                    throw new MavenJGitFlowException("Error building: " + e.getMessage(), e);
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

                throw new MavenJGitFlowException("Error while merging release!");
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
            throw new MavenJGitFlowException("Error releasing: " + e.getMessage(), e);
        }
        catch (GitAPIException e)
        {
            throw new MavenJGitFlowException("Error releasing: " + e.getMessage(), e);
        }
        catch (ReleaseExecutionException e)
        {
            throw new MavenJGitFlowException("Error releasing: " + e.getMessage(), e);
        }
        catch (ReactorReloadException e)
        {
            throw new MavenJGitFlowException("Error releasing: " + e.getMessage(), e);
        }
        catch (IOException e)
        {
            throw new MavenJGitFlowException("Error releasing: " + e.getMessage(), e);
        }
    }

    private void updateReleasePomsWithRelease(String releaseLabel, List<MavenProject> originalProjects, MavenSession session) throws MavenJGitFlowException
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
            throw new MavenJGitFlowException("Error starting release: " + e.getMessage(), e);
        }
        catch (ReactorReloadException e)
        {
            throw new MavenJGitFlowException("Error starting release: " + e.getMessage(), e);
        }
        catch (IOException e)
        {
            throw new MavenJGitFlowException("Error starting release: " + e.getMessage(), e);
        }
        catch (JGitFlowException e)
        {
            throw new MavenJGitFlowException("Error starting release: " + e.getMessage(), e);
        }
    }

}
