package com.atlassian.jgitflow.core;

import java.io.IOException;

import com.atlassian.jgitflow.core.exception.*;
import com.atlassian.jgitflow.core.util.GitHelper;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.util.StringUtils;

import static com.atlassian.jgitflow.core.util.Preconditions.checkState;

/**
 * Start a feature.
 * <p>
 * This will create a new branch using the feature prefix and feature name from the tip of develop
 * </p>
 * <p>
 * Examples (<code>flow</code> is a {@link com.atlassian.jgitflow.core.JGitFlow} instance):
 * <p>
 * Start a feature:
 *
 * <pre>
 * flow.featureStart(&quot;feature&quot;).call();
 * </pre>
 * <p>
 * Perform a fetch of develop before branching
 *
 * <pre>
 * flow.featureStart(&quot;feature&quot;).setFetchDevelop(true).call();
 * </pre>
 */
public class FeatureStartCommand extends AbstractGitFlowCommand<FeatureStartCommand,Ref>
{
    private static final String SHORT_NAME = "feature-start";
    
    private final String branchName;
    private boolean fetchDevelop;
    private boolean push;
    private RevCommit startCommit;
    private String startCommitString;

    /**
     * Create a new feature start command instance.
     * <p></p>
     * An instance of this class is usually obtained by calling {@link com.atlassian.jgitflow.core.JGitFlow#featureStart(String)}
     * @param branchName The name of the feature
     * @param git The git instance to use
     * @param gfConfig The GitFlowConfiguration to use
     * @param reporter
     */
    public FeatureStartCommand(String branchName, Git git, GitFlowConfiguration gfConfig, JGitFlowReporter reporter) 
    {
        super(git,gfConfig, reporter);

        checkState(!StringUtils.isEmptyOrNull(branchName));
        this.branchName = branchName;
        this.fetchDevelop = false;
        this.push = false;
    }

    /**
     * 
     * @return A reference to the new feature branch
     * @throws com.atlassian.jgitflow.core.exception.NotInitializedException
     * @throws com.atlassian.jgitflow.core.exception.JGitFlowGitAPIException
     * @throws com.atlassian.jgitflow.core.exception.LocalBranchExistsException
     * @throws com.atlassian.jgitflow.core.exception.BranchOutOfDateException
     * @throws com.atlassian.jgitflow.core.exception.JGitFlowIOException
     */
    @Override
    public Ref call() throws NotInitializedException, JGitFlowGitAPIException, LocalBranchExistsException, BranchOutOfDateException, JGitFlowIOException, LocalBranchMissingException, RemoteBranchExistsException
    {
        reporter.commandCall(SHORT_NAME);
        
        //TODO: we should test for remote feature and pull it if it exists
        final String prefixedBranchName = gfConfig.getPrefixValue(JGitFlowConstants.PREFIXES.FEATURE.configKey()) + branchName;
        requireGitFlowInitialized();
        requireLocalBranchAbsent(prefixedBranchName);
        
        if(fetchDevelop)
        {
            RefSpec spec = new RefSpec("+" + Constants.R_HEADS + gfConfig.getDevelop() + ":" + Constants.R_REMOTES + "origin/" + gfConfig.getDevelop());
            try
            {
                git.fetch().setRefSpecs(spec).call();
            }
            catch (GitAPIException e)
            {
                throw new JGitFlowGitAPIException(e);
            }
        }
        try
        {
            //ensure local develop isn't behind remote develop
            if(GitHelper.remoteBranchExists(git,gfConfig.getDevelop(), reporter))
            {
                requireLocalBranchNotBehindRemote(gfConfig.getDevelop());
            }

            RevCommit startPoint = null;

            if(null != startCommit)
            {
                startPoint = startCommit;
            }
            else if(!StringUtils.isEmptyOrNull(startCommitString))
            {
                startPoint = GitHelper.getCommitForString(git,startCommitString);
            }
            else
            {
                startPoint = GitHelper.getLatestCommit(git, gfConfig.getDevelop());
            }

            requireCommitOnBranch(startPoint,gfConfig.getDevelop());
            
            Ref newBranch = git.checkout()
               .setName(prefixedBranchName)
                    .setCreateBranch(true)
                    .setStartPoint(startPoint)
                    .call();

            if (push)
            {
                requireRemoteBranchAbsent(prefixedBranchName);
                RefSpec branchSpec = new RefSpec(prefixedBranchName + ":" + Constants.R_HEADS + prefixedBranchName);
                git.push().setRemote("origin").setRefSpecs(branchSpec).call();
                git.fetch().setRemote("origin").call();

                //setup tracking
                StoredConfig config = git.getRepository().getConfig();
                config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, prefixedBranchName, ConfigConstants.CONFIG_KEY_REMOTE, "origin");
                config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, prefixedBranchName, ConfigConstants.CONFIG_KEY_MERGE, Constants.R_HEADS + prefixedBranchName);
                config.save();
            }

            return newBranch;

        }
        catch (GitAPIException e)
        {
            throw new JGitFlowGitAPIException(e);
        }
        catch (IOException e)
        {
            throw new JGitFlowIOException(e);
        }
    }

    /**
     * Set whether to perform a git fetch of the remote develop branch before branching
     * @param fetch
     *              <code>true</code> to do the fetch, <code>false</code>(default) otherwise
     * @return {@code this}
     */
    public FeatureStartCommand setFetchDevelop(boolean fetch)
    {
        this.fetchDevelop = fetch;
        return this;
    }

    /**
     * Set whether to push the changes to the remote repository
     *
     * @param push <code>true</code> to do the push, <code>false</code>(default) otherwise
     * @return {@code this}
     */
    public FeatureStartCommand setPush(boolean push)
    {
        this.push = push;
        return this;
    }

    public FeatureStartCommand setStartCommit(String commitId)
    {
        this.startCommitString = commitId;

        return this;
    }

    public FeatureStartCommand setStartCommit(RevCommit commit)
    {
        this.startCommit = commit;

        return this;
    }
    
    @Override
    protected String getCommandName()
    {
        return SHORT_NAME;
    }
}
