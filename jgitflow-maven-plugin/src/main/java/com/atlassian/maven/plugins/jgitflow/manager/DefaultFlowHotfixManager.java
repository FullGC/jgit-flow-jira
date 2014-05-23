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
import com.atlassian.maven.plugins.jgitflow.BranchType;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.VersionState;
import com.atlassian.maven.plugins.jgitflow.VersionType;
import com.atlassian.maven.plugins.jgitflow.exception.MavenJGitFlowException;
import com.atlassian.maven.plugins.jgitflow.exception.ReactorReloadException;
import com.atlassian.maven.plugins.jgitflow.exception.UnresolvedSnapshotsException;
import com.atlassian.maven.plugins.jgitflow.extension.HotfixFinishPluginExtension;
import com.atlassian.maven.plugins.jgitflow.extension.HotfixStartPluginExtension;
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
public class DefaultFlowHotfixManager extends AbstractProductionBranchManager
{
    @Requirement
    private HotfixStartPluginExtension startExtension;

    @Requirement
    private HotfixFinishPluginExtension finishExtension;

    public DefaultFlowHotfixManager()
    {
        super(BranchType.HOTFIX);
    }

    @Override
    public void start(ReleaseContext ctx, List<MavenProject> reactorProjects, MavenSession session) throws MavenJGitFlowException
    {
        JGitFlow flow = null;

        try
        {
            String hotfixLabel = getStartLabelAndRunPreflight(ctx, reactorProjects, session);

            flow = jGitFlowProvider.gitFlow();

            startExtension.init(ctx.getHotfixStartExtension());

            flow.hotfixStart(hotfixLabel)
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
            throw new MavenJGitFlowException("Error starting hotfix: " + e.getMessage(), e);
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
            finishExtension.init(ctx.getHotfixFinishExtension());
            String hotfixLabel = getFinishLabelAndRunPreflight(ctx,reactorProjects,session);

            flow = jGitFlowProvider.gitFlow();

            getLogger().info("running jgitflow hotfix finish...");
            ReleaseMergeResult mergeResult = flow.hotfixFinish(hotfixLabel)
                                                 .setPush(ctx.isPushReleases())
                                                 .setKeepBranch(ctx.isKeepBranch())
                                                 .setNoTag(ctx.isNoTag())
                                                 .setAllowUntracked(ctx.isAllowUntracked())
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

                throw new MavenJGitFlowException("Error while merging hotfix!");
            }
            
        }
        catch (JGitFlowException e)
        {
            throw new MavenJGitFlowException("Error finishing hotfix: " + e.getMessage(), e);
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
