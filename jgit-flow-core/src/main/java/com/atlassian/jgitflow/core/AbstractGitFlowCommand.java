package com.atlassian.jgitflow.core;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;

import com.atlassian.jgitflow.core.exception.*;
import com.atlassian.jgitflow.core.util.CleanStatus;
import com.atlassian.jgitflow.core.util.GitHelper;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;

import static com.atlassian.jgitflow.core.util.Preconditions.checkNotNull;

/**
 * The base class for all JGitFlow commands.
 * <p>
 * Most commands should extend this class as it provides common helper methods
 * and methods to ensure valid state.
 * </p>
 * @param <T> The return type of the call() method
 */
public abstract class AbstractGitFlowCommand<T> implements Callable<T>
{
    protected final Git git;
    protected final GitFlowConfiguration gfConfig;
    protected final JGitFlowReporter reporter;
    private boolean allowUntracked;
    private String scmMessagePrefix;
    private String scmMessageSuffix;

    protected AbstractGitFlowCommand(Git git, GitFlowConfiguration gfConfig, JGitFlowReporter reporter)
    {
        checkNotNull(git);
        checkNotNull(gfConfig);
        checkNotNull(reporter);

        this.git = git;
        this.gfConfig = gfConfig;
        this.reporter = reporter;
        this.allowUntracked = false;
        this.scmMessagePrefix = "";
        this.scmMessageSuffix = "";
        
    }

    public AbstractGitFlowCommand setAllowUntracked(boolean allow)
    {
        this.allowUntracked = allow;
        return this;
    }
    
    public boolean isAllowUntracked()
    {
        return allowUntracked;
    }

    public String getScmMessagePrefix()
    {
        return scmMessagePrefix;
    }

    public AbstractGitFlowCommand setScmMessagePrefix(String scmMessagePrefix)
    {
        this.scmMessagePrefix = scmMessagePrefix;
        return this;
    }

    public String getScmMessageSuffix()
    {
        return scmMessageSuffix;
    }

    public AbstractGitFlowCommand setScmMessageSuffix(String scmMessageSuffix)
    {
        this.scmMessageSuffix = scmMessageSuffix;
        return this;
    }

    /**
     * Requires that git flow has been initialized for the project represented by the internal {Git} instance
     * @throws com.atlassian.jgitflow.core.exception.NotInitializedException
     * @throws com.atlassian.jgitflow.core.exception.JGitFlowGitAPIException
     */
    protected void requireGitFlowInitialized() throws NotInitializedException, JGitFlowGitAPIException
    {
        if (!gfConfig.gitFlowIsInitialized())
        {
            reporter.errorText(getCommandName(), "requireGitFlowInitialized() failed");
            reporter.flush();
            throw new NotInitializedException("Git flow is not initialized in " + git.getRepository().getWorkTree().getPath());
        }
    }

    protected abstract String getCommandName();

    /**
     * Requires that a local branch with the given name does not yet exist
     * @param branch the name of the branch to test
     * @throws com.atlassian.jgitflow.core.exception.LocalBranchExistsException
     * @throws com.atlassian.jgitflow.core.exception.JGitFlowGitAPIException
     */
    protected void requireLocalBranchAbsent(String branch) throws LocalBranchExistsException, JGitFlowGitAPIException
    {
        if (GitHelper.localBranchExists(git, branch))
        {
            reporter.errorText(getCommandName(), "requireLocalBranchAbsent() failed: '" + branch + "' already exists");
            reporter.flush();
            throw new LocalBranchExistsException("local branch '" + branch + "' already exists");
        }
    }

    /**
     * Requires that a local branch with the given name exists
     * @param branch The name of the branch to test
     * @throws com.atlassian.jgitflow.core.exception.LocalBranchMissingException
     * @throws com.atlassian.jgitflow.core.exception.JGitFlowGitAPIException
     */
    protected void requireLocalBranchExists(String branch) throws LocalBranchMissingException, JGitFlowGitAPIException
    {
        if (!GitHelper.localBranchExists(git, branch))
        {
            reporter.errorText(getCommandName(), "localBranchExists() failed: '" + branch + "' does not exist");
            reporter.flush();
            throw new LocalBranchMissingException("local branch " + branch + " does not exist");
        }
    }

    /**
     * Requires that a remote branch with the given name does not yet exist
     * @param branch The name of the branch to test
     * @throws com.atlassian.jgitflow.core.exception.RemoteBranchExistsException
     * @throws com.atlassian.jgitflow.core.exception.JGitFlowGitAPIException
     */
    protected void requireRemoteBranchAbsent(String branch) throws RemoteBranchExistsException, JGitFlowGitAPIException
    {
        if (GitHelper.remoteBranchExists(git, branch, reporter))
        {
            reporter.errorText(getCommandName(), "requireRemoteBranchAbsent() failed: '" + branch + "' already exists");
            reporter.flush();
            throw new RemoteBranchExistsException("remote branch '" + branch + "' already exists");
        }
    }

