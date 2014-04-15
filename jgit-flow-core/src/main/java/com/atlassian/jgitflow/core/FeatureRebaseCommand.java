package com.atlassian.jgitflow.core;

import com.atlassian.jgitflow.core.exception.*;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.util.StringUtils;

import static com.atlassian.jgitflow.core.util.Preconditions.checkState;

/**
 * Performs a rebase of the feature branch
 * <p>
 * Examples (<code>flow</code> is a {@link com.atlassian.jgitflow.core.JGitFlow} instance):
 * <p>
 * Rebase a feature:
 *
 * <pre>
 * flow.featureRebase(&quot;feature&quot;).call();
 * </pre>
 */
public class FeatureRebaseCommand extends AbstractGitFlowCommand<Void>
{
    private static final String SHORT_NAME = "feature-rebase";
    private final String branchName;

    /**
     * Create a new feature rebase command instance.
     * <p></p>
     * This command is usually run as part of a release finish by calling {@link com.atlassian.jgitflow.core.FeatureFinishCommand#setRebase(boolean)}
     * @param name The name of the feature
     * @param git The git instance to use
     * @param gfConfig The GitFlowConfiguration to use
     */
    public FeatureRebaseCommand(String name, Git git, GitFlowConfiguration gfConfig, JGitFlowReporter reporter)
    {
        super(git, gfConfig, reporter);
        checkState(!StringUtils.isEmptyOrNull(name));
        this.branchName = gfConfig.getPrefixValue(JGitFlowConstants.PREFIXES.FEATURE.configKey()) + name;
    }

    @Override
    public FeatureRebaseCommand setAllowUntracked(boolean allow)
    {
        super.setAllowUntracked(allow);
        return this;
    }

    @Override
    public FeatureRebaseCommand setScmMessagePrefix(String scmMessagePrefix)
    {
        super.setScmMessagePrefix(scmMessagePrefix);
        return this;
    }
    
    /**
     * 
     * @return nothing
     * @throws com.atlassian.jgitflow.core.exception.NotInitializedException
     * @throws com.atlassian.jgitflow.core.exception.JGitFlowGitAPIException
     * @throws com.atlassian.jgitflow.core.exception.DirtyWorkingTreeException
     * @throws com.atlassian.jgitflow.core.exception.JGitFlowIOException
     * @throws com.atlassian.jgitflow.core.exception.LocalBranchMissingException
     */
    @Override
    public Void call() throws NotInitializedException, JGitFlowGitAPIException, DirtyWorkingTreeException, JGitFlowIOException, LocalBranchMissingException
    {
        requireGitFlowInitialized();
        requireCleanWorkingTree();
        requireLocalBranchExists(branchName);

        try
        {
            git.checkout().setName(branchName).call();
            git.rebase().call();
        }
        catch (GitAPIException e)
        {
            throw new JGitFlowGitAPIException(e);
        }

        return null;
    }

    @Override
    protected String getCommandName()
    {
        return SHORT_NAME;
    }
}
