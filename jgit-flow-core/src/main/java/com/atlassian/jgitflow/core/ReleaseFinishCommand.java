package com.atlassian.jgitflow.core;

import com.atlassian.jgitflow.core.exception.*;
import com.atlassian.jgitflow.core.extension.ReleaseFinishExtension;
import com.atlassian.jgitflow.core.util.GitHelper;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.util.StringUtils;

import static com.atlassian.jgitflow.core.util.Preconditions.checkState;

/**
 * Finish a release.
 * <p>
 * This will merge the release into both master and develop and create a tag for the release
 * </p>
 * <p/>
 * Examples (<code>flow</code> is a {@link com.atlassian.jgitflow.core.JGitFlow} instance):
 * <p/>
 * Finish a release:
 * <p/>
 * <pre>
 * flow.releaseFinish(&quot;1.0&quot;).call();
 * </pre>
 * <p/>
 * Don't delete the local release branch
 * <p/>
 * <pre>
 * flow.releaseFinish(&quot;1.0&quot;).setKeepBranch(true).call();
 * </pre>
 * <p/>
 * Squash all commits on the release branch into one before merging
 * <p/>
 * <pre>
 * flow.releaseFinish(&quot;1.0&quot;).setSquash(true).call();
 * </pre>
 * <p/>
 * Push changes to the remote origin
 * <p/>
 * <pre>
 * flow.releaseFinish(&quot;1.0&quot;).setPush(true).call();
 * </pre>
 * <p/>
 * Don't create a tag for the release
 * <p/>
 * <pre>
 * flow.releaseFinish(&quot;1.0&quot;).setNoTag(true).call();
 * </pre>
 */
public class ReleaseFinishCommand extends AbstractGitFlowCommand<ReleaseFinishCommand, ReleaseMergeResult>
{
    private static final String SHORT_NAME = "release-finish";
    private final String releaseName;
    private boolean fetch;
    private String message;
    private boolean push;
    private boolean keepBranch;
    private boolean noTag;
    private boolean squash;
    private boolean noMerge;

