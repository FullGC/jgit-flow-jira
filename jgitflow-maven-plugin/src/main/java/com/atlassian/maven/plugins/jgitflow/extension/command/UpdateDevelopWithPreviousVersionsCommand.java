package com.atlassian.maven.plugins.jgitflow.extension.command;

import java.util.List;

import com.atlassian.jgitflow.core.GitFlowConfiguration;
import com.atlassian.jgitflow.core.JGitFlowReporter;
import com.atlassian.jgitflow.core.command.JGitFlowCommand;
import com.atlassian.jgitflow.core.exception.JGitFlowExtensionException;
import com.atlassian.jgitflow.core.extension.ExtensionCommand;
import com.atlassian.jgitflow.core.extension.ExtensionFailStrategy;
import com.atlassian.maven.plugins.jgitflow.helper.CurrentBranchHelper;
import com.atlassian.maven.plugins.jgitflow.helper.PomUpdater;
import com.atlassian.maven.plugins.jgitflow.helper.ProjectHelper;
import com.atlassian.maven.plugins.jgitflow.provider.VersionCacheProvider;

import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.jgit.api.Git;

@Component(role = UpdateDevelopWithPreviousVersionsCommand.class)
public class UpdateDevelopWithPreviousVersionsCommand implements ExtensionCommand
{
    @Requirement
    private VersionCacheProvider versionCacheProvider;

    @Requirement
    private PomUpdater pomUpdater;
    
    @Requirement
    private CurrentBranchHelper currentBranchHelper;
    
    @Requirement
    private ProjectHelper projectHelper;
    

    @Override
    public void execute(GitFlowConfiguration configuration, Git git, JGitFlowCommand gitFlowCommand, JGitFlowReporter reporter) throws JGitFlowExtensionException
    {
        try
        {
            List<MavenProject> developProjects = currentBranchHelper.getProjectsForCurrentBranch();
            pomUpdater.copyPomVersionsFromMap(developProjects, versionCacheProvider.getCachedVersions());
            projectHelper.commitAllPoms(git,developProjects,"Updating develop poms back to pre-hotfix merge state");
        }
        catch (Exception e)
        {
            throw new JGitFlowExtensionException("Error updating develop poms to previously cached versions", e);
        }
    }

    @Override
    public ExtensionFailStrategy failStrategy()
    {
        return null;
    }
}
