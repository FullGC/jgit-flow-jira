package com.atlassian.maven.plugins.jgitflow.helper;

import java.io.IOException;
import java.util.List;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.exception.JGitFlowException;
import com.atlassian.maven.plugins.jgitflow.BranchType;
import com.atlassian.maven.plugins.jgitflow.exception.ReactorReloadException;
import com.atlassian.maven.plugins.jgitflow.provider.JGitFlowProvider;
import com.atlassian.maven.plugins.jgitflow.provider.MavenSessionProvider;
import com.atlassian.maven.plugins.jgitflow.provider.ReactorProjectsProvider;
import com.atlassian.maven.plugins.jgitflow.util.NamingUtil;

import com.google.common.base.Strings;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.release.util.ReleaseUtil;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.jgit.api.errors.GitAPIException;

@Component(role = CurrentBranchHelper.class)
public class CurrentBranchHelper
{
    @Requirement
    private MavenSessionProvider sessionProvider;
    
    @Requirement
    private JGitFlowProvider jGitFlowProvider;

    @Requirement
    private MavenExecutionHelper mavenExecutionHelper;

    @Requirement
    private ReactorProjectsProvider projectsProvider;
    
    
    public List<MavenProject> getProjectsForCurrentBranch() throws JGitFlowException, IOException, GitAPIException, ReactorReloadException
    {
        JGitFlow flow = jGitFlowProvider.gitFlow();
        
        MavenSession session = sessionProvider.getSession();

        String branchName = flow.git().getRepository().getBranch();
        String branchPrefix = flow.getPrefixForBranch(branchName);

        String unprefixedBranchName = NamingUtil.unprefixedBranchName(branchPrefix, branchName);

        //reload the reactor projects for release
        MavenSession branchSession = mavenExecutionHelper.getSessionForBranch(branchName, ReleaseUtil.getRootProject(projectsProvider.getReactorProjects()), session);
        
        return branchSession.getSortedProjects();
    }

    public SessionAndProjects getSessionAndProjectsForCurrentBranch() throws JGitFlowException, IOException, GitAPIException, ReactorReloadException
    {
        JGitFlow flow = jGitFlowProvider.gitFlow();

        MavenSession session = sessionProvider.getSession();

        String branchName = flow.git().getRepository().getBranch();
        String branchPrefix = flow.getPrefixForBranch(branchName);

        String unprefixedBranchName = NamingUtil.unprefixedBranchName(branchPrefix, branchName);

        //reload the reactor projects for release
        MavenSession branchSession = mavenExecutionHelper.getSessionForBranch(branchName, ReleaseUtil.getRootProject(projectsProvider.getReactorProjects()), session);

        return new SessionAndProjects(branchSession,branchSession.getSortedProjects());
    }
    
    public String getUnprefixedBranchName() throws JGitFlowException, IOException
    {
        JGitFlow flow = jGitFlowProvider.gitFlow();
        String branchName = flow.git().getRepository().getBranch();
        String branchPrefix = flow.getPrefixForBranch(branchName);

        return NamingUtil.unprefixedBranchName(branchPrefix, branchName);
    }

    public String getBranchName() throws JGitFlowException, IOException
    {
        JGitFlow flow = jGitFlowProvider.gitFlow();
        return flow.git().getRepository().getBranch();
    }
    
    public BranchType getBranchType() throws JGitFlowException, IOException
    {
        JGitFlow flow = jGitFlowProvider.gitFlow();
        String branchName = flow.git().getRepository().getBranch();
        
        if(flow.getDevelopBranchName().equals(branchName))
        {
            return BranchType.DEVELOP;
        }

        if(flow.getMasterBranchName().equals(branchName))
        {
            return BranchType.MASTER;
        }

        String branchPrefix = stripSlash(flow.getPrefixForBranch(branchName));
        
        try
        {
            return BranchType.valueOf(branchPrefix.toUpperCase());
        }
        catch (IllegalArgumentException e)
        {
            return BranchType.UNKNOWN;
        }
    }
    
    private String stripSlash(String prefix)
    {
        if(Strings.isNullOrEmpty(prefix))
        {
            return prefix;    
        }
        
        if(prefix.endsWith("/"))
        {
            return prefix.substring(0,prefix.length() - 1);
        }
        
        return prefix;
    }
}
