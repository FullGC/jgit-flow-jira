package com.atlassian.jgitflow.core;

import java.io.IOException;

import com.atlassian.jgitflow.core.exception.*;
import com.atlassian.jgitflow.core.extension.HotfixFinishExtension;
import com.atlassian.jgitflow.core.util.GitHelper;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.util.StringUtils;

import static com.atlassian.jgitflow.core.util.Preconditions.checkState;

/**
 * Finish a hotfix.
 * <p>
 * This will merge the hotfix into both master and develop and create a tag for the hotfix
 * </p>
 * <p>
 * Examples (<code>flow</code> is a {@link com.atlassian.jgitflow.core.JGitFlow} instance):
 * <p>
 * Finish a hotfix:
 *
 * <pre>
 * flow.hotfixFinish(&quot;1.0&quot;).call();
 * </pre>
 * <p>
 * Don't delete the local hotfix branch
 *
 * <pre>
 * flow.hotfixFinish(&quot;1.0&quot;).setKeepBranch(true).call();
 * </pre>
 * <p>
 * Squash all commits on the hotfix branch into one before merging
 *
 * <pre>
 * flow.hotfixFinish(&quot;1.0&quot;).setSquash(true).call();
 * </pre>
 * <p>
 * Push changes to the remote origin
 *
 * <pre>
 * flow.hotfixFinish(&quot;1.0&quot;).setPush(true).call();
 * </pre>
 * <p>
 * Don't create a tag for the hotfix
 *
 * <pre>
 * flow.hotfixFinish(&quot;1.0&quot;).setNoTag(true).call();
 * </pre>
 */
public class HotfixFinishCommand extends AbstractGitFlowCommand<HotfixFinishCommand, ReleaseMergeResult>
{
    private static final String SHORT_NAME = "hotfix-finish";
    private final String hotfixName;
    private boolean fetch;
    private String message;
    private boolean push;
    private boolean keepBranch;
    private boolean noTag;

    /**
     * Create a new hotfix finish command instance.
     * <p></p>
     * An instance of this class is usually obtained by calling {@link com.atlassian.jgitflow.core.JGitFlow#hotfixFinish(String)}
     * @param hotfixName The name/version of the hotfix
     * @param git The git instance to use
     * @param gfConfig The GitFlowConfiguration to use
     * @param reporter
     */
    public HotfixFinishCommand(String hotfixName, Git git, GitFlowConfiguration gfConfig, JGitFlowReporter reporter)
    {
        super(git, gfConfig, reporter);

        checkState(!StringUtils.isEmptyOrNull(hotfixName));
        this.hotfixName = hotfixName;
        this.fetch = false;
        this.message = "tagging hotfix " + hotfixName;
        this.push = false;
        this.keepBranch = false;
        this.noTag = false;
    }

