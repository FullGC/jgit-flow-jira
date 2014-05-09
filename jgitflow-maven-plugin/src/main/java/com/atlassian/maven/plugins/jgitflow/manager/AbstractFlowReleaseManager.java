package com.atlassian.maven.plugins.jgitflow.manager;

import java.util.List;

import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.MavenJGitFlowException;
import com.atlassian.maven.plugins.jgitflow.provider.ContextProvider;
import com.atlassian.maven.plugins.jgitflow.provider.MavenSessionProvider;
import com.atlassian.maven.plugins.jgitflow.provider.ReactorProjectsProvider;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.AbstractLogEnabled;

/**
 * @since version
 */
public abstract class AbstractFlowReleaseManager extends AbstractLogEnabled implements FlowReleaseManager
{
    @Requirement
    protected ContextProvider contextProvider;

    @Requirement
    protected MavenSessionProvider sessionProvider;

    @Requirement
    protected ReactorProjectsProvider projectsProvider;
    
    @Override
    public void deploy(ReleaseContext ctx, List<MavenProject> reactorProjects, MavenSession session, String buildNumber, String goals) throws MavenJGitFlowException
    {
        //do nothing. override if you need to
    }
    
    protected void setupProviders(ReleaseContext ctx, MavenSession session, List<MavenProject> projects)
    {
        contextProvider.setContext(ctx);
        sessionProvider.setSession(session);
        projectsProvider.setReactorProjects(projects);
    }
}
