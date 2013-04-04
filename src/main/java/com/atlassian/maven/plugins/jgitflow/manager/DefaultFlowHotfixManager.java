package com.atlassian.maven.plugins.jgitflow.manager;

import java.util.List;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.exception.JGitFlowException;
import com.atlassian.jgitflow.core.exception.LocalBranchExistsException;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.JGitFlowReleaseException;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.errors.GitAPIException;

/**
 * @since version
 */
public class DefaultFlowHotfixManager extends AbstractFlowReleaseManager
{
    @Override
    public void start(ReleaseContext ctx, List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        checkPomForSnapshot(reactorProjects);

        JGitFlow flow = null;
        String releaseLabel = getReleaseLabel(ctx,reactorProjects);
        try
        {
            flow = JGitFlow.getOrInit(ctx.getBaseDir(),ctx.getFlowInitContext());
            flow.hotfixStart(releaseLabel).call();
        }
        catch (LocalBranchExistsException e)
        {
            //since the release branch already exists, just check it out
            try
            {
                flow.git().checkout().setName(flow.getHotfixBranchPrefix() + releaseLabel).call();
            }
            catch (GitAPIException e1)
            {
                throw new JGitFlowReleaseException("Error checking out existing hotfix branch: " + e1.getMessage(), e1);
            }
        }
        catch (JGitFlowException e)
        {
            throw new JGitFlowReleaseException("Error starting hotfix: " + e.getMessage(), e);
        }

        updatePomsWithReleaseVersion(ctx, reactorProjects);

        commitAllChanges(flow.git(),"updating poms for " + releaseLabel + " hotfix");
    }

    @Override
    public void finish(ReleaseContext ctx, List<MavenProject> reactorProjects, MavenSession session) throws JGitFlowReleaseException
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }
}
