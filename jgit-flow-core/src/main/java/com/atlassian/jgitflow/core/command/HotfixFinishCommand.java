package com.atlassian.jgitflow.core.command;

import com.atlassian.jgitflow.core.GitFlowConfiguration;
import com.atlassian.jgitflow.core.JGitFlowConstants;
import com.atlassian.jgitflow.core.JGitFlowReporter;
import com.atlassian.jgitflow.core.ReleaseMergeResult;
import com.atlassian.jgitflow.core.exception.*;
import com.atlassian.jgitflow.core.extension.HotfixFinishExtension;
import com.atlassian.jgitflow.core.extension.impl.EmptyHotfixFinishExtension;
import com.atlassian.jgitflow.core.extension.impl.MergeProcessExtensionWrapper;
import com.atlassian.jgitflow.core.util.GitHelper;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.util.StringUtils;

import static com.atlassian.jgitflow.core.util.Preconditions.checkState;

/**
 * Finish a hotfix.
 * <p>
 * This will merge the hotfix into both master and develop and create a tag for the hotfix
 * </p>
 * <p/>
 * Examples (<code>flow</code> is a {@link com.atlassian.jgitflow.core.JGitFlow} instance):
 * <p/>
 * Finish a hotfix:
 * <p/>
 * <pre>
 * flow.hotfixFinish(&quot;1.0&quot;).call();
 * </pre>
 * <p/>
 * Don't delete the local hotfix branch
 * <p/>
 * <pre>
 * flow.hotfixFinish(&quot;1.0&quot;).setKeepBranch(true).call();
 * </pre>
 * <p/>
 * Squash all commits on the hotfix branch into one before merging
 * <p/>
 * <pre>
 * flow.hotfixFinish(&quot;1.0&quot;).setSquash(true).call();
 * </pre>
 * <p/>
 * Push changes to the remote origin
 * <p/>
 * <pre>
 * flow.hotfixFinish(&quot;1.0&quot;).setPush(true).call();
 * </pre>
 * <p/>
 * Don't create a tag for the hotfix
 * <p/>
 * <pre>
 * flow.hotfixFinish(&quot;1.0&quot;).setNoTag(true).call();
 * </pre>
 */
public class HotfixFinishCommand extends AbstractBranchMergingCommand<HotfixFinishCommand, ReleaseMergeResult>
{
    private static final String SHORT_NAME = "hotfix-finish";
    private String message;
    private boolean noTag;
    private HotfixFinishExtension extension;

    /**
     * Create a new hotfix finish command instance.
     * <p></p>
     * An instance of this class is usually obtained by calling {@link com.atlassian.jgitflow.core.JGitFlow#hotfixFinish(String)}
     *
     * @param hotfixName The name/version of the hotfix
     * @param git        The git instance to use
     * @param gfConfig   The GitFlowConfiguration to use
     * @param reporter
     */
    public HotfixFinishCommand(String hotfixName, Git git, GitFlowConfiguration gfConfig, JGitFlowReporter reporter)
    {
        super(hotfixName, git, gfConfig, reporter);

        checkState(!StringUtils.isEmptyOrNull(hotfixName));
        this.message = "tagging hotfix " + hotfixName;
        this.noTag = false;
        this.extension = new EmptyHotfixFinishExtension();
    }

    /**
     * @return nothing
     * @throws com.atlassian.jgitflow.core.exception.JGitFlowGitAPIException
     * @throws com.atlassian.jgitflow.core.exception.LocalBranchMissingException
     * @throws com.atlassian.jgitflow.core.exception.DirtyWorkingTreeException
     * @throws com.atlassian.jgitflow.core.exception.JGitFlowIOException
     * @throws com.atlassian.jgitflow.core.exception.BranchOutOfDateException
     */
    @Override
    public ReleaseMergeResult call() throws JGitFlowGitAPIException, LocalBranchMissingException, DirtyWorkingTreeException, JGitFlowIOException, BranchOutOfDateException, JGitFlowExtensionException, NotInitializedException
    {
        String prefixedBranchName = runBeforeAndGetPrefixedBranchName(extension.before(), JGitFlowConstants.PREFIXES.HOTFIX);

        enforcer().requireGitFlowInitialized();
        enforcer().requireLocalBranchExists(prefixedBranchName);
        enforcer().requireCleanWorkingTree(isAllowUntracked());

        MergeResult developResult = createEmptyMergeResult();
        MergeResult masterResult = createEmptyMergeResult();
        try
        {
            doFetchIfNeeded(extension);

            ensureLocalBranchesNotBehindRemotes(prefixedBranchName, gfConfig.getMaster(), gfConfig.getDevelop());

            //first merge master
            MergeProcessExtensionWrapper masterExtension = new MergeProcessExtensionWrapper(extension.beforeMasterCheckout(), extension.afterMasterCheckout(), extension.beforeMasterMerge(), extension.afterMasterMerge());
            masterResult = doMerge(prefixedBranchName, gfConfig.getMaster(), masterExtension);

            //now, tag master
            if (!noTag && masterResult.getMergeStatus().isSuccessful())
            {
                doTag(gfConfig.getMaster(), message, masterResult);
            }

            //IMPORTANT: we need to back-merge master into develop so that git describe works properly
            MergeProcessExtensionWrapper developExtension = new MergeProcessExtensionWrapper(extension.beforeDevelopCheckout(), extension.afterDevelopCheckout(), extension.beforeDevelopMerge(), extension.afterDevelopMerge());

            developResult = doMerge(gfConfig.getMaster(), gfConfig.getDevelop(), developExtension);

            boolean mergeSuccess = checkMergeResults(masterResult, developResult);

            if (mergeSuccess)
            {
                doPushIfNeeded(extension, !noTag, gfConfig.getDevelop(), gfConfig.getMaster(), prefixedBranchName);
            }

            if (mergeSuccess)
            {
                cleanupBranchesIfNeeded(gfConfig.getDevelop(), prefixedBranchName);
            }

            reporter.infoText(getCommandName(), "checking out '" + gfConfig.getDevelop() + "'");
            git.checkout().setName(gfConfig.getDevelop()).call();

            runExtensionCommands(extension.after());
            return new ReleaseMergeResult(masterResult, developResult);

        }
        catch (GitAPIException e)
        {
            throw new JGitFlowGitAPIException(e);
        }
        finally
        {
            reporter.endCommand();
            reporter.flush();
        }
    }

    /**
     * Set the commit message for the tag creation
     *
     * @param message
     * @return {@code this}
     */
    public HotfixFinishCommand setMessage(String message)
    {
        this.message = message;
        return this;
    }

    /**
     * Set whether to turn off tagging
     *
     * @param noTag <code>true</code> to turn off tagging, <code>false</code>(default) otherwise
     * @return {@code this}
     */
    public HotfixFinishCommand setNoTag(boolean noTag)
    {
        this.noTag = noTag;
        return this;
    }

    public HotfixFinishCommand setExtension(HotfixFinishExtension extension)
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
