package com.atlassian.jgitflow.core;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.atlassian.jgitflow.core.exception.*;
import com.atlassian.jgitflow.core.extension.ExtensionProvider;
import com.atlassian.jgitflow.core.util.FileHelper;
import com.atlassian.jgitflow.core.util.GitHelper;
import com.atlassian.jgitflow.core.util.IterableHelper;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.StringUtils;

import static com.atlassian.jgitflow.core.util.Preconditions.checkState;

/**
 * Finish a feature.
 * <p>
 * This will merge the feature into develop and set the local branch to develop.
 * </p>
 * <p>
 * Examples (<code>flow</code> is a {@link com.atlassian.jgitflow.core.JGitFlow} instance):
 * <p>
 * Finish a feature:
 *
 * <pre>
 * flow.featureFinish(&quot;feature&quot;).call();
 * </pre>
 * <p>
 * Don't delete the local feature branch
 *
 * <pre>
 * flow.featureFinish(&quot;feature&quot;).setKeepBranch(true).call();
 * </pre>
 * <p>
 * Squash all commits on the feature branch into one before merging
 *
 * <pre>
 * flow.featureFinish(&quot;feature&quot;).setSquash(true).call();
 * </pre>
 */
public class FeatureFinishCommand extends AbstractGitFlowCommand<FeatureFinishCommand,Void>
{
    private static final String SHORT_NAME = "feature-finish";
    private final String branchName;
    private boolean fetchDevelop;
    private boolean rebase;
    private boolean keepBranch;
    private boolean forceDeleteBranch;
    private boolean squash;
    private boolean push;
    private boolean noMerge;

    /**
     * Create a new feature finish command instance.
     * <p></p>
     * An instance of this class is usually obtained by calling {@link com.atlassian.jgitflow.core.JGitFlow#featureFinish(String)}
     * @param name The name of the feature
     * @param git The git instance to use
     * @param gfConfig The GitFlowConfiguration to use
     * @param reporter
     */
    public FeatureFinishCommand(String name, Git git, GitFlowConfiguration gfConfig, JGitFlowReporter reporter)
    {
        super(git, gfConfig, reporter);

        checkState(!StringUtils.isEmptyOrNull(name));
        this.branchName = name;
        this.fetchDevelop = false;
        this.rebase = false;
        this.keepBranch = false;
        this.forceDeleteBranch = false;
        this.squash = false;
        this.push = false;
        this.noMerge = false;
    }

