package com.atlassian.maven.plugins.jgitflow.extension.command;

import com.atlassian.jgitflow.core.GitFlowConfiguration;
import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.JGitFlowReporter;
import com.atlassian.jgitflow.core.command.JGitFlowCommand;
import com.atlassian.jgitflow.core.exception.JGitFlowExtensionException;
import com.atlassian.jgitflow.core.extension.ExtensionCommand;
import com.atlassian.jgitflow.core.extension.ExtensionFailStrategy;
import com.atlassian.jgitflow.core.util.GitHelper;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.provider.ContextProvider;
import com.atlassian.maven.plugins.jgitflow.provider.JGitFlowProvider;

import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.jgit.api.Git;

public abstract class BranchPullingCommand implements ExtensionCommand
{
    @Requirement
    protected JGitFlowProvider jGitFlowProvider;

    @Requirement
    protected ContextProvider contextProvider;
    
    private String branchName;

    @Override
    public void execute(GitFlowConfiguration configuration, Git git, JGitFlowCommand gitFlowCommand, JGitFlowReporter reporter) throws JGitFlowExtensionException
    {
        try
        {
            JGitFlow flow = jGitFlowProvider.gitFlow();
            ReleaseContext ctx = contextProvider.getContext();
            
            if(!ctx.isRemoteAllowed())
            {
                return;    
            }
            
            if (GitHelper.remoteBranchExists(flow.git(), branchName, flow.getReporter()))
            {
                    reporter.debugText("finishRelease", "pulling '" + branchName + "' before remote behind check");
                    reporter.flush();

                    String originalBranch = flow.git().getRepository().getBranch();
                
                    flow.git().checkout().setName(branchName).call();
                    flow.git().pull().call();

                    flow.git().checkout().setName(originalBranch).call();
            }
        }
        catch (Exception e)
        {
            throw new JGitFlowExtensionException("Error pulling branch '" + branchName + "'",e);
        }
    }

    @Override
    public ExtensionFailStrategy failStrategy()
    {
        return ExtensionFailStrategy.ERROR;
    }

    public void setBranchName(String branchName)
    {
        this.branchName = branchName;
    }

    public String getBranchName()
    {
        return branchName;
    }
}
