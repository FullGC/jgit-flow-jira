package com.atlassian.maven.plugins.jgitflow.helper;

import java.io.IOException;
import java.util.List;

import com.atlassian.jgitflow.core.BranchType;
import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.exception.JGitFlowException;
import com.atlassian.jgitflow.core.util.GitHelper;
import com.atlassian.maven.plugins.jgitflow.exception.MavenJGitFlowException;
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
import org.eclipse.jgit.lib.Ref;

@Component(role = BranchHelper.class)
public class BranchHelper
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

        return new SessionAndProjects(branchSession, branchSession.getSortedProjects());
    }

    public String getUnprefixedCurrentBranchName() throws JGitFlowException, IOException
    {
        JGitFlow flow = jGitFlowProvider.gitFlow();
        String branchName = flow.git().getRepository().getBranch();
        String branchPrefix = flow.getPrefixForBranch(branchName);

        return NamingUtil.unprefixedBranchName(branchPrefix, branchName);
    }

    public String getCurrentBranchName() throws MavenJGitFlowException
    {
        try
        {
            JGitFlow flow = jGitFlowProvider.gitFlow();
            return flow.git().getRepository().getBranch();
        }
        catch (Exception e)
        {
            throw new MavenJGitFlowException("Error looking up current branch name", e);
        }
    }

    public String getCurrentReleaseBranchNameOrBlank() throws MavenJGitFlowException
    {
        String branchName = "";
        try
        {
            JGitFlow flow = jGitFlowProvider.gitFlow();

            List<Ref> branches = GitHelper.listBranchesWithPrefix(flow.git(), flow.getReleaseBranchPrefix());

            if (!branches.isEmpty())
            {
                branchName = branches.get(0).getName();
            }
        }
        catch (Exception e)
        {
            throw new MavenJGitFlowException("Error looking up release branch name", e);
        }

        return branchName;
    }

    public boolean releaseBranchExists() throws MavenJGitFlowException
    {
        boolean exists = false;
        try
        {
            JGitFlow flow = jGitFlowProvider.gitFlow();

            List<Ref> branches = GitHelper.listBranchesWithPrefix(flow.git(), flow.getReleaseBranchPrefix());

            if (!branches.isEmpty())
            {
                exists = true;
            }
        }
        catch (Exception e)
        {
            throw new MavenJGitFlowException("Error looking up release branch", e);
        }

        return exists;
    }

    public BranchType getCurrentBranchType() throws JGitFlowException, IOException
    {
        JGitFlow flow = jGitFlowProvider.gitFlow();
        String branchName = flow.git().getRepository().getBranch();

        return flow.getTypeForBranch(branchName);
    }

    public List<MavenProject> getProjectsForTopicBranch(BranchType branchType) throws MavenJGitFlowException
    {
        try
        {
            String branchName = getTopicBranchName(branchType);
            MavenSession branchSession = mavenExecutionHelper.getSessionForBranch(branchName, ReleaseUtil.getRootProject(projectsProvider.getReactorProjects()), sessionProvider.getSession());

            return branchSession.getSortedProjects();

        }
        catch (Exception e)
        {
            throw new MavenJGitFlowException("Error getting project for production branch", e);
        }

    }

    public String getTopicBranchName(BranchType branchType) throws MavenJGitFlowException
    {
        String branchPrefix = "";
        try
        {
            JGitFlow flow = jGitFlowProvider.gitFlow();
            switch (branchType)
            {
                case RELEASE:
                    branchPrefix = flow.getReleaseBranchPrefix();
                    break;

                case HOTFIX:
                    branchPrefix = flow.getHotfixBranchPrefix();
                    break;

                case FEATURE:
                    branchPrefix = flow.getFeatureBranchPrefix();
                    break;

                default:
                    throw new MavenJGitFlowException("Unsupported branch type '" + branchType.name() + "' while trying to get the current production branch name");
            }

            List<Ref> topicBranches = GitHelper.listBranchesWithPrefix(flow.git(), branchPrefix);

            if (topicBranches.isEmpty())
            {
                throw new MavenJGitFlowException("Could not find current production branch of type " + branchType.name());
            }

            return topicBranches.get(0).getName();
        }
        catch (Exception e)
        {
            throw new MavenJGitFlowException("Error getting name for production branch", e);
        }
    }

    private String stripSlash(String prefix)
    {
        if (Strings.isNullOrEmpty(prefix))
        {
            return prefix;
        }

        if (prefix.endsWith("/"))
        {
            return prefix.substring(0, prefix.length() - 1);
        }

        return prefix;
    }
}
