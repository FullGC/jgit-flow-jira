package com.atlassian.jgitflow.core;

import java.util.concurrent.Callable;

import com.atlassian.jgitflow.core.exception.BranchOutOfDateException;
import com.atlassian.jgitflow.core.exception.JGitFlowExtensionException;
import com.atlassian.jgitflow.core.exception.JGitFlowGitAPIException;
import com.atlassian.jgitflow.core.exception.JGitFlowIOException;
import com.atlassian.jgitflow.core.extension.ExtensionCommand;
import com.atlassian.jgitflow.core.extension.ExtensionFailStrategy;
import com.atlassian.jgitflow.core.extension.JGitFlowExtension;
import com.atlassian.jgitflow.core.util.GitHelper;
import com.atlassian.jgitflow.core.util.RequirementHelper;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.RefSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.atlassian.jgitflow.core.util.Preconditions.checkNotNull;

/**
 * The base class for all JGitFlow commands.
 * <p>
 * Most commands should extend this class as it provides common helper methods
 * and methods to ensure valid state.
 * </p>
 *
 * @param <T> The return type of the call() method
 */
public abstract class AbstractGitFlowCommand<C, T> implements Callable<T>
{
    private static final Logger log = LoggerFactory.getLogger(AbstractGitFlowCommand.class);
    protected final Git git;
    protected final GitFlowConfiguration gfConfig;
    protected final JGitFlowReporter reporter;
    protected final RequirementHelper requirementHelper;
    private boolean allowUntracked;
    private String scmMessagePrefix;
    private String scmMessageSuffix;
    private boolean fetch;
    private boolean push;
    private final String branchName;

    protected AbstractGitFlowCommand(String branchName, Git git, GitFlowConfiguration gfConfig, JGitFlowReporter reporter)
    {
        checkNotNull(branchName);
        checkNotNull(git);
        checkNotNull(gfConfig);
        checkNotNull(reporter);

        this.requirementHelper = new RequirementHelper(git, gfConfig, reporter, getCommandName());

        this.git = git;
        this.gfConfig = gfConfig;
        this.reporter = reporter;
        this.allowUntracked = false;
        this.scmMessagePrefix = "";
        this.scmMessageSuffix = "";
        this.fetch = false;
        this.push = false;
        this.branchName = branchName;
    }

    protected void doFetchIfNeeded(JGitFlowExtension fetchingExtension) throws GitAPIException, JGitFlowGitAPIException, JGitFlowExtensionException
    {
        if (fetch)
        {
            runExtensionCommands(fetchingExtension.beforeFetch());

            git.fetch().setRemote(Constants.DEFAULT_REMOTE_NAME).call();

            runExtensionCommands(fetchingExtension.afterFetch());
        }
    }

    protected void doPushIfNeeded(JGitFlowExtension pushExtension, boolean includeTags, String... branchesToPush) throws GitAPIException, JGitFlowGitAPIException, JGitFlowExtensionException
    {
        if (push)
        {
            reporter.infoText(getCommandName(), "pushing changes to origin...");

            for (String branchToPush : branchesToPush)
            {
                if (GitHelper.remoteBranchExists(git, branchToPush, reporter))
                {
                    reporter.infoText(getCommandName(), "pushing '" + branchToPush + "'");
                    RefSpec branchSpec = new RefSpec(branchToPush);
                    git.push().setRemote(Constants.DEFAULT_REMOTE_NAME).setRefSpecs(branchSpec).call();
                }
            }

            if (includeTags)
            {
                reporter.infoText(getCommandName(), "pushing tags");
                git.push().setRemote(Constants.DEFAULT_REMOTE_NAME).setPushTags().call();
            }

            git.fetch().setRemote(Constants.DEFAULT_REMOTE_NAME).call();

            runExtensionCommands(pushExtension.afterPush());
        }
    }

    protected String runBeforeAndGetPrefixedBranchName(Iterable<ExtensionCommand> before, JGitFlowConstants.PREFIXES prefix) throws JGitFlowExtensionException
    {
        reporter.commandCall(getCommandName());
        runExtensionCommands(before);
        return gfConfig.getPrefixValue(prefix.configKey()) + branchName;
    }

    protected void ensureLocalBranchesNotBehindRemotes(String... branchesToTest) throws JGitFlowGitAPIException, BranchOutOfDateException, JGitFlowIOException
    {
        for (String branchToTest : branchesToTest)
        {
            if (GitHelper.remoteBranchExists(git, branchToTest, reporter))
            {
                enforcer().requireLocalBranchNotBehindRemote(branchToTest);
            }
        }
    }

    public C setAllowUntracked(boolean allow)
    {
        this.allowUntracked = allow;
        return (C) this;
    }

    public boolean isAllowUntracked()
    {
        return allowUntracked;
    }

    public String getScmMessagePrefix()
    {
        return scmMessagePrefix;
    }

    public C setScmMessagePrefix(String scmMessagePrefix)
    {
        this.scmMessagePrefix = scmMessagePrefix;
        return (C) this;
    }

    public String getScmMessageSuffix()
    {
        return scmMessageSuffix;
    }

    public C setScmMessageSuffix(String scmMessageSuffix)
    {
        this.scmMessageSuffix = scmMessageSuffix;
        return (C) this;
    }

    /**
     * Set whether to perform a git fetch of the remote branches before doing the merge
     *
     * @param fetch <code>true</code> to do the fetch, <code>false</code>(default) otherwise
     * @return {@code this}
     */
    public C setFetch(boolean fetch)
    {
        this.fetch = fetch;
        return (C) this;
    }

    public boolean isFetch()
    {
        return fetch;
    }

    /**
     * Set whether to push the changes to the remote repository
     *
     * @param push <code>true</code> to do the push, <code>false</code>(default) otherwise
     * @return {@code this}
     */
    public C setPush(boolean push)
    {
        this.push = push;
        return (C) this;
    }

    public boolean isPush()
    {
        return push;
    }

    public String getBranchName()
    {
        return branchName;
    }

    protected abstract String getCommandName();

    protected void runExtensionCommands(Iterable<ExtensionCommand> commands) throws JGitFlowExtensionException
    {
        for (final ExtensionCommand command : commands)
        {
            try
            {
                command.execute(gfConfig, git, reporter);
            }
            catch (JGitFlowExtensionException e)
            {
                if (ExtensionFailStrategy.ERROR.equals(command.failStrategy()))
                {
                    throw e;
                }
                else
                {
                    log.warn("Error running JGitFlow Extension", e);
                }
            }
        }
    }

    protected RequirementHelper enforcer()
    {
        return requirementHelper;
    }
}