    /**
     * 
     * @return nothing
     * @throws com.atlassian.jgitflow.core.exception.JGitFlowGitAPIException
     * @throws com.atlassian.jgitflow.core.exception.LocalBranchMissingException
     * @throws com.atlassian.jgitflow.core.exception.DirtyWorkingTreeException
     * @throws com.atlassian.jgitflow.core.exception.JGitFlowIOException
     * @throws com.atlassian.jgitflow.core.exception.BranchOutOfDateException
     */
    @Override
    public ReleaseMergeResult call() throws JGitFlowGitAPIException, LocalBranchMissingException, DirtyWorkingTreeException, JGitFlowIOException, BranchOutOfDateException, JGitFlowExtensionException
    {
        HotfixFinishExtension extension = getExtensionProvider().provideHotfixFinishExtension();
        
        runExtensionCommands(extension.before());
        
        String prefixedHotfixName = gfConfig.getPrefixValue(JGitFlowConstants.PREFIXES.HOTFIX.configKey()) + hotfixName;

        requireLocalBranchExists(prefixedHotfixName);
        requireCleanWorkingTree();

        MergeResult developResult = new MergeResult(null,null,new ObjectId[] { null, null }, MergeResult.MergeStatus.ALREADY_UP_TO_DATE, MergeStrategy.RESOLVE,null);
        MergeResult masterResult = new MergeResult(null,null,new ObjectId[] { null, null }, MergeResult.MergeStatus.ALREADY_UP_TO_DATE,MergeStrategy.RESOLVE,null);
        try
        {
            if (fetch)
            {
                runExtensionCommands(extension.beforeFetch());
                RefSpec developSpec = new RefSpec("+" + Constants.R_HEADS + gfConfig.getDevelop() + ":" + Constants.R_REMOTES + "origin/" + gfConfig.getDevelop());
                RefSpec masterSpec = new RefSpec("+" + Constants.R_HEADS + gfConfig.getMaster() + ":" + Constants.R_REMOTES + "origin/" + gfConfig.getMaster());

                git.fetch().setRemote(Constants.DEFAULT_REMOTE_NAME).setRefSpecs(masterSpec).call();
                git.fetch().setRemote(Constants.DEFAULT_REMOTE_NAME).setRefSpecs(developSpec).call();
                git.fetch().setRemote(Constants.DEFAULT_REMOTE_NAME).call();
                
                runExtensionCommands(extension.afterFetch());
            }

            if (GitHelper.remoteBranchExists(git, prefixedHotfixName, reporter))
            {
                requireLocalBranchNotBehindRemote(prefixedHotfixName);
            }

            if (GitHelper.remoteBranchExists(git, gfConfig.getMaster(), reporter))
            {
                requireLocalBranchNotBehindRemote(gfConfig.getMaster());
            }

            if (GitHelper.remoteBranchExists(git, gfConfig.getDevelop(), reporter))
            {
                requireLocalBranchNotBehindRemote(gfConfig.getDevelop());
            }

            runExtensionCommands(extension.beforeMasterCheckout());
            Ref hotfixBranch = GitHelper.getLocalBranch(git, prefixedHotfixName);
            RevCommit hotfixCommit = GitHelper.getLatestCommit(git, prefixedHotfixName);
        
        /*
        try to merge into master
        in case a previous attempt to finish this release branch has failed,
        but the merge into master was successful, we skip it now
         */
            if (!GitHelper.isMergedInto(git, prefixedHotfixName, gfConfig.getMaster()))
            {
                git.checkout().setName(gfConfig.getMaster()).call();

                runExtensionCommands(extension.afterMasterCheckout());
                
                runExtensionCommands(extension.beforeMasterMerge());
                masterResult = git.merge().setFastForward(MergeCommand.FastForwardMode.NO_FF).include(hotfixBranch).call();
                runExtensionCommands(extension.afterMasterMerge());
            }

            if (!noTag && masterResult.getMergeStatus().isSuccessful())
            {
            /*
            try to tag the release
            in case a previous attempt to finish this release branch has failed,
            but the tag was successful, we skip it now
            */
                String tagName = gfConfig.getPrefixValue(JGitFlowConstants.PREFIXES.VERSIONTAG.configKey()) + hotfixName;
                if (!GitHelper.tagExists(git, tagName))
                {
                    reporter.infoText(
                        getCommandName(),
                        String.format(
                            "tagging hotfix with name: <%s>. On branch (%s). merge status (%s)",
                            tagName,
                            git.getRepository( ).getFullBranch(),
                            masterResult.getMergeStatus( )
                        )
                    );
                    git.tag().setName(tagName).setMessage(getScmMessagePrefix() + message + getScmMessageSuffix()).call();
                }
            }
        
        /*
        try to merge into develop
        in case a previous attempt to finish this release branch has failed,
        but the merge into develop was successful, we skip it now
         */
            runExtensionCommands(extension.beforeDevelopCheckout());
            if (!GitHelper.isMergedInto(git, prefixedHotfixName, gfConfig.getDevelop()))
            {
                git.checkout().setName(gfConfig.getDevelop()).call();
                
                runExtensionCommands(extension.afterDevelopCheckout());

                runExtensionCommands(extension.beforeDevelopMerge());
                developResult = git.merge().setFastForward(MergeCommand.FastForwardMode.NO_FF).include(hotfixBranch).call();
                runExtensionCommands(extension.afterDevelopMerge());
            }

            if (push && masterResult.getMergeStatus().isSuccessful() && developResult.getMergeStatus().isSuccessful())
            {
                //push to develop
                RefSpec developSpec = new RefSpec(gfConfig.getDevelop());
                git.push().setRemote(Constants.DEFAULT_REMOTE_NAME).setRefSpecs(developSpec).call();

                //push to master
                RefSpec masterSpec = new RefSpec(gfConfig.getMaster());
                git.push().setRemote(Constants.DEFAULT_REMOTE_NAME).setRefSpecs(masterSpec).call();

                if (!noTag)
                {
                    git.push().setRemote(Constants.DEFAULT_REMOTE_NAME).setPushTags().call();
                }

                if (GitHelper.remoteBranchExists(git, prefixedHotfixName, reporter))
                {
                    RefSpec branchSpec = new RefSpec(prefixedHotfixName);
                    git.push().setRemote(Constants.DEFAULT_REMOTE_NAME).setRefSpecs(branchSpec).call();
                }

                runExtensionCommands(extension.afterPush());
            }

            if (!keepBranch && masterResult.getMergeStatus().isSuccessful() && developResult.getMergeStatus().isSuccessful())
            {
                git.checkout().setName(gfConfig.getDevelop()).call();
                git.branchDelete().setForce(true).setBranchNames(prefixedHotfixName).call();

                if (push && GitHelper.remoteBranchExists(git, prefixedHotfixName, reporter))
                {
                    RefSpec deleteSpec = new RefSpec(":" + Constants.R_HEADS + prefixedHotfixName);
                    git.push().setRemote(Constants.DEFAULT_REMOTE_NAME).setRefSpecs(deleteSpec).call();
                }
            }

            git.checkout().setName(gfConfig.getDevelop()).call();

        }
        catch (GitAPIException e)
        {
            throw new JGitFlowGitAPIException(e);
        }
        catch (IOException e)
        {
            throw new JGitFlowIOException(e);
        }

        runExtensionCommands(extension.after());
        
        return new ReleaseMergeResult(masterResult,developResult);
    }

