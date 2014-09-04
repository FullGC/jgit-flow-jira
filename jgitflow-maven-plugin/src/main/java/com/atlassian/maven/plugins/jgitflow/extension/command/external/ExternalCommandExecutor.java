package com.atlassian.maven.plugins.jgitflow.extension.command.external;

import com.atlassian.jgitflow.core.GitFlowConfiguration;
import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.JGitFlowInfo;
import com.atlassian.jgitflow.core.command.JGitFlowCommand;
import com.atlassian.jgitflow.core.exception.JGitFlowExtensionException;
import com.atlassian.jgitflow.core.extension.ExtensionCommand;
import com.atlassian.jgitflow.core.extension.ExtensionFailStrategy;
import com.atlassian.maven.jgitflow.api.MavenJGitFlowExtension;
import com.atlassian.maven.plugins.jgitflow.extension.ExternalInitializingExtension;
import com.atlassian.maven.plugins.jgitflow.provider.JGitFlowProvider;

import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.jgit.api.Git;

public abstract class ExternalCommandExecutor implements ExtensionCommand, ExternalInitializingExtension, ExternalCommand
{
    @Requirement
    private JGitFlowProvider jGitFlowProvider;

    private MavenJGitFlowExtension externalExtension;

    @Override
    public void execute(GitFlowConfiguration configuration, Git git, JGitFlowCommand gitFlowCommand) throws JGitFlowExtensionException
    {
        if (null == externalExtension)
        {
            return;
        }

        try
        {
            JGitFlow flow = jGitFlowProvider.gitFlow();

            execute(externalExtension, getNewVersion(), getOldVersion(), new JGitFlowInfo(flow.git(), configuration));
        }
        catch (Exception e)
        {
            throw new JGitFlowExtensionException("Error running external extension for branch change", e);
        }
    }

    @Override
    public void init(MavenJGitFlowExtension externalExtension)
    {
        this.externalExtension = externalExtension;
    }


    @Override
    public ExtensionFailStrategy failStrategy()
    {
        return ExtensionFailStrategy.ERROR;
    }
}
