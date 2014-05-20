package com.atlassian.maven.plugins.jgitflow.manager;

import java.util.List;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.exception.JGitFlowException;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.MavenJGitFlowException;
import com.atlassian.maven.plugins.jgitflow.helper.JGitFlowSetupHelper;
import com.atlassian.maven.plugins.jgitflow.manager.tasks.CheckoutAndGetProjects;
import com.atlassian.maven.plugins.jgitflow.manager.tasks.SetupOriginAndFetchIfNeeded;
import com.atlassian.maven.plugins.jgitflow.manager.tasks.VerifyInitialVersionState;
import com.atlassian.maven.plugins.jgitflow.provider.ContextProvider;
import com.atlassian.maven.plugins.jgitflow.provider.JGitFlowProvider;
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

    @Requirement
    protected JGitFlowProvider jGitFlowProvider;

    @Requirement
    protected SetupOriginAndFetchIfNeeded setupOriginAndFetchIfNeeded;

    @Requirement
    protected JGitFlowSetupHelper setupHelper;

    @Requirement
    protected CheckoutAndGetProjects checkoutAndGetProjects;

    @Requirement
    protected VerifyInitialVersionState verifyInitialVersionState;
    
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

    public void runPreflight(ReleaseContext ctx, List<MavenProject> reactorProjects, MavenSession session) throws JGitFlowException, MavenJGitFlowException
    {
        setupProviders(ctx, session, reactorProjects);

        JGitFlow flow = jGitFlowProvider.gitFlow();

        setupHelper.runCommonSetup();

        setupOriginAndFetchIfNeeded.run();

    }
}