    /**
     * Set whether to perform a git fetch of the remote branches before doing the merge
     * @param fetch
     *              <code>true</code> to do the fetch, <code>false</code>(default) otherwise
     * @return {@code this}
     */
    public HotfixFinishCommand setFetch(boolean fetch)
    {
        this.fetch = fetch;
        return this;
    }

    /**
     * Set the commit message for the tag creation
     * @param message
     * @return {@code this}
     */
    public HotfixFinishCommand setMessage(String message)
    {
        this.message = message;
        return this;
    }

    /**
     * Set whether to push the changes to the remote repository
     * @param push
     *              <code>true</code> to do the push, <code>false</code>(default) otherwise
     * @return {@code this}
     */
    public HotfixFinishCommand setPush(boolean push)
    {
        this.push = push;
        return this;
    }

    /**
     * Set whether to keep the local release branch after the merge
     * @param keepBranch
     *              <code>true</code> to keep the branch, <code>false</code>(default) otherwise
     * @return {@code this}
     */
    public HotfixFinishCommand setKeepBranch(boolean keepBranch)
    {
        this.keepBranch = keepBranch;
        return this;
    }

    /**
     * Set whether to turn off tagging
     * @param noTag
     *              <code>true</code> to turn off tagging, <code>false</code>(default) otherwise
     * @return {@code this}
     */
    public HotfixFinishCommand setNoTag(boolean noTag)
    {
        this.noTag = noTag;
        return this;
    }

    @Override
    protected String getCommandName()
    {
        return SHORT_NAME;
    }
}
