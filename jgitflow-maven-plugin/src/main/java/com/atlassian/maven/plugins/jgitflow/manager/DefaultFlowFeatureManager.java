package com.atlassian.maven.plugins.jgitflow.manager;

import java.io.IOException;
import java.util.List;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.JGitFlowReporter;
import com.atlassian.jgitflow.core.exception.BranchOutOfDateException;
import com.atlassian.jgitflow.core.exception.JGitFlowException;
import com.atlassian.jgitflow.core.util.GitHelper;
import com.atlassian.maven.plugins.jgitflow.BranchType;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.MavenJGitFlowException;
import com.atlassian.maven.plugins.jgitflow.exception.ReactorReloadException;
import com.atlassian.maven.plugins.jgitflow.extension.FeatureFinishPluginExtension;
import com.atlassian.maven.plugins.jgitflow.extension.FeatureStartPluginExtension;
import com.atlassian.maven.plugins.jgitflow.helper.*;
import com.atlassian.maven.plugins.jgitflow.provider.BranchLabelProvider;
import com.atlassian.maven.plugins.jgitflow.provider.ContextProvider;
import com.atlassian.maven.plugins.jgitflow.provider.JGitFlowProvider;
import com.atlassian.maven.plugins.jgitflow.provider.ProjectCacheKey;
import com.atlassian.maven.plugins.jgitflow.util.NamingUtil;

import com.google.common.base.Splitter;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.release.exec.MavenExecutorException;
import org.apache.maven.shared.release.util.ReleaseUtil;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.RefSpec;

/**
 * @since version
 */
@Component(role = FlowReleaseManager.class, hint = "feature")
public class DefaultFlowFeatureManager extends AbstractFlowReleaseManager
{
    @Requirement
    private MavenExecutionHelper mavenExecutionHelper;

    @Requirement
    private BranchLabelProvider labelProvider;

    @Requirement
    private PomUpdater pomUpdater;

    @Requirement
    private FeatureStartPluginExtension startExtension;

    @Requirement
    private FeatureFinishPluginExtension finishExtension;

