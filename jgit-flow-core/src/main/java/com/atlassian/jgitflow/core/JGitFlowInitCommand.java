package com.atlassian.jgitflow.core;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

import com.atlassian.jgitflow.core.exception.AlreadyInitializedException;
import com.atlassian.jgitflow.core.exception.JGitFlowGitAPIException;
import com.atlassian.jgitflow.core.exception.JGitFlowIOException;
import com.atlassian.jgitflow.core.exception.SameBranchException;
import com.atlassian.jgitflow.core.util.GitHelper;

import com.google.common.base.Strings;

import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Initializes a project for use with git flow
 * <p/>
 * Examples:
 * <p/>
 * Initialize with the defaults or throw an exception if it's already initialized
 * <p/>
 * <pre>
 * JGitFlow flow = JGitFlow.init(new File(&quot;some dir&quot;));
 * </pre>
 * <p/>
 * Initialize with the defaults or return the instance if it's already initialized
 * <p/>
 * <pre>
 * JGitFlow flow = JGitFlow.getOrInit(new File(&quot;some dir&quot;));
 * </pre>
 * <p/>
 * Initialize with custom overrides or return the instance if it's already initialized
 * <p/>
 * <pre>
 * InitContext ctx = new InitContext();
 * ctx.setMaster("GA");
 *
 * JGitFlow flow = JGitFlow.getOrInit(new File(&quot;some dir&quot;), ctx);
 * </pre>
 * <p/>
 * Initialize with custom overrides replacing any existing configuration
 * <p/>
 * <pre>
 * InitContext ctx = new InitContext();
 * ctx.setMaster("GA");
 *
 * JGitFlow flow = JGitFlow.forceInit(new File(&quot;some dir&quot;), ctx);
 * </pre>
 */
public class JGitFlowInitCommand implements Callable<JGitFlow>
{
    private static final String SHORT_NAME = "init";
    private File directory;
    private boolean force;
    private InitContext context;
    JGitFlowReporter reporter;
    private String defaultOriginUrl;

    /**
     * Create a new init command instance.
     * <p></p>
     * An instance of this class is usually obtained by calling one of the static {@link com.atlassian.jgitflow.core.JGitFlow} init methods
     */
    public JGitFlowInitCommand()
    {
        this.force = false;
        this.reporter = new JGitFlowReporter();
    }

