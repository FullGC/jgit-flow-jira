package com.atlassian.maven.plugins.jgitflow.manager.tasks;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.MavenJGitFlowException;
import com.atlassian.maven.plugins.jgitflow.helper.MavenExecutionHelper;
import com.atlassian.maven.plugins.jgitflow.helper.SessionAndProjects;
import com.atlassian.maven.plugins.jgitflow.provider.ContextProvider;
import com.atlassian.maven.plugins.jgitflow.provider.JGitFlowProvider;
import com.atlassian.maven.plugins.jgitflow.provider.MavenSessionProvider;
import com.atlassian.maven.plugins.jgitflow.provider.ReactorProjectsProvider;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.shared.release.util.ReleaseUtil;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

@Component(role = CheckoutAndGetProjects.class)
public class CheckoutAndGetProjects
{
    @Requirement
    private MavenExecutionHelper mavenExecutionHelper;

    @Requirement
    private JGitFlowProvider jGitFlowProvider;

    @Requirement
    private MavenSessionProvider sessionProvider;

    @Requirement
    private ContextProvider contextProvider;

    @Requirement
    private ReactorProjectsProvider reactorProjectsProvider;

    public SessionAndProjects run(String branchName) throws MavenJGitFlowException
    {
        try
        {
            JGitFlow flow = jGitFlowProvider.gitFlow();
            ReleaseContext ctx = contextProvider.getContext();

            flow.git().checkout().setName(branchName).call();

            //reload the reactor projects for develop
            MavenSession branchSession = mavenExecutionHelper.getSessionForBranch(branchName, ReleaseUtil.getRootProject(reactorProjectsProvider.getReactorProjects()), sessionProvider.getSession());

            return new SessionAndProjects(branchSession, branchSession.getSortedProjects());
        }
        catch (Exception e)
        {
            throw new MavenJGitFlowException("Error checking out branch and loading projects", e);
        }
    }
}
