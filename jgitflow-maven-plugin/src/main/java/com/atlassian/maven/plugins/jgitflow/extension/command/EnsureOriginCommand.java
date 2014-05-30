package com.atlassian.maven.plugins.jgitflow.extension.command;

import com.atlassian.jgitflow.core.GitFlowConfiguration;
import com.atlassian.jgitflow.core.JGitFlowReporter;
import com.atlassian.jgitflow.core.command.JGitFlowCommand;
import com.atlassian.jgitflow.core.exception.JGitFlowExtensionException;
import com.atlassian.jgitflow.core.extension.ExtensionCommand;
import com.atlassian.jgitflow.core.extension.ExtensionFailStrategy;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.helper.JGitFlowSetupHelper;
import com.atlassian.maven.plugins.jgitflow.provider.ContextProvider;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.jgit.api.Git;

@Component(role = EnsureOriginCommand.class)
public class EnsureOriginCommand implements ExtensionCommand
{
    @Requirement
    private ContextProvider contextProvider;
    
    @Requirement
    private JGitFlowSetupHelper setupHelper;
    
    @Override
    public void execute(GitFlowConfiguration configuration, Git git, JGitFlowCommand gitFlowCommand, JGitFlowReporter reporter) throws JGitFlowExtensionException
    {
        ReleaseContext ctx = contextProvider.getContext();
        
        if (ctx.isRemoteAllowed())
        {
            try
            {
                setupHelper.ensureOrigin();
            }
            catch (Exception e)
            {
                throw new JGitFlowExtensionException("Error setting origin in configuration", e);
            }
        }
    }

    @Override
    public ExtensionFailStrategy failStrategy()
    {
        return ExtensionFailStrategy.ERROR;
    }
}
