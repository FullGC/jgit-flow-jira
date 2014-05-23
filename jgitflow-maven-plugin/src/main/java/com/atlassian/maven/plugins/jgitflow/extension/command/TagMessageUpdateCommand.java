package com.atlassian.maven.plugins.jgitflow.extension.command;

import com.atlassian.jgitflow.core.GitFlowConfiguration;
import com.atlassian.jgitflow.core.JGitFlowReporter;
import com.atlassian.jgitflow.core.command.AbstractBranchMergingCommand;
import com.atlassian.jgitflow.core.command.JGitFlowCommand;
import com.atlassian.jgitflow.core.exception.JGitFlowExtensionException;
import com.atlassian.jgitflow.core.extension.ExtensionCommand;
import com.atlassian.jgitflow.core.extension.ExtensionFailStrategy;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.helper.BranchHelper;
import com.atlassian.maven.plugins.jgitflow.provider.ContextProvider;

import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.release.util.ReleaseUtil;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.jgit.api.Git;

@Component(role = TagMessageUpdateCommand.class)
public class TagMessageUpdateCommand implements ExtensionCommand
{
    @Requirement
    private BranchHelper branchHelper;

    @Requirement
    private ContextProvider contextProvider;
    
    @Override
    public void execute(GitFlowConfiguration configuration, Git git, JGitFlowCommand gitFlowCommand, JGitFlowReporter reporter) throws JGitFlowExtensionException
    {
        try
        {
            if(AbstractBranchMergingCommand.class.isAssignableFrom(gitFlowCommand.getClass()))
            {
                ReleaseContext ctx = contextProvider.getContext();
                
                AbstractBranchMergingCommand mergingCommand = (AbstractBranchMergingCommand) gitFlowCommand;
                
                MavenProject rootProject = ReleaseUtil.getRootProject(branchHelper.getProjectsForCurrentBranch());
                
                mergingCommand.setMessage(ReleaseUtil.interpolate(ctx.getTagMessage(), rootProject.getModel()));
            }
        }
        catch (Exception e)
        {
            throw new JGitFlowExtensionException("Error setting tag message", e);
        }
    }

    @Override
    public ExtensionFailStrategy failStrategy()
    {
        return null;
    }
}
