package com.atlassian.jgitflow.core;

import java.io.IOException;

import com.atlassian.jgitflow.core.exception.*;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.util.StringUtils;

import static com.atlassian.jgitflow.core.util.Preconditions.checkState;

/**
 * Publishes feature branch to the remote repository
 * <p>
 * Examples (<code>flow</code> is a {@link com.atlassian.jgitflow.core.JGitFlow} instance):
 * <p>
 * Publish a feature:
 *
 * <pre>
 * flow.featurePublish(&quot;feature&quot;).call();
 * </pre>
 */
public class FeaturePublishCommand extends AbstractGitFlowCommand<Void>
{
    private static final String SHORT_NAME = "feature-publish";
    private final String branchName;

    /**
     * Create a new feature publish command instance.
     * <p></p>
     * An instance of this class is usually obtained by calling {@link com.atlassian.jgitflow.core.JGitFlow#featurePublish(String)}
     * @param name The name of the feature
     * @param git The git instance to use
     * @param gfConfig The GitFlowConfiguration to use
     * @param reporter
     */
    public FeaturePublishCommand(String name, Git git, GitFlowConfiguration gfConfig, JGitFlowReporter reporter)
    {
        super(git, gfConfig, reporter);
        checkState(!StringUtils.isEmptyOrNull(name));
        this.branchName = name;
    }

    @Override
    public FeaturePublishCommand setAllowUntracked(boolean allow)
    {
        super.setAllowUntracked(allow);
        return this;
    }

    @Override
    public FeaturePublishCommand setScmMessagePrefix(String scmMessagePrefix)
    {
        super.setScmMessagePrefix(scmMessagePrefix);
        return this;
    }

    @Override
    public FeaturePublishCommand setScmMessageSuffix(String scmMessageSuffix)
    {
        super.setScmMessageSuffix(scmMessageSuffix);
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
     * @throws com.atlassian.jgitflow.core.exception.RemoteBranchExistsException
     */
    @Override
    public Void call() throws NotInitializedException, JGitFlowGitAPIException, DirtyWorkingTreeException, JGitFlowIOException, LocalBranchMissingException, RemoteBranchExistsException
    {
        requireGitFlowInitialized();
        String prefixedBranchName = gfConfig.getPrefixValue(JGitFlowConstants.PREFIXES.FEATURE.configKey()) + branchName;

        requireCleanWorkingTree();
        requireLocalBranchExists(prefixedBranchName);

        try
        {
            git.fetch().setRemote("origin").call();
            requireRemoteBranchAbsent(prefixedBranchName);

            //create remote feature branch
            RefSpec branchSpec = new RefSpec(prefixedBranchName + ":" + Constants.R_HEADS + prefixedBranchName);
            git.push().setRemote("origin").setRefSpecs(branchSpec).call();
            git.fetch().setRemote("origin").call();

            //setup tracking
            StoredConfig config = git.getRepository().getConfig();
            config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, prefixedBranchName, ConfigConstants.CONFIG_KEY_REMOTE, "origin");
            config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, prefixedBranchName, ConfigConstants.CONFIG_KEY_MERGE, Constants.R_HEADS + prefixedBranchName);
            config.save();

            //checkout the branch
            git.checkout().setName(prefixedBranchName).call();

        }
        catch (IOException e)
        {
            throw new JGitFlowIOException(e);
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