    /**
     * 
     * @return nothing
     * @throws com.atlassian.jgitflow.core.exception.NotInitializedException
     * @throws com.atlassian.jgitflow.core.exception.JGitFlowGitAPIException
     * @throws com.atlassian.jgitflow.core.exception.LocalBranchMissingException
     * @throws com.atlassian.jgitflow.core.exception.JGitFlowIOException
     * @throws com.atlassian.jgitflow.core.exception.DirtyWorkingTreeException
     * @throws com.atlassian.jgitflow.core.exception.MergeConflictsNotResolvedException
     * @throws com.atlassian.jgitflow.core.exception.BranchOutOfDateException
     */
    @Override
    public Void call() throws NotInitializedException, JGitFlowGitAPIException, LocalBranchMissingException, JGitFlowIOException, DirtyWorkingTreeException, MergeConflictsNotResolvedException, BranchOutOfDateException
    {
        reporter.debugCommandCall(getCommandName());
        String prefixedBranchName = gfConfig.getPrefixValue(JGitFlowConstants.PREFIXES.FEATURE.configKey()) + branchName;

        requireGitFlowInitialized();
        requireLocalBranchExists(prefixedBranchName);

        //check to see if we're restoring from a merge conflict
        File flowDir = new File(git.getRepository().getDirectory(), JGitFlowConstants.GITFLOW_DIR);
        File mergeBase = new File(flowDir, JGitFlowConstants.MERGE_BASE);

        if (!noMerge && mergeBase.exists())
        {
            reporter.debugText(getCommandName(),"restoring from merge conflict. base: " + mergeBase.getAbsolutePath());
            if (GitHelper.workingTreeIsClean(git, isAllowUntracked(), reporter).isClean())
            {
                //check to see if the merge was done
                String finishBase = FileHelper.readFirstLine(mergeBase);
                if (GitHelper.isMergedInto(git, prefixedBranchName, finishBase))
                {
                    mergeBase.delete();
                    cleanupBranch(prefixedBranchName);
                    reporter.endCommand();
                    return null;
                }
                else
                {
                    mergeBase.delete();
                }
            }
            else
            {
                reporter.errorText(getCommandName(),"Merge conflicts are not resolved");
                reporter.endCommand();
                throw new MergeConflictsNotResolvedException("Merge conflicts are not resolved");
            }
        }

        //not restoring a merge, continue
        requireCleanWorkingTree();

        boolean remoteFeatureExists = GitHelper.remoteBranchExists(git, prefixedBranchName, reporter);

        reporter.debugText(getCommandName(),"remote feature exists? " + remoteFeatureExists);
        try
        {
            //update from remote if needed
            if (remoteFeatureExists && fetchDevelop)
            {
                RefSpec branchSpec = new RefSpec("+" + Constants.R_HEADS + prefixedBranchName + ":" + Constants.R_REMOTES + "origin/" + prefixedBranchName);
                RefSpec developSpec = new RefSpec("+" + Constants.R_HEADS + gfConfig.getDevelop() + ":" + Constants.R_REMOTES + "origin/" + gfConfig.getDevelop());
                git.fetch().setRefSpecs(branchSpec).call();
                git.fetch().setRefSpecs(developSpec).call();
            }

            //make sure nothing is behind
            if (remoteFeatureExists)
            {
                requireLocalBranchNotBehindRemote(prefixedBranchName);
            }

            if (GitHelper.remoteBranchExists(git, gfConfig.getDevelop(), reporter))
            {
                requireLocalBranchNotBehindRemote(gfConfig.getDevelop());
            }

            if (rebase)
            {
                FeatureRebaseCommand rebaseCommand = new FeatureRebaseCommand(branchName, git, gfConfig, reporter);
                rebaseCommand.setAllowUntracked(isAllowUntracked()).call();
            }

            if(!noMerge)
            {
                reporter.debugText(getCommandName(),"beginning merges...");
                //merge into base
                git.checkout().setName(gfConfig.getDevelop()).call();
    
                Ref featureBranch = GitHelper.getLocalBranch(git, prefixedBranchName);
    
                RevCommit developCommit = GitHelper.getLatestCommit(git, gfConfig.getDevelop());
                RevCommit featureCommit = GitHelper.getLatestCommit(git, prefixedBranchName);
    
                List<RevCommit> commitList = IterableHelper.asList(git.log().setMaxCount(2).addRange(developCommit, featureCommit).call());
    
                MergeResult mergeResult = null;
    
                if (commitList.size() < 2)
                {
                    mergeResult = git.merge().setFastForward(MergeCommand.FastForwardMode.FF).include(featureBranch).call();
                    if(mergeResult.getMergeStatus().isSuccessful())
                    {
                        git.commit().setMessage(getScmMessagePrefix() + "merging '" + prefixedBranchName + "' into '" + gfConfig.getDevelop() + "'" + getScmMessageSuffix()).call();
                    }
                }
                else
                {
                    if (squash)
                    {
                        mergeResult = git.merge().setSquash(true).include(featureBranch).call();
                        if(mergeResult.getMergeStatus().isSuccessful())
                        {
                            git.commit().setMessage(getScmMessagePrefix() + "squashing '" + prefixedBranchName + "' into '" + gfConfig.getDevelop() + "'" + getScmMessageSuffix()).call();
                        }
                        this.forceDeleteBranch = true;
                    }
                    else
                    {
                        mergeResult = git.merge().setFastForward(MergeCommand.FastForwardMode.NO_FF).include(featureBranch).call();
                    }
                }
    
                if (null == mergeResult || mergeResult.getMergeStatus().equals(MergeResult.MergeStatus.FAILED) || mergeResult.getMergeStatus().equals(MergeResult.MergeStatus.CONFLICTING))
                {
                    FileHelper.createParentDirs(mergeBase);
                    FileUtils.createNewFile(mergeBase);
                    FileHelper.writeStringToFile(gfConfig.getDevelop(), mergeBase);
                    reporter.endCommand();
                    reporter.flush();
                    throw new MergeConflictsNotResolvedException("merge conflicts exist, please resolve!");
                }
            }

            reporter.debugText(getCommandName(),"do wee need to push? " + push);
            if (push)
            {
                reporter.debugText(getCommandName(),"does remote branch [" + prefixedBranchName + "] exist? " + GitHelper.remoteBranchExists(git, prefixedBranchName, reporter));
                
                if (GitHelper.remoteBranchExists(git, prefixedBranchName, reporter))
                {
                    reporter.infoText(getCommandName(), "pushing feature branch");
                    RefSpec branchSpec = new RefSpec(prefixedBranchName);
                    git.push().setRemote(Constants.DEFAULT_REMOTE_NAME).setRefSpecs(branchSpec).call();
                }
            }
            
            cleanupBranch(prefixedBranchName);

        }
        catch (GitAPIException e)
        {
            reporter.endCommand();
            throw new JGitFlowGitAPIException(e);
        }
        catch (IOException e)
        {
            reporter.endCommand();
            throw new JGitFlowIOException(e);
        }
        finally
        {
            reporter.endCommand();
            reporter.flush();
        }

        
        return null;
    }