    /**
     * Requires that a remote branch with the given name exists
     * @param branch The name of the branch to test
     * @throws com.atlassian.jgitflow.core.exception.RemoteBranchMissingException
     * @throws com.atlassian.jgitflow.core.exception.JGitFlowGitAPIException
     */
    protected void requireRemoteBranchExists(String branch) throws RemoteBranchMissingException, JGitFlowGitAPIException
    {
        if (!GitHelper.remoteBranchExists(git, branch, reporter))
        {
            reporter.errorText(getCommandName(), "requireRemoteBranchExists() failed: '" + branch + "' does not exist");
            reporter.flush();
            throw new RemoteBranchMissingException("remote branch " + branch + " does not exist");
        }
    }

    /**
     * Requires that a tag with the given name does not yet exist
     * @param name The name of the tag to test
     * @throws com.atlassian.jgitflow.core.exception.TagExistsException
     * @throws com.atlassian.jgitflow.core.exception.JGitFlowGitAPIException
     */
    protected void requireTagAbsent(String name) throws TagExistsException, JGitFlowGitAPIException
    {
        if (GitHelper.tagExists(git, name))
        {
            reporter.errorText(getCommandName(), "requireTagAbsent() failed: '" + name + "' already exists");
            reporter.flush();
            throw new TagExistsException("tag '" + name + "' already exists");
        }
    }

    /**
     * Requires that the local branch with the given name is not behind a remote brach with the same name
     * @param branch The name of the branch to test
     * @throws com.atlassian.jgitflow.core.exception.BranchOutOfDateException
     * @throws com.atlassian.jgitflow.core.exception.JGitFlowIOException
     */
    protected void requireLocalBranchNotBehindRemote(String branch) throws BranchOutOfDateException, JGitFlowIOException
    {
        reporter.debugMethod(getCommandName(),"requireLocalBranchNotBehindRemote");
        boolean behind = GitHelper.localBranchBehindRemote(git,branch,reporter);

        if (behind)
        {
            reporter.errorText(getCommandName(),"local branch '" + branch + "' is behind the remote branch");
            reporter.endMethod();
            reporter.flush();
            throw new BranchOutOfDateException("local branch '" + branch + "' is behind the remote branch");
        }
        
        reporter.endMethod();
    }

    /**
     * Requires that the local working tree has no un-committed changes
     * @throws com.atlassian.jgitflow.core.exception.DirtyWorkingTreeException
     * @throws com.atlassian.jgitflow.core.exception.JGitFlowIOException
     * @throws com.atlassian.jgitflow.core.exception.JGitFlowGitAPIException
     */
    protected void requireCleanWorkingTree() throws DirtyWorkingTreeException, JGitFlowIOException, JGitFlowGitAPIException
    {
        CleanStatus cs = GitHelper.workingTreeIsClean(git,isAllowUntracked(),reporter);
        if (cs.isNotClean())
        {
            reporter.errorText(getCommandName(),cs.getMessage());
            reporter.flush();
            throw new DirtyWorkingTreeException(cs.getMessage());
        }
    }

    /**
     * Requires that no release branches already exist
     * @throws com.atlassian.jgitflow.core.exception.ReleaseBranchExistsException
     * @throws com.atlassian.jgitflow.core.exception.JGitFlowGitAPIException
     */
    protected void requireNoExistingReleaseBranches() throws ReleaseBranchExistsException, JGitFlowGitAPIException
    {
        List<Ref> branches = GitHelper.listBranchesWithPrefix(git, JGitFlowConstants.PREFIXES.RELEASE.configKey(),reporter);

        if (!branches.isEmpty())
        {
            reporter.errorText(getCommandName(),"a release branch [" + branches.get(0).getName() + "] already exists. Finish that first!");
            reporter.flush();
            throw new ReleaseBranchExistsException("a release branch [" + branches.get(0).getName() + "] already exists. Finish that first!");
        }
    }

    /**
     * Requires that no hotfix branches already exist
     * @throws com.atlassian.jgitflow.core.exception.HotfixBranchExistsException
     * @throws com.atlassian.jgitflow.core.exception.JGitFlowGitAPIException
     */
    protected void requireNoExistingHotfixBranches() throws HotfixBranchExistsException, JGitFlowGitAPIException
    {
        List<Ref> branches = GitHelper.listBranchesWithPrefix(git, JGitFlowConstants.PREFIXES.HOTFIX.configKey(),reporter);

        if (!branches.isEmpty())
        {
            reporter.errorText(getCommandName(),"a hotfix branch [" + branches.get(0).getName() + "] already exists. Finish that first!");
            reporter.flush();
            throw new HotfixBranchExistsException("a hotfix branch [" + branches.get(0).getName() + "] already exists. Finish that first!");
        }
    }

    /**
     * Requires that a local branch contains the given commit
     * @param commit the commit to test
     * @param branch the name of the branch to check
     * @throws com.atlassian.jgitflow.core.exception.LocalBranchMissingException
     * @throws com.atlassian.jgitflow.core.exception.JGitFlowGitAPIException
     */
    protected void requireCommitOnBranch(RevCommit commit, String branch) throws LocalBranchExistsException, JGitFlowGitAPIException, JGitFlowIOException
    {
        if (!GitHelper.isMergedInto(git,commit,branch))
        {
            reporter.errorText(getCommandName(), "requireCommitOnBranch() failed: '" + commit.getName() + "' is not on " + branch);
            reporter.flush();
            throw new LocalBranchExistsException("commit '" + commit.getName() + "' does not exist on " + branch);
        }
    }

}
