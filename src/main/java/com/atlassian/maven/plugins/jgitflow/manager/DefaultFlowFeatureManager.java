package com.atlassian.maven.plugins.jgitflow.manager;

import java.io.IOException;
import java.util.List;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.JGitFlowConstants;
import com.atlassian.jgitflow.core.exception.JGitFlowException;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.JGitFlowReleaseException;
import com.atlassian.maven.plugins.jgitflow.exception.ReactorReloadException;
import com.atlassian.maven.plugins.jgitflow.util.NamingUtil;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.release.exec.MavenExecutorException;
import org.apache.maven.shared.release.util.ReleaseUtil;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.RefSpec;

/**
 * @since version
 */
public class DefaultFlowFeatureManager extends AbstractFlowReleaseManager
{
    @Override
    public void start(ReleaseContext ctx, List<MavenProject> reactorProjects, MavenSession session) throws JGitFlowReleaseException
    {
        JGitFlow flow = null;
        String featureName = null;
        try
        {
            flow = JGitFlow.getOrInit(ctx.getBaseDir(), ctx.getFlowInitContext());

            writeReportHeader(ctx,flow.getReporter());
            setupSshCredentialProviders(ctx,flow.getReporter());
            
            //make sure we're on develop
            flow.git().checkout().setName(flow.getDevelopBranchName()).call();

            featureName = getFeatureStartName(ctx, flow);

            flow.featureStart(featureName)
                .setAllowUntracked(ctx.isAllowUntracked())
                .setPush(ctx.isPushFeatures())
                .setStartCommit(ctx.getStartCommit())
                .call();
            
            if(ctx.isEnableFeatureVersions())
            {
                final String prefixedBranchName = flow.getFeatureBranchPrefix() + featureName;
                updateFeaturePomsWithFeatureVersion(featureName, flow, ctx, reactorProjects, session);
                
                if(ctx.isPushFeatures())
                {
                    RefSpec branchSpec = new RefSpec(prefixedBranchName);
                    flow.git().push().setRemote("origin").setRefSpecs(branchSpec).call();
                }
            }

            projectHelper.commitAllChanges(flow.git(), "updating poms for " + featureName + " branch");
        }
        catch (GitAPIException e)
        {
            throw new JGitFlowReleaseException("Error starting feature: " + e.getMessage(), e);
        }
        catch (JGitFlowException e)
        {
            throw new JGitFlowReleaseException("Error starting feature: " + e.getMessage(), e);
        }

    }

    @Override
    public void finish(ReleaseContext ctx, List<MavenProject> reactorProjects, MavenSession session) throws JGitFlowReleaseException
    {
        JGitFlow flow = null;

        MavenProject rootProject = ReleaseUtil.getRootProject(reactorProjects);
        MavenSession currentSession = session;

        try
        {
            flow = JGitFlow.getOrInit(ctx.getBaseDir(), ctx.getFlowInitContext());

            writeReportHeader(ctx,flow.getReporter());
            setupSshCredentialProviders(ctx,flow.getReporter());
            
            String featureLabel = getFeatureFinishName(ctx, flow);

            // make sure we are on specific feature branch
            flow.git().checkout().setName(flow.getFeatureBranchPrefix() + featureLabel).call();

            if(ctx.isEnableFeatureVersions())
            {
                updateFeaturePomsWithNonFeatureVersion(featureLabel, flow, ctx, reactorProjects, session);
                
                //reload the reactor projects
                MavenSession featureSession = getSessionForBranch(flow, flow.getFeatureBranchPrefix() + featureLabel, reactorProjects, session);
                List<MavenProject> featureProjects = featureSession.getSortedProjects();

                currentSession = featureSession;
                rootProject = ReleaseUtil.getRootProject(featureProjects);
            }

            if(!ctx.isNoBuild())
            {
                try
                {
                    mavenExecutionHelper.execute(rootProject, ctx, currentSession);
                }
                catch (MavenExecutorException e)
                {
                    throw new JGitFlowReleaseException("Error building: " + e.getMessage(), e);
                }
            }

            getLogger().info("running jgitflow feature finish...");
            flow.featureFinish(featureLabel)
                .setKeepBranch(ctx.isKeepBranch())
                .setSquash(ctx.isSquash())
                .setRebase(ctx.isFeatureRebase())
                .setAllowUntracked(ctx.isAllowUntracked())
                .setPush(ctx.isPushFeatures())
                .setNoMerge(ctx.isNoFeatureMerge())
                .call();

            //make sure we're on develop
            flow.git().checkout().setName(flow.getDevelopBranchName()).call();

        }
        catch (JGitFlowException e)
        {
            throw new JGitFlowReleaseException("Error finish feature: " + e.getMessage(), e);
        }
        catch (GitAPIException e)
        {
            throw new JGitFlowReleaseException("Error finish feature: " + e.getMessage(), e);
        }
        catch (ReactorReloadException e)
        {
            throw new JGitFlowReleaseException("Error finish feature: " + e.getMessage(), e);
        }
        catch (IOException e)
        {
            throw new JGitFlowReleaseException("Error finish feature: " + e.getMessage(), e);
        }
        finally
        {
            if(null != flow)
            {
                flow.getReporter().flush();
            }
        }
    }

    private void updateFeaturePomsWithFeatureVersion(String featureName, JGitFlow flow, ReleaseContext ctx, List<MavenProject> originalProjects, MavenSession session) throws JGitFlowReleaseException
    {
        try
        {
            //reload the reactor projects
            MavenSession featureSession = getSessionForBranch(flow, flow.getFeatureBranchPrefix() + featureName, originalProjects, session);
            List<MavenProject> featureProjects = featureSession.getSortedProjects();
            
            String featureVersion = NamingUtil.camelCaseOrSpaceToDashed(featureName);
            
            updatePomsWithFeatureVersion("featureStartLabel", featureVersion, ctx, featureProjects);

            projectHelper.commitAllChanges(flow.git(), "updating poms for " + featureVersion + " version");
        }
        catch (GitAPIException e)
        {
            throw new JGitFlowReleaseException("Error starting feature: " + e.getMessage(), e);
        }
        catch (ReactorReloadException e)
        {
            throw new JGitFlowReleaseException("Error starting feature: " + e.getMessage(), e);
        }
        catch (IOException e)
        {
            throw new JGitFlowReleaseException("Error starting feature: " + e.getMessage(), e);
        }
    }

    private void updateFeaturePomsWithNonFeatureVersion(String featureLabel, JGitFlow flow, ReleaseContext ctx, List<MavenProject> originalProjects, MavenSession session) throws JGitFlowReleaseException
    {
        try
        {
            //reload the reactor projects
            MavenSession featureSession = getSessionForBranch(flow, flow.getFeatureBranchPrefix() + featureLabel, originalProjects, session);
            List<MavenProject> featureProjects = featureSession.getSortedProjects();

            String featureVersion = NamingUtil.camelCaseOrSpaceToDashed(featureLabel);

            updatePomsWithNonFeatureVersion("featureFinishLabel", featureVersion, ctx, featureProjects);

            projectHelper.commitAllChanges(flow.git(), "updating poms for " + featureVersion + " version");
        }
        catch (GitAPIException e)
        {
            throw new JGitFlowReleaseException("Error finishing feature: " + e.getMessage(), e);
        }
        catch (ReactorReloadException e)
        {
            throw new JGitFlowReleaseException("Error finishing feature: " + e.getMessage(), e);
        }
        catch (IOException e)
        {
            throw new JGitFlowReleaseException("Error finishing feature: " + e.getMessage(), e);
        }
    }


}
