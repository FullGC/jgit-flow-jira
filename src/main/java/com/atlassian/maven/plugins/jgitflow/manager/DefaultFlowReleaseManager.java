package com.atlassian.maven.plugins.jgitflow.manager;

import java.util.List;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.exception.JGitFlowException;
import com.atlassian.jgitflow.core.exception.ReleaseBranchExistsException;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.JGitFlowReleaseException;

import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.errors.GitAPIException;

/**
 * @since version
 */
public class DefaultFlowReleaseManager extends AbstractFlowReleaseManager
{
    @Override
    public void start(ReleaseContext ctx, List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        checkPom(reactorProjects);
        
        JGitFlow flow = null;
        String releaseLabel = getReleaseLabel(ctx,reactorProjects);
        try
        {
            flow = JGitFlow.getOrInit(ctx.getBaseDir(),ctx.getFlowInitContext());
            flow.releaseStart(releaseLabel).call();
        }
        catch (ReleaseBranchExistsException e)
        {
            //since the release branch already exists, just check it out
            try
            {
                flow.git().checkout().setName(flow.getReleaseBranchPrefix() + releaseLabel).call();
            }
            catch (GitAPIException e1)
            {
                throw new JGitFlowReleaseException("Error checking out existing release branch: " + e1.getMessage(), e1);
            }
        }
        catch (JGitFlowException e)
        {
            throw new JGitFlowReleaseException("Error starting release: " + e.getMessage(), e);
        }

        updatePoms(ctx, reactorProjects);

        commitAllChanges(flow.git(),"updating poms for " + releaseLabel + " release");
    }

}