    /**
     * @return A {@link com.atlassian.jgitflow.core.JGitFlow} instance
     * @throws com.atlassian.jgitflow.core.exception.JGitFlowIOException
     * @throws com.atlassian.jgitflow.core.exception.JGitFlowGitAPIException
     * @throws com.atlassian.jgitflow.core.exception.AlreadyInitializedException
     * @throws com.atlassian.jgitflow.core.exception.SameBranchException
     */
    @Override
    public JGitFlow call() throws JGitFlowIOException, JGitFlowGitAPIException, AlreadyInitializedException, SameBranchException
    {

        Git git = null;

        reporter.debugCommandCall(SHORT_NAME);

        if (null == this.context)
        {
            this.context = new InitContext();
        }

        try
        {
            git = getOrInitGit(directory);
        }
        catch (IOException e)
        {
            reporter.errorText(SHORT_NAME, e.getMessage());
            reporter.endCommand();
            throw new JGitFlowIOException(e);
        }
        catch (GitAPIException e)
        {
            reporter.errorText(SHORT_NAME, e.getMessage());
            reporter.endCommand();
            throw new JGitFlowGitAPIException(e);
        }

        Repository repo = git.getRepository();
        GitFlowConfiguration gfConfig = new GitFlowConfiguration(git);

        RevWalk walk = null;
        try
        {
            String currentBranch = repo.getBranch();
            
            //set origin if we need to
            if (!Strings.isNullOrEmpty(defaultOriginUrl))
            {
                StoredConfig gitConfig = git.getRepository().getConfig();
                String originUrl = gitConfig.getString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME, "url");

                if (Strings.isNullOrEmpty(originUrl))
                {
                    String newOriginUrl = defaultOriginUrl;

                    if (defaultOriginUrl.startsWith("file://"))
                    {
                        File originFile = new File(defaultOriginUrl.substring(7));
                        newOriginUrl = "file://" + originFile.getCanonicalPath();
                    }
                    gitConfig.setString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME, "url", newOriginUrl);
                    gitConfig.setString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME, "fetch", "+refs/heads/*:refs/remotes/origin/*");
                    gitConfig.save();

                    gitConfig.load();
                    git.fetch().setRemote(Constants.DEFAULT_REMOTE_NAME).call();

                }
            }

            if (!force && gfConfig.gitFlowIsInitialized())
            {
                reporter.errorText(SHORT_NAME, "git flow is already initialized and force flag is false");
                reporter.endCommand();
                throw new AlreadyInitializedException("Already initialized for git flow.");
            }

            //First setup master
            if (gfConfig.hasMasterConfigured() && !force)
            {
                context.setMaster(gfConfig.getMaster());
            }

            //TODO: we should set an allowFetch flag and do a complete fetch before the local/remote checks if needed.
            //if no local master exists, but a remote does, check it out
            if (!GitHelper.localBranchExists(git, context.getMaster()) && GitHelper.remoteBranchExists(git, context.getMaster(), reporter))
            {
                reporter.debugText(SHORT_NAME, "creating new local '" + context.getMaster() + "' branch from origin '" + context.getMaster() + "'");
                git.branchCreate()
                   .setName(context.getMaster())
                   .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.SET_UPSTREAM)
                   .setStartPoint("origin/" + context.getMaster())
                   .call();
            }


            gfConfig.setMaster(context.getMaster());


            //now setup develop
            if (gfConfig.hasDevelopConfigured() && !force)
            {
                context.setDevelop(gfConfig.getDevelop());
            }

            if (context.getDevelop().equals(context.getMaster()))
            {
                reporter.errorText(SHORT_NAME, "master and develop branches configured with the same name: [" + context.getMaster() + "]");
                reporter.endCommand();
                throw new SameBranchException("master and develop branches cannot be the same: [" + context.getMaster() + "]");
            }

            reporter.infoText(SHORT_NAME, "setting develop in config to '" + context.getDevelop() + "'");
            gfConfig.setDevelop(context.getDevelop());

            //Creation of HEAD
            walk = new RevWalk(repo);
            ObjectId masterBranch = repo.resolve(Constants.R_HEADS + context.getMaster());
            RevCommit masterCommit = null;

            if (null != masterBranch)
            {
                try
                {
                    masterCommit = walk.parseCommit(masterBranch);
                }
                catch (MissingObjectException e)
                {
                    //ignore
                }
                catch (IncorrectObjectTypeException e)
                {
                    //ignore
                }
            }

            if (null == masterCommit)
            {
                reporter.debugText(SHORT_NAME, "no commits found on '" + context.getMaster() + "'. creating initial commit.");
                RefUpdate refUpdate = repo.getRefDatabase().newUpdate(Constants.HEAD, false);
                refUpdate.setForceUpdate(true);
                refUpdate.link(Constants.R_HEADS + context.getMaster());

                git.commit().setMessage("Initial Commit").call();
                
            }

            //creation of develop
            if (!GitHelper.localBranchExists(git, context.getDevelop()))
            {
                if (GitHelper.remoteBranchExists(git, context.getDevelop(), reporter))
                {
                    reporter.debugText(SHORT_NAME, "creating new local '" + context.getDevelop() + "' branch from origin '" + context.getDevelop() + "'");
                    git.branchCreate()
                       .setName(context.getDevelop())
                       .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.SET_UPSTREAM)
                       .setStartPoint("origin/" + context.getDevelop())
                       .call();
                }
                else
                {
                    reporter.debugText(SHORT_NAME, "creating new local '" + context.getDevelop() + "' branch without origin");
                    git.branchCreate()
                       .setName(context.getDevelop())
                       .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.NOTRACK)
                       .call();
                }
            }

            //don't do this as it breaks feature finish
            //git.checkout().setName(context.getDevelop()).call();

            //setup prefixes
            for (String prefixName : gfConfig.getPrefixNames())
            {
                if (gfConfig.hasPrefixConfigured(prefixName) && !force)
                {
                    context.setPrefix(prefixName, gfConfig.getPrefixValue(prefixName));
                }

                gfConfig.setPrefix(prefixName, context.getPrefix(prefixName));
            }

            if(!Strings.isNullOrEmpty(currentBranch) && !currentBranch.equals(repo.getBranch()))
            {
                git.checkout().setName(currentBranch).call();
            }

        }
        catch (IOException e)
        {
            reporter.errorText(SHORT_NAME, e.getMessage());
            reporter.flush();
            throw new JGitFlowIOException(e);
        }
        catch (GitAPIException e)
        {
            reporter.errorText(SHORT_NAME, e.getMessage());
            reporter.flush();
            throw new JGitFlowGitAPIException(e);
        }
        catch (ConfigInvalidException e)
        {
            reporter.errorText(SHORT_NAME, e.getMessage());
            reporter.flush();
            throw new JGitFlowGitAPIException(e);
        }
        finally
        {
            if (null != walk)
            {
                walk.release();
            }

            reporter.endCommand();
        }

        return new JGitFlow(git, gfConfig, reporter);
    }


    /**
     * Sets the project root folder
     *
     * @param directory
     * @return {@code this}
     */
    public JGitFlowInitCommand setDirectory(File directory)
    {
        this.directory = directory;
        return this;
    }

    /**
     * Set the initialization context
     *
     * @param context
     * @return {@code this}
     */
    public JGitFlowInitCommand setInitContext(InitContext context)
    {
        this.context = context;
        return this;
    }

    /**
     * Whether to override the current configuration
     *
     * @param force <code>true</code> to override, <code>false</code>(default) otherwise
     * @return {@code this}
     */
    public JGitFlowInitCommand setForce(boolean force)
    {
        this.force = force;
        return this;
    }

    public JGitFlowInitCommand setDefaultOriginUrl(String defaultOriginUrl)
    {
        this.defaultOriginUrl = defaultOriginUrl;
        return this;
    }

    private Git getOrInitGit(File folder) throws IOException, GitAPIException
    {
        reporter.debugMethod(SHORT_NAME, "getOrInitGit");
        Git gitRepo;
        try
        {
            reporter.debugText(SHORT_NAME, "looking for git folder in " + folder.getAbsolutePath());
            RepositoryBuilder rb = new RepositoryBuilder()
                    .readEnvironment()
                    .findGitDir(folder);

            File gitDir = rb.getGitDir();

            if (null != gitDir)
            {
                reporter.debugText(SHORT_NAME, "found existing git folder");
                gitRepo = Git.open(gitDir);
            }
            else
            {
                reporter.debugText(SHORT_NAME, "no git folder found, initializing");
                gitRepo = Git.init().setDirectory(folder).call();
            }
        }
        catch (RepositoryNotFoundException e)
        {
            reporter.debugText(SHORT_NAME, "no git folder found, initializing");
            gitRepo = Git.init().setDirectory(folder).call();
        }

        reporter.endMethod();
        return gitRepo;
    }


}