    private void cleanupBranch(String branch) throws JGitFlowGitAPIException, LocalBranchMissingException, DirtyWorkingTreeException, JGitFlowIOException
    {
        requireLocalBranchExists(branch);
        requireCleanWorkingTree();

        try
        {
            //make sure we're on the develop branch
            git.checkout().setName(gfConfig.getDevelop()).call();

            //delete the branch
            if (fetchDevelop)
            {
                RefSpec spec = new RefSpec(":" + Constants.R_HEADS + branch);
                git.push().setRemote("origin").setRefSpecs(spec).call();
            }

            if (!keepBranch)
            {
                if (noMerge || forceDeleteBranch)
                {
                    git.branchDelete().setForce(true).setBranchNames(branch).call();
                }
                else
                {
                    git.branchDelete().setForce(false).setBranchNames(branch).call();
                }
            }
        }
        catch (GitAPIException e)
        {
            throw new JGitFlowGitAPIException(e);
        }
    }

    /**
     * Set whether to perform a git fetch of the remote develop branch before doing the merge
     * @param fetch
     *              <code>true</code> to do the fetch, <code>false</code>(default) otherwise
     * @return {@code this}
     */
    public FeatureFinishCommand setFetchDevelop(boolean fetch)
    {
        this.fetchDevelop = fetch;
        return this;
    }

    /**
     * Set whether to perform a git rebase on the feature before doing the merge
     * @param rebase
     *              <code>true</code> to do a rebase, <code>false</code>(default) otherwise
     * @return {@code this}
     */
    public FeatureFinishCommand setRebase(boolean rebase)
    {
        this.rebase = rebase;
        return this;
    }

    /**
     * Set whether to keep the local feature branch after the merge
     * @param keep
     *              <code>true</code> to keep the branch, <code>false</code>(default) otherwise
     * @return {@code this}
     */
    public FeatureFinishCommand setKeepBranch(boolean keep)
    {
        this.keepBranch = keep;
        return this;
    }

    /**
     * Set whether to use the force flag when deleting the local feature branch
     * @param force
     *              <code>true</code> to force, <code>false</code>(default) otherwise
     * @return {@code this}
     */
    public FeatureFinishCommand setForceDeleteBranch(boolean force)
    {
        this.forceDeleteBranch = force;
        return this;
    }

    /**
     * Set whether to squash all commits into a single commit before the merge
     * @param squash
     *              <code>true</code> to squash, <code>false</code>(default) otherwise
     * @return {@code this}
     */
    public FeatureFinishCommand setSquash(boolean squash)
    {
        this.squash = squash;
        return this;
    }

    public FeatureFinishCommand setPush(boolean push)
    {
        this.push = push;
        return this;
    }

    public FeatureFinishCommand setNoMerge(boolean noMerge)
    {
        this.noMerge = noMerge;
        return this;
    }
    
    @Override
    protected String getCommandName()
    {
        return SHORT_NAME;
    }

}
