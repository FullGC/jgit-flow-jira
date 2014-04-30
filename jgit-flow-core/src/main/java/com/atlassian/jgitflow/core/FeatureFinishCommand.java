package com.atlassian.jgitflow.core;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.atlassian.jgitflow.core.exception.*;
import com.atlassian.jgitflow.core.extension.FeatureFinishExtension;
import com.atlassian.jgitflow.core.extension.impl.EmptyFeatureFinishExtension;
import com.atlassian.jgitflow.core.extension.impl.MergeProcessExtensionWrapper;
import com.atlassian.jgitflow.core.util.FileHelper;
import com.atlassian.jgitflow.core.util.GitHelper;
import com.atlassian.jgitflow.core.util.IterableHelper;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.StringUtils;

import static com.atlassian.jgitflow.core.util.Preconditions.checkState;

/**
 * Finish a feature.
 * <p>
 * This will merge the feature into develop and set the local branch to develop.
 * </p>
 * <p/>
 * Examples (<code>flow</code> is a {@link com.atlassian.jgitflow.core.JGitFlow} instance):
 * <p/>
 * Finish a feature:
 * <p/>
 * <pre>
 * flow.featureFinish(&quot;feature&quot;).call();
 * </pre>
 * <p/>
 * Don't delete the local feature branch
 * <p/>
 * <pre>
 * flow.featureFinish(&quot;feature&quot;).setKeepBranch(true).call();
 * </pre>
 * <p/>
 * Squash all commits on the feature branch into one before merging
 * <p/>
 * <pre>
 * flow.featureFinish(&quot;feature&quot;).setSquash(true).call();
 * </pre>
 */
public class FeatureFinishCommand extends AbstractBranchMergingCommand<FeatureFinishCommand, MergeResult>
{
    private static final String SHORT_NAME = "feature-finish";
    private boolean rebase;
    private boolean squash;
    private boolean noMerge;
    private FeatureFinishExtension extension;

    /**
     * Create a new feature finish command instance.
     * <p></p>
     * An instance of this class is usually obtained by calling {@link com.atlassian.jgitflow.core.JGitFlow#featureFinish(String)}
     *
     * @param name     The name of the feature
     * @param git      The git instance to use
     * @param gfConfig The GitFlowConfiguration to use
     * @param reporter
     */
    public FeatureFinishCommand(String branchName, Git git, GitFlowConfiguration gfConfig, JGitFlowReporter reporter)
    {
        super(branchName, git, gfConfig, reporter);

        checkState(!StringUtils.isEmptyOrNull(branchName));
        this.rebase = false;
        this.squash = false;
        this.noMerge = false;
        this.extension = new EmptyFeatureFinishExtension();
    }

    /**
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
    public MergeResult call() throws NotInitializedException, JGitFlowGitAPIException, LocalBranchMissingException, JGitFlowIOException, DirtyWorkingTreeException, MergeConflictsNotResolvedException, BranchOutOfDateException, JGitFlowExtensionException, GitAPIException
    {
        MergeResult mergeResult = null;

        String prefixedBranchName = runBeforeAndGetPrefixedBranchName(extension.before(), JGitFlowConstants.PREFIXES.FEATURE);

        enforcer().requireGitFlowInitialized();
        enforcer().requireLocalBranchExists(prefixedBranchName);

        //check to see if we're restoring from a merge conflict
        File flowDir = new File(git.getRepository().getDirectory(), JGitFlowConstants.GITFLOW_DIR);
        File mergeBase = new File(flowDir, JGitFlowConstants.MERGE_BASE);

        if (!noMerge && mergeBase.exists())
        {
            reporter.debugText(getCommandName(), "restoring from merge conflict. base: " + mergeBase.getAbsolutePath());
            if (GitHelper.workingTreeIsClean(git, isAllowUntracked(), reporter).isClean())
            {
                //check to see if the merge was done
                String finishBase = FileHelper.readFirstLine(mergeBase);
                if (GitHelper.isMergedInto(git, prefixedBranchName, finishBase))
                {
                    mergeBase.delete();
                    cleanupBranchesIfNeeded(gfConfig.getDevelop(), prefixedBranchName);
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
                reporter.errorText(getCommandName(), "Merge conflicts are not resolved");
                reporter.endCommand();
                throw new MergeConflictsNotResolvedException("Merge conflicts are not resolved");
            }
        }

        //not restoring a merge, continue
        enforcer().requireCleanWorkingTree(isAllowUntracked());

        try
        {
            doFetchIfNeeded(extension);

            ensureLocalBranchesNotBehindRemotes(prefixedBranchName, prefixedBranchName, gfConfig.getDevelop());

            if (rebase)
            {
                runExtensionCommands(extension.beforeRebase());
                FeatureRebaseCommand rebaseCommand = new FeatureRebaseCommand(getBranchName(), git, gfConfig, reporter);
                rebaseCommand.setAllowUntracked(isAllowUntracked()).call();
                runExtensionCommands(extension.afterRebase());
            }

            if (!noMerge)
            {
                Ref localBranchRef = GitHelper.getLocalBranch(git, prefixedBranchName);

                RevCommit developCommit = GitHelper.getLatestCommit(git, gfConfig.getDevelop());
                RevCommit featureCommit = GitHelper.getLatestCommit(git, prefixedBranchName);

                List<RevCommit> commitList = IterableHelper.asList(git.log().setMaxCount(2).addRange(developCommit, featureCommit).call());

                MergeProcessExtensionWrapper developExtension = new MergeProcessExtensionWrapper(extension.beforeDevelopCheckout(), extension.afterDevelopCheckout(), extension.beforeDevelopMerge(), extension.afterDevelopMerge());
                if (commitList.size() < 2)
                {
                    mergeResult = doMerge(localBranchRef, gfConfig.getDevelop(), developExtension, false, MergeCommand.FastForwardMode.FF);
                }
                else
                {
                    mergeResult = doMerge(localBranchRef, gfConfig.getDevelop(), developExtension, squash);
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

            doPushIfNeeded(extension, false, gfConfig.getDevelop(), prefixedBranchName);

            cleanupBranchesIfNeeded(gfConfig.getDevelop(), prefixedBranchName);

            reporter.infoText(getCommandName(), "checking out '" + gfConfig.getDevelop() + "'");
            git.checkout().setName(gfConfig.getDevelop()).call();

            reporter.endCommand();

            runExtensionCommands(extension.after());
            return mergeResult;

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
    }

    /**
     * Set whether to perform a git rebase on the feature before doing the merge
     *
     * @param rebase <code>true</code> to do a rebase, <code>false</code>(default) otherwise
     * @return {@code this}
     */
    public FeatureFinishCommand setRebase(boolean rebase)
    {
        this.rebase = rebase;
        return this;
    }

    /**
     * Set whether to squash all commits into a single commit before the merge
     *
     * @param squash <code>true</code> to squash, <code>false</code>(default) otherwise
     * @return {@code this}
     */
    public FeatureFinishCommand setSquash(boolean squash)
    {
        this.squash = squash;
        return this;
    }

    public FeatureFinishCommand setNoMerge(boolean noMerge)
    {
        this.noMerge = noMerge;
        return this;
    }

    public FeatureFinishCommand setExtension(FeatureFinishExtension extension)
    {
        this.extension = extension;
        return this;
    }

    @Override
    protected String getCommandName()
    {
        return SHORT_NAME;
    }

}
