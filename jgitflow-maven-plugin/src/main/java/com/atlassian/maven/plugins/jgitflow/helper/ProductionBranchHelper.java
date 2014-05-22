package com.atlassian.maven.plugins.jgitflow.helper;

import java.util.List;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.exception.JGitFlowExtensionException;
import com.atlassian.jgitflow.core.util.GitHelper;
import com.atlassian.maven.plugins.jgitflow.BranchType;
import com.atlassian.maven.plugins.jgitflow.VersionType;
import com.atlassian.maven.plugins.jgitflow.exception.MavenJGitFlowException;
import com.atlassian.maven.plugins.jgitflow.provider.JGitFlowProvider;
import com.atlassian.maven.plugins.jgitflow.provider.MavenSessionProvider;
import com.atlassian.maven.plugins.jgitflow.provider.ProjectCacheKey;
import com.atlassian.maven.plugins.jgitflow.provider.ReactorProjectsProvider;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.release.util.ReleaseUtil;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;

@Component(role = ProductionBranchHelper.class)
public class ProductionBranchHelper
{
    @Requirement
    JGitFlowProvider jGitFlowProvider;
    
    @Requirement
    MavenExecutionHelper mavenExecutionHelper;
    
    @Requirement
    ReactorProjectsProvider reactorProjectsProvider;
    
    @Requirement
    MavenSessionProvider sessionProvider;
    
    public List<MavenProject> getProjectsForProductionBranch(BranchType branchType) throws MavenJGitFlowException
    {
        try
        {
            String branchName = getProductionBranchName(branchType);
            MavenSession branchSession = mavenExecutionHelper.getSessionForBranch(branchName, ReleaseUtil.getRootProject(reactorProjectsProvider.getReactorProjects()), sessionProvider.getSession());

            return branchSession.getSortedProjects();

        }
        catch (Exception e)
        {
            throw new MavenJGitFlowException("Error getting project for production branch", e);
        }
           
    }
    
    public String getProductionBranchName(BranchType branchType) throws MavenJGitFlowException
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

                default:
                    throw new MavenJGitFlowException("Unsupported branch type '" + branchType.name() + "' while trying to get the current production branch name");
            }

            List<Ref> productionBranches = GitHelper.listBranchesWithPrefix(flow.git(), branchPrefix, flow.getReporter());

            if (productionBranches.isEmpty())
            {
                throw new MavenJGitFlowException("Could not find current production branch of type " + branchType.name());
            }

            return productionBranches.get(0).getName();
        }
        catch (Exception e)
        {
            throw new MavenJGitFlowException("Error getting name for production branch", e);
        }
    }
}