    /**
     * Create a new release finish command instance.
     * <p></p>
     * An instance of this class is usually obtained by calling {@link com.atlassian.jgitflow.core.JGitFlow#releaseFinish(String)}
     *
     * @param releaseName The name/version of the release
     * @param git         The git instance to use
     * @param gfConfig    The GitFlowConfiguration to use
     * @param reporter
     */
    public ReleaseFinishCommand(String releaseName, Git git, GitFlowConfiguration gfConfig, JGitFlowReporter reporter)
    {
        super(git, gfConfig, reporter);
        checkState(!StringUtils.isEmptyOrNull(releaseName));
        this.releaseName = releaseName;
        this.fetch = false;
        this.message = "tagging release " + releaseName;
        this.push = false;
        this.keepBranch = false;
        this.noTag = false;
        this.squash = false;
        this.noMerge = false;
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
    public ReleaseMergeResult call() throws JGitFlowGitAPIException, LocalBranchMissingException, DirtyWorkingTreeException, JGitFlowIOException, BranchOutOfDateException, JGitFlowExtensionException
    {
        ReleaseFinishExtension extension = getExtensionProvider().provideReleaseFinishExtension();
        
        reporter.commandCall(getCommandName());
        
        runExtensionCommands(extension.before());
        
        String prefixedReleaseName = gfConfig.getPrefixValue(JGitFlowConstants.PREFIXES.RELEASE.configKey()) + releaseName;

        requireLocalBranchExists(prefixedReleaseName);
        requireCleanWorkingTree();

        MergeResult developResult = new MergeResult(null, null, new ObjectId[]{null, null}, MergeResult.MergeStatus.ALREADY_UP_TO_DATE, MergeStrategy.RESOLVE, null);
        MergeResult masterResult = new MergeResult(null, null, new ObjectId[]{null, null}, MergeResult.MergeStatus.ALREADY_UP_TO_DATE, MergeStrategy.RESOLVE, null);
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

            if (GitHelper.remoteBranchExists(git, prefixedReleaseName, reporter))
            {
                requireLocalBranchNotBehindRemote(prefixedReleaseName);
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
            
            Ref releaseBranch = GitHelper.getLocalBranch(git, prefixedReleaseName);
        
        if(!noMerge)
        {
            /*
            try to merge into master
            in case a previous attempt to finish this release branch has failed,
            but the merge into master was successful, we skip it now
             */
                if (!GitHelper.isMergedInto(git, prefixedReleaseName, gfConfig.getMaster()))
                {
                    git.checkout().setName(gfConfig.getMaster()).call();
                    
                    runExtensionCommands(extension.afterMasterCheckout());
                    
                    reporter.infoText(getCommandName(), "merging '" + prefixedReleaseName + "' into '" + gfConfig.getMaster() + "'...");
                    
                    runExtensionCommands(extension.beforeMasterMerge());
                    if (squash)
                    {
                        reporter.infoText(getCommandName(), "squashing merge");
                        masterResult = git.merge().setSquash(true).include(releaseBranch).call();
                        if(masterResult.getMergeStatus().isSuccessful())
                        {
                            git.commit().setMessage(getScmMessagePrefix() + "squashing '" + prefixedReleaseName + "' into '" + gfConfig.getMaster() + "'" + getScmMessageSuffix()).call();
                        }
                    }
                    else
                    {
                        masterResult = git.merge().setFastForward(MergeCommand.FastForwardMode.NO_FF).include(releaseBranch).call();
                    }
                    
                    runExtensionCommands(extension.afterMasterMerge());
                }
    
                reporter.mergeResult(getCommandName(), masterResult);
    
                if (!masterResult.getMergeStatus().isSuccessful())
                {
                    reporter.errorText(getCommandName(), "merge into '" + gfConfig.getMaster() + "' was not successful! Aborting the release...");
                    if (masterResult.getMergeStatus().equals(MergeResult.MergeStatus.CONFLICTING))
                    {
                        reporter.errorText(getCommandName(), "please resolve your merge conflicts and re-run " + getCommandName());
                    }
                    else
                    {
                        reporter.errorText(getCommandName(), "until JGit supports merge resets, please run 'git reset --merge' to get back to a clean state");
                    }
                }
                
                /*
                try to merge into develop
                in case a previous attempt to finish this release branch has failed,
                but the merge into develop was successful, we skip it now
                 */
                runExtensionCommands(extension.beforeDevelopCheckout());
                if (!GitHelper.isMergedInto(git, prefixedReleaseName, gfConfig.getDevelop()))
                {
                    reporter.infoText(getCommandName(), "merging '" + prefixedReleaseName + "' into '" + gfConfig.getDevelop() + "'...");
                    git.checkout().setName(gfConfig.getDevelop()).call();
                    
                    runExtensionCommands(extension.afterDevelopCheckout());
                    
                    runExtensionCommands(extension.beforeDevelopMerge());
    
                    if (squash)
                    {
                        reporter.infoText(getCommandName(), "squashing merge");
                        developResult = git.merge().setSquash(true).include(releaseBranch).call();
                        if(developResult.getMergeStatus().isSuccessful())
                        {
                            git.commit().setMessage(getScmMessagePrefix() + "squashing '" + prefixedReleaseName + "' into '" + gfConfig.getDevelop() + "'" + getScmMessageSuffix()).call();
                        }
                    }
                    else
                    {
                        developResult = git.merge().setFastForward(MergeCommand.FastForwardMode.NO_FF).include(releaseBranch).call();
                    }
                    
                    runExtensionCommands(extension.afterDevelopMerge());
                }
    
                reporter.mergeResult(getCommandName(), developResult);
                
                if (!developResult.getMergeStatus().isSuccessful())
                {
                    reporter.errorText(getCommandName(), "merge into '" + gfConfig.getDevelop() + "' was not successful! Aborting the release...");
                    if (developResult.getMergeStatus().equals(MergeResult.MergeStatus.CONFLICTING))
                    {
                        reporter.errorText(getCommandName(), "please resolve your merge conflicts and re-run " + getCommandName());
                    }
                    else
                    {
                        reporter.errorText(getCommandName(), "until JGit supports merge resets, please run 'git reset --merge' to get back to a clean state");
                    }
                }
            }

            if (!noTag && masterResult.getMergeStatus().isSuccessful() && developResult.getMergeStatus().isSuccessful())
            {
                /*
                Change to the release branch to create the tag so that
                'git describe' will continue to work.
                See: https://github.com/nvie/gitflow/commit/aa93d2346f840e16a05c6546e1e22c1dddfbc997
                 */
            	git.checkout().setName(releaseBranch.getName()).call();

            /*
            try to tag the release
            in case a previous attempt to finish this release branch has failed,
            but the tag was successful, we skip it now
            */
                String tagName = gfConfig.getPrefixValue(JGitFlowConstants.PREFIXES.VERSIONTAG.configKey()) + releaseName;
                if (!GitHelper.tagExists(git, tagName))
                {
                    reporter.infoText(getCommandName(), "tagging release with name:" + tagName);
                    git.tag().setName(tagName).setMessage(getScmMessagePrefix() + message + getScmMessageSuffix()).call();
                }
            }

            if (push && masterResult.getMergeStatus().isSuccessful() && developResult.getMergeStatus().isSuccessful())
            {
                reporter.infoText(getCommandName(), "pushing changes to origin...");
                //push to develop
                reporter.infoText(getCommandName(), "pushing '" + gfConfig.getDevelop() + "'");
                RefSpec developSpec = new RefSpec(gfConfig.getDevelop());
                git.push().setRemote(Constants.DEFAULT_REMOTE_NAME).setRefSpecs(developSpec).call();
                

                //push to master
                reporter.infoText(getCommandName(), "pushing '" + gfConfig.getMaster() + "'");
                RefSpec masterSpec = new RefSpec(gfConfig.getMaster());
                git.push().setRemote(Constants.DEFAULT_REMOTE_NAME).setRefSpecs(masterSpec).call();

                if (!noTag)
                {
                    reporter.infoText(getCommandName(), "pushing tags");
                    git.push().setRemote(Constants.DEFAULT_REMOTE_NAME).setPushTags().call();
                }

                if (GitHelper.remoteBranchExists(git, prefixedReleaseName, reporter))
                {
                    reporter.infoText(getCommandName(), "pushing release branch");
                    RefSpec branchSpec = new RefSpec(prefixedReleaseName);
                    git.push().setRemote(Constants.DEFAULT_REMOTE_NAME).setRefSpecs(branchSpec).call();
                }
                
                runExtensionCommands(extension.afterPush());
            }

            if (!keepBranch && masterResult.getMergeStatus().isSuccessful() && developResult.getMergeStatus().isSuccessful())
            {
                reporter.infoText(getCommandName(), "deleting local release branch");
                git.checkout().setName(gfConfig.getDevelop()).call();
                git.branchDelete().setForce(true).setBranchNames(prefixedReleaseName).call();

                if (push && GitHelper.remoteBranchExists(git, prefixedReleaseName, reporter))
                {
                    reporter.infoText(getCommandName(), "pushing deleted release branch");
                    RefSpec deleteSpec = new RefSpec(":" + Constants.R_HEADS + prefixedReleaseName);
                    git.push().setRemote(Constants.DEFAULT_REMOTE_NAME).setRefSpecs(deleteSpec).call();
                }
            }

            reporter.infoText(getCommandName(), "checking out '" + gfConfig.getDevelop() + "'");
            git.checkout().setName(gfConfig.getDevelop()).call();

        }
        catch (GitAPIException e)
        {
            reporter.errorText(getCommandName(),e.getMessage());
            reporter.endCommand();
            throw new JGitFlowGitAPIException(e);
        }

        reporter.endCommand();
        
        runExtensionCommands(extension.after());
        return new ReleaseMergeResult(masterResult, developResult);
    }

    /**
     * Set whether to perform a git fetch of the remote branches before doing the merge
     *
     * @param fetch <code>true</code> to do the fetch, <code>false</code>(default) otherwise
     * @return {@code this}
     */
    public ReleaseFinishCommand setFetch(boolean fetch)
    {
        this.fetch = fetch;
        return this;
    }

    /**
     * Set the commit message for the tag creation
     *
     * @param message
     * @return {@code this}
     */
    public ReleaseFinishCommand setMessage(String message)
    {
        this.message = message;
        return this;
    }

    /**
     * Set whether to push the changes to the remote repository
     *
     * @param push <code>true</code> to do the push, <code>false</code>(default) otherwise
     * @return {@code this}
     */
    public ReleaseFinishCommand setPush(boolean push)
    {
        this.push = push;
        return this;
    }

    /**
     * Set whether to keep the local release branch after the merge
     *
     * @param keepBranch <code>true</code> to keep the branch, <code>false</code>(default) otherwise
     * @return {@code this}
     */
    public ReleaseFinishCommand setKeepBranch(boolean keepBranch)
    {
        this.keepBranch = keepBranch;
        return this;
    }

    /**
     * Set whether to turn off tagging
     *
     * @param noTag <code>true</code> to turn off tagging, <code>false</code>(default) otherwise
     * @return {@code this}
     */
    public ReleaseFinishCommand setNoTag(boolean noTag)
    {
        this.noTag = noTag;
        return this;
    }

    /**
     * Set whether to squash all commits into a single commit before the merge
     *
     * @param squash <code>true</code> to squash, <code>false</code>(default) otherwise
     * @return {@code this}
     */
    public ReleaseFinishCommand setSquash(boolean squash)
    {
        this.squash = squash;
        return this;
    }

    /**
     * Set whether to turn off merging
     *
     * @param noMerge <code>true</code> to turn off merging, <code>false</code>(default) otherwise
     * @return {@code this}
     */
    public ReleaseFinishCommand setNoMerge(boolean noMerge)
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
