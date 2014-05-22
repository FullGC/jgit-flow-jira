package com.atlassian.maven.plugins.jgitflow.manager.tasks;

import java.util.List;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.MavenJGitFlowException;
import com.atlassian.maven.plugins.jgitflow.helper.CurrentBranchHelper;
import com.atlassian.maven.plugins.jgitflow.helper.MavenExecutionHelper;
import com.atlassian.maven.plugins.jgitflow.provider.ContextProvider;
import com.atlassian.maven.plugins.jgitflow.provider.JGitFlowProvider;
import com.atlassian.maven.plugins.jgitflow.provider.MavenSessionProvider;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.release.util.ReleaseUtil;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

@Component(role = GetProjectsForBranch.class)
public class GetProjectsForBranch
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
    private CurrentBranchHelper currentBranchHelper;

    public List<MavenProject> run(String branchName, List<MavenProject> originalProjects) throws MavenJGitFlowException
    {
        try
        {
            JGitFlow flow = jGitFlowProvider.gitFlow();
            ReleaseContext ctx = contextProvider.getContext();
            
            String originalBranchName = currentBranchHelper.getBranchName();
            
            flow.git().checkout().setName(branchName).call();

            //reload the reactor projects for develop
            MavenSession branchSession = mavenExecutionHelper.getSessionForBranch(branchName, ReleaseUtil.getRootProject(originalProjects), sessionProvider.getSession());

            flow.git().checkout().setName(originalBranchName).call();
            
            return branchSession.getSortedProjects();
        }
        catch (Exception e)
        {
            throw new MavenJGitFlowException("Error checking out branch and loading projects", e);
        }
    }
}
