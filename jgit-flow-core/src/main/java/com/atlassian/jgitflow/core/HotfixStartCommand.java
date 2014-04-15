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
 * Start a hotfix.
 * <p>
 * This will create a new branch using the hotfix prefix and release name from the tip of develop
 * </p>
 * <p>
 * Examples (<code>flow</code> is a {@link com.atlassian.jgitflow.core.JGitFlow} instance):
 * <p>
 * Start a feature:
 *
 * <pre>
 * flow.hotfixStart(&quot;1.0.1&quot;).call();
 * </pre>
 * <p>
 * Perform a fetch of develop before branching
 *
 * <pre>
 * flow.hotfixStart(&quot;1.0.1&quot;).setFetch(true).call();
 * </pre>
 */
public class HotfixStartCommand extends AbstractGitFlowCommand<Ref>
{
    private static final String SHORT_NAME = "hotfix-start";
    private final String hotfixName;
    private boolean fetch;
    private boolean push;
    private RevCommit startCommit;
    private String startCommitString;

    /**
     * Create a new hotfix start command instance.
     * <p></p>
     * An instance of this class is usually obtained by calling {@link com.atlassian.jgitflow.core.JGitFlow#hotfixStart(String)}
     * @param hotfixName The name of the hotfix
     * @param git The git instance to use
     * @param gfConfig The GitFlowConfiguration to use
     * @param reporter
     */
    public HotfixStartCommand(String hotfixName, Git git, GitFlowConfiguration gfConfig, JGitFlowReporter reporter)
    {
        super(git, gfConfig, reporter);

        checkState(!StringUtils.isEmptyOrNull(hotfixName));
        this.hotfixName = hotfixName;
        this.fetch = false;
        this.push = false;
    }

    @Override
    public HotfixStartCommand setAllowUntracked(boolean allow)
    {
        super.setAllowUntracked(allow);
        return this;
    }

    @Override
    public HotfixStartCommand setScmMessagePrefix(String scmMessagePrefix)
    {
        super.setScmMessagePrefix(scmMessagePrefix);
        return this;
    }

    @Override
    public HotfixStartCommand setScmMessageSuffix(String scmMessageSuffix)
    {
        super.setScmMessageSuffix(scmMessageSuffix);
        return this;
    }
    
    /**
     * 
     * @return A reference to the new hotfix branch
     * @throws com.atlassian.jgitflow.core.exception.NotInitializedException
     * @throws com.atlassian.jgitflow.core.exception.JGitFlowGitAPIException
     * @throws com.atlassian.jgitflow.core.exception.HotfixBranchExistsException
     * @throws com.atlassian.jgitflow.core.exception.DirtyWorkingTreeException
     * @throws com.atlassian.jgitflow.core.exception.JGitFlowIOException
     * @throws com.atlassian.jgitflow.core.exception.LocalBranchExistsException
     * @throws com.atlassian.jgitflow.core.exception.TagExistsException
     * @throws com.atlassian.jgitflow.core.exception.BranchOutOfDateException
     */
    @Override
    public Ref call() throws NotInitializedException, JGitFlowGitAPIException, HotfixBranchExistsException, DirtyWorkingTreeException, JGitFlowIOException, LocalBranchExistsException, TagExistsException, BranchOutOfDateException, LocalBranchMissingException, RemoteBranchExistsException
    {
        String prefixedHotfixName = gfConfig.getPrefixValue(JGitFlowConstants.PREFIXES.HOTFIX.configKey()) + hotfixName;

        requireGitFlowInitialized();
        requireNoExistingHotfixBranches();
        requireCleanWorkingTree();
        requireLocalBranchAbsent(prefixedHotfixName);

        try
        {
            if (fetch)
            {
                RefSpec spec = new RefSpec("+" + Constants.R_HEADS + gfConfig.getMaster() + ":" + Constants.R_REMOTES + "origin/" + gfConfig.getMaster());
                git.fetch().setRefSpecs(spec).call();
            }

            requireTagAbsent(gfConfig.getPrefixValue(JGitFlowConstants.PREFIXES.VERSIONTAG.configKey()) + hotfixName);

            if (GitHelper.remoteBranchExists(git, gfConfig.getMaster(), reporter))
            {
                requireLocalBranchNotBehindRemote(gfConfig.getMaster());
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
                startPoint = GitHelper.getLatestCommit(git, gfConfig.getMaster());
            }

            requireCommitOnBranch(startPoint,gfConfig.getMaster());
            
            Ref newBranch = git.checkout()
                      .setName(prefixedHotfixName)
                      .setCreateBranch(true)
                      .setStartPoint(startPoint)
                      .call();

            if (push)
            {
                requireRemoteBranchAbsent(prefixedHotfixName);
                RefSpec branchSpec = new RefSpec(prefixedHotfixName + ":" + Constants.R_HEADS + prefixedHotfixName);
                git.push().setRemote("origin").setRefSpecs(branchSpec).call();
                git.fetch().setRemote("origin").call();

                //setup tracking
                StoredConfig config = git.getRepository().getConfig();
                config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, prefixedHotfixName, ConfigConstants.CONFIG_KEY_REMOTE, "origin");
                config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, prefixedHotfixName, ConfigConstants.CONFIG_KEY_MERGE, Constants.R_HEADS + prefixedHotfixName);
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
    public HotfixStartCommand setFetch(boolean fetch)
    {
        this.fetch = fetch;
        return this;
    }

    public HotfixStartCommand setStartCommit(String commitId)
    {
        this.startCommitString = commitId;

        return this;
    }

    public HotfixStartCommand setStartCommit(RevCommit commit)
    {
        this.startCommit = commit;

        return this;
    }

    /**
     * Set whether to push the changes to the remote repository
     *
     * @param push <code>true</code> to do the push, <code>false</code>(default) otherwise
     * @return {@code this}
     */
    public HotfixStartCommand setPush(boolean push)
    {
        this.push = push;
        return this;
    }

    @Override
    protected String getCommandName()
    {
        return SHORT_NAME;
    }
}
