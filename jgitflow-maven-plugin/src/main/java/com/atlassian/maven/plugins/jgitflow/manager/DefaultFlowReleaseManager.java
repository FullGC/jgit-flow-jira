package com.atlassian.maven.plugins.jgitflow.manager;

import java.io.IOException;
import java.util.List;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.JGitFlowReporter;
import com.atlassian.jgitflow.core.ReleaseMergeResult;
import com.atlassian.jgitflow.core.exception.JGitFlowException;
import com.atlassian.maven.plugins.jgitflow.BranchType;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.VersionType;
import com.atlassian.maven.plugins.jgitflow.exception.MavenJGitFlowException;
import com.atlassian.maven.plugins.jgitflow.exception.ReactorReloadException;
import com.atlassian.maven.plugins.jgitflow.extension.ReleaseFinishPluginExtension;
import com.atlassian.maven.plugins.jgitflow.extension.ReleaseStartPluginExtension;
import com.atlassian.maven.plugins.jgitflow.provider.ProjectCacheKey;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.release.util.ReleaseUtil;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.RefSpec;

/**
 * @since version
 */
@Component(role = FlowReleaseManager.class, hint = "release")
public class DefaultFlowReleaseManager extends AbstractProductionBranchManager
{
    @Requirement
    private ReleaseStartPluginExtension startExtension;

    @Requirement
    private ReleaseFinishPluginExtension finishExtension;

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
            
            startExtension.init();

            flow.releaseStart(releaseLabel)
                .setAllowUntracked(ctx.isAllowUntracked())
                .setPush(ctx.isPushReleases())
                .setStartCommit(ctx.getStartCommit())
                .setScmMessagePrefix(ctx.getScmCommentPrefix())
                .setScmMessageSuffix(ctx.getScmCommentSuffix())
                .setExtension(startExtension)
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
    public void finish(ReleaseContext ctx, List<MavenProject> reactorProjects, MavenSession session) throws MavenJGitFlowException
    {
        JGitFlow flow = null;

        try
        {
            finishExtension.init();
            String releaseLabel = getFinishLabelAndRunPreflight(ctx,reactorProjects,session);

            flow = jGitFlowProvider.gitFlow();
            JGitFlowReporter reporter = flow.getReporter();
            MavenProject originalRootProject = ReleaseUtil.getRootProject(reactorProjects);

            getLogger().info("running jgitflow release finish...");
            ReleaseMergeResult mergeResult = flow.releaseFinish(releaseLabel)
                                                 .setPush(ctx.isPushReleases())
                                                 .setKeepBranch(ctx.isKeepBranch())
                                                 .setNoTag(ctx.isNoTag())
                                                 .setSquash(ctx.isSquash())
                                                 .setAllowUntracked(ctx.isAllowUntracked())
                                                 .setNoMerge(ctx.isNoReleaseMerge())
                                                 .setScmMessagePrefix(ctx.getScmCommentPrefix())
                                                 .setScmMessageSuffix(ctx.getScmCommentSuffix())
                                                 .setExtension(finishExtension)
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

            //-- MOVE this to release start
            //make sure we're on develop
            flow.git().checkout().setName(flow.getDevelopBranchName()).call();

            //reload the reactor projects for develop
            MavenSession developSession = mavenExecutionHelper.getSessionForBranch(flow.getDevelopBranchName(), originalRootProject, session);
            List<MavenProject> developProjects = developSession.getSortedProjects();

            String developLabel = labelProvider.getNextVersionLabel(VersionType.DEVELOPMENT, ProjectCacheKey.DEVELOP_BRANCH, developProjects);

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
            throw new MavenJGitFlowException("Error starting release: " + e.getMessage(), e);
        }
        catch (GitAPIException e)
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
        finally
        {
            if (null != flow)
            {
                flow.getReporter().flush();
            }
        }
    }
}