    @Override
    public void start(ReleaseContext ctx, List<MavenProject> reactorProjects, MavenSession session) throws MavenJGitFlowException
    {
        JGitFlow flow = null;
        
        try
        {
            String featureName = getStartLabelAndRunPreflight(ctx,reactorProjects,session);
            
            flow = jGitFlowProvider.gitFlow();

            startExtension.init();

            flow.featureStart(featureName)
                .setAllowUntracked(ctx.isAllowUntracked())
                .setPush(ctx.isPushFeatures())
                .setStartCommit(ctx.getStartCommit())
                .setScmMessagePrefix(ctx.getScmCommentPrefix())
                .setScmMessageSuffix(ctx.getScmCommentSuffix())
                .setExtension(startExtension)
                .call();
        }
        catch (JGitFlowException e)
        {
            throw new MavenJGitFlowException("Error starting feature: " + e.getMessage(), e);
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

            String featureLabel = getFinishLabelAndRunPreflight(ctx,reactorProjects,session);
            flow = jGitFlowProvider.gitFlow();

            JGitFlowReporter reporter = flow.getReporter();

            getLogger().info("running jgitflow feature finish...");
            
            MergeResult mergeResult = flow.featureFinish(featureLabel)
                .setKeepBranch(ctx.isKeepBranch())
                .setSquash(ctx.isSquash())
                .setRebase(ctx.isFeatureRebase())
                .setAllowUntracked(ctx.isAllowUntracked())
                .setPush(ctx.isPushFeatures())
                .setNoMerge(ctx.isNoFeatureMerge())
                .setScmMessagePrefix(ctx.getScmCommentPrefix())
                .setScmMessageSuffix(ctx.getScmCommentSuffix())
                .setExtension(finishExtension)
                .call();
            
            if (!mergeResult.getMergeStatus().isSuccessful())
            {
                getLogger().error("Error merging into " + flow.getDevelopBranchName() + ":");
                getLogger().error(mergeResult.toString());
                getLogger().error("see .git/jgitflow.log for more info");

                throw new MavenJGitFlowException("Error while merging feature!");
            }

        }
        catch (JGitFlowException e)
        {
            throw new MavenJGitFlowException("Error finish feature: " + e.getMessage(), e);
        }
        catch (GitAPIException e)
        {
            throw new MavenJGitFlowException("Error finish feature: " + e.getMessage(), e);
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
    public void deploy(ReleaseContext ctx, List<MavenProject> reactorProjects, MavenSession session, String buildNumber, String goals) throws MavenJGitFlowException
    {
        JGitFlow flow = null;

        try
        {
            String featureLabel = getFinishLabelAndRunPreflight(ctx,reactorProjects,session);
            flow = jGitFlowProvider.gitFlow();

            JGitFlowReporter reporter = flow.getReporter();

            SessionAndProjects sessionAndProjects = checkoutAndGetProjects.run(flow.getFeatureBranchPrefix() + featureLabel);

            List<MavenProject> featureProjects = sessionAndProjects.getProjects();
            MavenSession featureSession = sessionAndProjects.getSession();
            
            String featureVersion = NamingUtil.camelCaseOrSpaceToDashed(featureLabel);
            featureVersion = StringUtils.replace(featureVersion, "-", "_");

            if (StringUtils.isNotBlank(buildNumber))
            {
                featureVersion = featureVersion + "-build" + buildNumber;
            }
            else
            {
                featureVersion = featureVersion + "-SNAPSHOT";
            }

            pomUpdater.removeSnapshotFromFeatureVersions(ProjectCacheKey.FEATURE_DEPLOY_LABEL, featureVersion, reactorProjects);

            MavenProject rootProject = ReleaseUtil.getRootProject(featureProjects);
            featureSession = mavenExecutionHelper.reloadReactor(rootProject, session);

            rootProject = ReleaseUtil.getRootProject(featureSession.getSortedProjects());


            if (!ctx.isNoBuild())
            {
                String mvnGoals = "clean install deploy";
                if (StringUtils.isNotBlank(goals))
                {
                    mvnGoals = goals;
                }

                try
                {
                    for (String goal : Splitter.on(" ").trimResults().omitEmptyStrings().split(mvnGoals))
                    {
                        mavenExecutionHelper.execute(rootProject, featureSession, goal);
                    }
                }
                catch (MavenExecutorException e)
                {
                    throw new MavenJGitFlowException("Error building: " + e.getMessage(), e);
                }
            }

            //revert our local changes
            flow.git().reset().setMode(ResetCommand.ResetType.HARD).call();

        }
        catch (JGitFlowException e)
        {
            throw new MavenJGitFlowException("Error finish feature: " + e.getMessage(), e);
        }
        catch (GitAPIException e)
        {
            throw new MavenJGitFlowException("Error finish feature: " + e.getMessage(), e);
        }
        catch (ReactorReloadException e)
        {
            throw new MavenJGitFlowException("Error finish feature: " + e.getMessage(), e);
        }
        finally
        {
            if (null != flow)
            {
                flow.getReporter().flush();
            }
        }
    }

    

    public String getStartLabelAndRunPreflight(ReleaseContext ctx, List<MavenProject> reactorProjects, MavenSession session) throws JGitFlowException, MavenJGitFlowException
    {
        runPreflight(ctx,reactorProjects,session);

        JGitFlow flow = jGitFlowProvider.gitFlow();

        //make sure we're on develop
        List<MavenProject> branchProjects = checkoutAndGetProjects.run(flow.getDevelopBranchName()).getProjects();

        verifyInitialVersionState.run(BranchType.FEATURE, branchProjects);
        
        return labelProvider.getFeatureStartName();

    }

    public String getFinishLabelAndRunPreflight(ReleaseContext ctx, List<MavenProject> reactorProjects, MavenSession session) throws JGitFlowException, MavenJGitFlowException
    {
        runPreflight(ctx,reactorProjects,session);

        return labelProvider.getFeatureFinishName();

    }

}
