package com.atlassian.maven.plugins.jgitflow.extension.command;

import java.util.List;
import java.util.Map;

import com.atlassian.jgitflow.core.GitFlowConfiguration;
import com.atlassian.jgitflow.core.JGitFlowReporter;
import com.atlassian.jgitflow.core.command.JGitFlowCommand;
import com.atlassian.jgitflow.core.exception.JGitFlowExtensionException;
import com.atlassian.jgitflow.core.extension.ExtensionCommand;
import com.atlassian.jgitflow.core.extension.ExtensionFailStrategy;
import com.atlassian.maven.plugins.jgitflow.BranchType;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.MavenJGitFlowException;
import com.atlassian.maven.plugins.jgitflow.helper.*;
import com.atlassian.maven.plugins.jgitflow.provider.BranchLabelProvider;
import com.atlassian.maven.plugins.jgitflow.provider.ContextProvider;
import com.atlassian.maven.plugins.jgitflow.provider.VersionCacheProvider;
import com.atlassian.maven.plugins.jgitflow.provider.VersionProvider;

import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.jgit.api.Git;

@Component(role = UpdateDevelopWithHotfixVersionsCommand.class)
public class UpdateDevelopWithHotfixVersionsCommand implements ExtensionCommand
{
    @Requirement
    private VersionCacheProvider versionCacheProvider;
    
    @Requirement
    private PomUpdater pomUpdater;
   
    @Requirement
    private ProductionBranchHelper productionBranchHelper;

    @Requirement
    private CurrentBranchHelper currentBranchHelper;
    
    @Requirement
    private VersionProvider versionProvider;
    
    @Requirement
    private ProjectHelper projectHelper;

    @Requirement
    private ContextProvider contextProvider;
    
    @Override
    public void execute(GitFlowConfiguration configuration, Git git, JGitFlowCommand gitFlowCommand, JGitFlowReporter reporter) throws JGitFlowExtensionException
    {
        try
        {
            ReleaseContext ctx = contextProvider.getContext();
            
            versionCacheProvider.cacheCurrentBranchVersions();
            
            List<MavenProject> developProjects = currentBranchHelper.getProjectsForCurrentBranch();
            
            List<MavenProject> hotfixProjects = productionBranchHelper.getProjectsForProductionBranch(BranchType.HOTFIX);
            
            pomUpdater.copyPomVersionsFromProject(hotfixProjects,developProjects);
            projectHelper.commitAllPoms(git,developProjects,ctx.getScmCommentPrefix() + "Updating develop poms to hotfix version to avoid merge conflicts" + ctx.getScmCommentSuffix());
        }
        catch (Exception e)
        {
            throw new JGitFlowExtensionException("Error updating develop poms to hotfix version", e);
        }
        
    }

    @Override
    public ExtensionFailStrategy failStrategy()
    {
        return ExtensionFailStrategy.ERROR;
    }
}
