package com.atlassian.maven.plugins.jgitflow.extension.command;

import java.util.List;

import com.atlassian.jgitflow.core.GitFlowConfiguration;
import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.JGitFlowReporter;
import com.atlassian.jgitflow.core.command.JGitFlowCommand;
import com.atlassian.jgitflow.core.exception.JGitFlowExtensionException;
import com.atlassian.jgitflow.core.extension.ExtensionCommand;
import com.atlassian.jgitflow.core.extension.ExtensionFailStrategy;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.helper.*;
import com.atlassian.maven.plugins.jgitflow.manager.tasks.GetProjectsForBranch;
import com.atlassian.maven.plugins.jgitflow.provider.ContextProvider;
import com.atlassian.maven.plugins.jgitflow.provider.JGitFlowProvider;
import com.atlassian.maven.plugins.jgitflow.provider.ReactorProjectsProvider;
import com.atlassian.maven.plugins.jgitflow.provider.VersionCacheProvider;

import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.jgit.api.Git;

@Component(role = UpdateDevelopPomsWithMasterVersion.class)
public class UpdateDevelopPomsWithMasterVersion implements ExtensionCommand
{
    @Requirement
    private JGitFlowProvider jGitFlowProvider;
    
    @Requirement
    private PomUpdater pomUpdater;

    @Requirement
    private CurrentBranchHelper currentBranchHelper;

    @Requirement
    private GetProjectsForBranch getProjectsForBranch;

    @Requirement
    private ReactorProjectsProvider reactorProjectsProvider;

    @Requirement
    private ProjectHelper projectHelper;

    @Requirement
    private ContextProvider contextProvider;
    
    @Requirement
    private VersionCacheProvider versionCacheProvider;
    
    @Override
    public void execute(GitFlowConfiguration configuration, Git git, JGitFlowCommand gitFlowCommand, JGitFlowReporter reporter) throws JGitFlowExtensionException
    {
        try
        {
            String originalBranchName = currentBranchHelper.getBranchName();

            ReleaseContext ctx = contextProvider.getContext();
            
            JGitFlow flow = jGitFlowProvider.gitFlow();

            List<MavenProject> developProjects = getProjectsForBranch.run(flow.getDevelopBranchName(),reactorProjectsProvider.getReactorProjects());
            List<MavenProject> masterProjects = getProjectsForBranch.run(flow.getMasterBranchName(),reactorProjectsProvider.getReactorProjects());
            
            //cache the current develop versions
            flow.git().checkout().setName(flow.getDevelopBranchName()).call();
            versionCacheProvider.cacheCurrentBranchVersions();
            
            pomUpdater.copyPomVersionsFromProject(masterProjects,developProjects);

            projectHelper.commitAllPoms(flow.git(), developProjects, ctx.getScmCommentPrefix() + "updating develop poms to master versions to avoid merge conflicts" + ctx.getScmCommentSuffix());

            flow.git().checkout().setName(originalBranchName).call();
            
        }
        catch (Exception e)
        {
            throw new JGitFlowExtensionException("Error updating release poms to develop version", e);
        }
    }

    @Override
    public ExtensionFailStrategy failStrategy()
    {
        return null;
    }
}
