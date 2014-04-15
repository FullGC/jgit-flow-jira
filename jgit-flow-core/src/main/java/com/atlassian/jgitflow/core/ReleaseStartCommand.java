package com.atlassian.jgitflow.core;

import java.io.IOException;

import com.atlassian.jgitflow.core.exception.*;
import com.atlassian.jgitflow.core.util.GitHelper;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.util.StringUtils;

import static com.atlassian.jgitflow.core.util.Preconditions.checkState;

/**
 * Start a release.
 * <p>
 * This will create a new branch using the release prefix and release name from the tip of develop
 * </p>
 * <p>
 * Examples (<code>flow</code> is a {@link com.atlassian.jgitflow.core.JGitFlow} instance):
 * <p>
 * Start a feature:
 *
 * <pre>
 * flow.releaseStart(&quot;1.0&quot;).call();
 * </pre>
 * <p>
 * Perform a fetch of develop before branching
 *
 * <pre>
 * flow.releaseStart(&quot;1.0&quot;).setFetch(true).call();
 * </pre>
 */
public class ReleaseStartCommand extends AbstractGitFlowCommand<Ref>
{
    private static final String SHORT_NAME = "release-start";
    
    //TODO: add ability to pass in start commit on ALL commands
    private final String releaseName;
    private boolean fetch;
    private boolean push;
    private RevCommit startCommit;
    private String startCommitString;

    /**
     * Create a new release start command instance.
     * <p></p>
     * An instance of this class is usually obtained by calling {@link com.atlassian.jgitflow.core.JGitFlow#releaseStart(String)}
     * @param releaseName The name of the release
     * @param git The git instance to use
     * @param gfConfig The GitFlowConfiguration to use
     * @param reporter
     */
    public ReleaseStartCommand(String releaseName, Git git, GitFlowConfiguration gfConfig, JGitFlowReporter reporter)
    {
        super(git, gfConfig, reporter);

        checkState(!StringUtils.isEmptyOrNull(releaseName));
        this.releaseName = releaseName;
        this.fetch = false;
        this.push = false;
        this.startCommit = null;
        this.startCommitString = null;
    }

    @Override
    public ReleaseStartCommand setAllowUntracked(boolean allow)
    {
        super.setAllowUntracked(allow);
        return this;
    }

    @Override
    public ReleaseStartCommand setScmMessagePrefix(String scmMessagePrefix)
    {
        super.setScmMessagePrefix(scmMessagePrefix);
        return this;
    }

    @Override
    public ReleaseStartCommand setScmMessageSuffix(String scmMessageSuffix)
    {
        super.setScmMessageSuffix(scmMessageSuffix);
        return this;
    }

    /**
     * 
     * @return A reference to the new release branch
     * @throws com.atlassian.jgitflow.core.exception.NotInitializedException
     * @throws com.atlassian.jgitflow.core.exception.JGitFlowGitAPIException
     * @throws com.atlassian.jgitflow.core.exception.ReleaseBranchExistsException
     * @throws com.atlassian.jgitflow.core.exception.DirtyWorkingTreeException
     * @throws com.atlassian.jgitflow.core.exception.JGitFlowIOException
     * @throws com.atlassian.jgitflow.core.exception.LocalBranchExistsException
     * @throws com.atlassian.jgitflow.core.exception.TagExistsException
     * @throws com.atlassian.jgitflow.core.exception.BranchOutOfDateException
     */
    @Override
    public Ref call() throws NotInitializedException, JGitFlowGitAPIException, ReleaseBranchExistsException, DirtyWorkingTreeException, JGitFlowIOException, LocalBranchExistsException, TagExistsException, BranchOutOfDateException, LocalBranchMissingException, RemoteBranchExistsException
    {
        String prefixedReleaseName = gfConfig.getPrefixValue(JGitFlowConstants.PREFIXES.RELEASE.configKey()) + releaseName;

        requireGitFlowInitialized();
        requireNoExistingReleaseBranches();
        requireCleanWorkingTree();
        requireLocalBranchAbsent(prefixedReleaseName);
        
        try
        {
            if (fetch)
            {
                RefSpec spec = new RefSpec("+" + Constants.R_HEADS + gfConfig.getDevelop() + ":" + Constants.R_REMOTES + "origin/" + gfConfig.getDevelop());
                git.fetch().setRefSpecs(spec).call();
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

            RevCommit latest = GitHelper.getLatestCommit(git, gfConfig.getDevelop());
            reporter.debugText(getCommandName(),"startPoint is: " + startPoint);
            reporter.debugText(getCommandName(),"latestCommit is: " + latest.getName());
            
            requireCommitOnBranch(startPoint, gfConfig.getDevelop());

            requireTagAbsent(gfConfig.getPrefixValue(JGitFlowConstants.PREFIXES.VERSIONTAG.configKey()) + releaseName);

            if (GitHelper.remoteBranchExists(git, gfConfig.getDevelop(), reporter))
            {
                requireLocalBranchNotBehindRemote(gfConfig.getDevelop());
            }

            Ref newBranch = git.checkout()
                      .setName(prefixedReleaseName)
                      .setCreateBranch(true)
                      .setStartPoint(startPoint)
                      .call();

            reporter.debugText(getCommandName(),"created branch: " + prefixedReleaseName);

            if (push)
            {
                requireRemoteBranchAbsent(prefixedReleaseName);
                reporter.debugText(getCommandName(),"pushing branch to origin using refspec: " + prefixedReleaseName);
                RefSpec branchSpec = new RefSpec(prefixedReleaseName);

                git.push().setRemote("origin").setRefSpecs(branchSpec).call();

                reporter.debugText(getCommandName(),"push complete");
                
                git.fetch().setRemote("origin").call();

                //setup tracking
                StoredConfig config = git.getRepository().getConfig();
                config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, prefixedReleaseName, ConfigConstants.CONFIG_KEY_REMOTE, "origin");
                config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, prefixedReleaseName, ConfigConstants.CONFIG_KEY_MERGE, Constants.R_HEADS + prefixedReleaseName);
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
        finally
        {
            reporter.flush();
        }
    }

    /**
     * Set whether to perform a git fetch of the remote develop branch before branching
     * @param fetch
     *              <code>true</code> to do the fetch, <code>false</code>(default) otherwise
     * @return {@code this}
     */
    public ReleaseStartCommand setFetch(boolean fetch)
    {
        this.fetch = fetch;
        return this;
    }

    /**
     * Set whether to push the changes to the remote repository
     *
     * @param push <code>true</code> to do the push, <code>false</code>(default) otherwise
     * @return {@code this}
     */
    public ReleaseStartCommand setPush(boolean push)
    {
        this.push = push;
        return this;
    }
    
    public ReleaseStartCommand setStartCommit(String commitId)
    {
        this.startCommitString = commitId;
        
        return this;
    }

    public ReleaseStartCommand setStartCommit(RevCommit commit)
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
