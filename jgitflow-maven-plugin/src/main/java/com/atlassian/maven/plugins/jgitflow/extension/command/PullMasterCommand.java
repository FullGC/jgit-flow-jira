package com.atlassian.maven.plugins.jgitflow.extension.command;

import com.atlassian.jgitflow.core.GitFlowConfiguration;
import com.atlassian.jgitflow.core.JGitFlowReporter;
import com.atlassian.jgitflow.core.command.JGitFlowCommand;
import com.atlassian.jgitflow.core.exception.JGitFlowExtensionException;

import org.codehaus.plexus.component.annotations.Component;
import org.eclipse.jgit.api.Git;

@Component(role = PullMasterCommand.class)
public class PullMasterCommand extends BranchPullingCommand
{
    @Override
    public void execute(GitFlowConfiguration configuration, Git git, JGitFlowCommand gitFlowCommand, JGitFlowReporter reporter) throws JGitFlowExtensionException
    {
        try
        {
            if(contextProvider.getContext().isPullMaster())
            {
                setBranchName(jGitFlowProvider.gitFlow().getMasterBranchName());
                super.execute(configuration, git, gitFlowCommand, reporter);
            }
        }
        catch (Exception e)
        {
            throw new JGitFlowExtensionException("Error pulling branch '" + getBranchName() + "'",e);
        }
    }
}
