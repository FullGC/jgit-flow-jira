package com.atlassian.maven.plugins.jgitflow.helper;

import java.io.IOException;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.JGitFlowReporter;
import com.atlassian.jgitflow.core.exception.JGitFlowException;
import com.atlassian.maven.plugins.jgitflow.PrettyPrompter;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.MavenJGitFlowException;
import com.atlassian.maven.plugins.jgitflow.provider.ContextProvider;
import com.atlassian.maven.plugins.jgitflow.provider.JGitFlowProvider;
import com.atlassian.maven.plugins.jgitflow.util.ConsoleCredentialsProvider;
import com.atlassian.maven.plugins.jgitflow.util.SshCredentialsProvider;

import com.google.common.base.Strings;

import org.apache.maven.execution.RuntimeInformation;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.CoreConfig;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

@Component(role = JGitFlowSetupHelper.class)
public class DefaultJGitFlowSetupHelper extends AbstractLogEnabled implements JGitFlowSetupHelper
{
    private static String OS = System.getProperty("os.name").toLowerCase();
    private static boolean isWindows = (OS.indexOf("win") >= 0);
    private static boolean isCygwin = (isWindows && !Strings.isNullOrEmpty(System.getenv("TERM")));

    private boolean sshAgentConfigured = false;
    private boolean sshConsoleInstalled = false;
    private boolean headerWritten = false;

    @Requirement
    private RuntimeInformation runtimeInformation;

    @Requirement
    private PrettyPrompter prompter;

    @Requirement
    private ContextProvider contextProvider;

    @Requirement
    private JGitFlowProvider jGitFlowProvider;

    @Override
    public void runCommonSetup() throws MavenJGitFlowException
    {
        try
        {
            fixCygwinIfNeeded();
            writeReportHeader();
            warnCoreAutoCrlf();
        }
        catch (Exception e)
        {
            throw new MavenJGitFlowException("Error running common setup tasks", e);
        }
    }

    public void warnCoreAutoCrlf() throws MavenJGitFlowException
    {
        try
        {
            JGitFlow flow = jGitFlowProvider.gitFlow();
            StoredConfig config = flow.git().getRepository().getConfig();

            CoreConfig.AutoCRLF autoCRLF = config.getEnum(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_AUTOCRLF, CoreConfig.AutoCRLF.FALSE);

            if (autoCRLF.TRUE.equals(autoCRLF))
            {
                getLogger().warn("..oo00 ---- WARNING ---- 00oo..");
                getLogger().warn("core.autocrlf is set to true but is NOT supported by JGit or JGitFlow!");
                getLogger().warn("00oo.. you have been warned 00oo..");
            }
        }
        catch (Exception e)
        {
            throw new MavenJGitFlowException("Error checking for core.autocrlf", e);
        }
    }

    @Override
    public void fixCygwinIfNeeded() throws MavenJGitFlowException
    {
        if (isCygwin)
        {
            getLogger().info("detected cygwin:");
            try
            {
                getLogger().info("    - turning off filemode...");

                StoredConfig config = jGitFlowProvider.gitFlow().git().getRepository().getConfig();
                config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_FILEMODE, false);
                config.save();
                config.load();
            }
            catch (IOException e)
            {
                throw new MavenJGitFlowException("error configuring filemode for cygwin", e);
            }
            catch (ConfigInvalidException e)
            {
                throw new MavenJGitFlowException("error configuring filemode for cygwin", e);
            }
            catch (JGitFlowException e)
            {
                throw new MavenJGitFlowException("error configuring filemode for cygwin", e);
            }

            getLogger().info("    - fixing maven prompter...");
            prompter.setCygwinTerminal();
        }
    }

    @Override
    public void setupCredentialProviders() throws JGitFlowException
    {
        ReleaseContext ctx = contextProvider.getContext();

        if (!ctx.isRemoteAllowed())
        {
            return;
        }

        if (!sshConsoleInstalled)
        {
            sshConsoleInstalled = setupUserPasswordCredentialsProvider();
        }

        if (!sshAgentConfigured)
        {
            sshAgentConfigured = setupSshCredentialsProvider();
        }
    }

    protected void writeReportHeader() throws JGitFlowException
    {
        ReleaseContext ctx = contextProvider.getContext();

        if (!headerWritten)
        {
            String mvnVersion = runtimeInformation.getApplicationVersion().toString();
            Package mvnFlowPkg = getClass().getPackage();
            String mvnFlowVersion = mvnFlowPkg.getImplementationVersion();

            String shortName = getClass().getSimpleName();

            JGitFlowReporter.get().debugText(shortName, "# Maven JGitFlow Plugin")
                .debugText(shortName, JGitFlowReporter.P)
                .debugText(shortName, "  ## Configuration")
                .debugText(shortName, JGitFlowReporter.EOL)
                .debugText(shortName, "    Maven Version: " + mvnVersion)
                .debugText(shortName, JGitFlowReporter.EOL)
                .debugText(shortName, "    Maven JGitFlow Plugin Version: " + mvnFlowVersion)
                .debugText(shortName, JGitFlowReporter.EOL)
                .debugText(shortName, "    args: " + ctx.getArgs())
                .debugText(shortName, "    base dir: " + ctx.getBaseDir().getAbsolutePath())
                .debugText(shortName, "    default development version: " + ctx.getDefaultDevelopmentVersion())
                .debugText(shortName, "    default feature name: " + ctx.getDefaultFeatureName())
                .debugText(shortName, "    default release version: " + ctx.getDefaultReleaseVersion())
                .debugText(shortName, "    release branch version suffix: " + ctx.getReleaseBranchVersionSuffix())
                .debugText(shortName, "    tag message: " + ctx.getTagMessage())
                .debugText(shortName, "    allow snapshots: " + ctx.isAllowSnapshots())
                .debugText(shortName, "    auto version submodules: " + ctx.isAutoVersionSubmodules())
                .debugText(shortName, "    enable feature versions: " + ctx.isEnableFeatureVersions())
                .debugText(shortName, "    enable ssh agent: " + ctx.isEnableSshAgent())
                .debugText(shortName, "    feature rebase: " + ctx.isFeatureRebase())
                .debugText(shortName, "    interactive: " + ctx.isInteractive())
                .debugText(shortName, "    keep branch: " + ctx.isKeepBranch())
                .debugText(shortName, "    no build: " + ctx.isNoBuild())
                .debugText(shortName, "    no deploy: " + ctx.isNoDeploy())
                .debugText(shortName, "    no tag: " + ctx.isNoTag())
                .debugText(shortName, "    pushFeatures: " + ctx.isPushFeatures())
                .debugText(shortName, "    pushReleases: " + ctx.isPushReleases())
                .debugText(shortName, "    pushHotfixes: " + ctx.isPushHotfixes())
                .debugText(shortName, "    squash: " + ctx.isSquash())
                .debugText(shortName, "    update dependencies: " + ctx.isUpdateDependencies())
                .debugText(shortName, "    use release profile: " + ctx.isUseReleaseProfile())
                .debugText(shortName, JGitFlowReporter.HR);

            JGitFlowReporter.get().flush();
            this.headerWritten = true;
        }
    }

    private boolean setupUserPasswordCredentialsProvider() throws JGitFlowException
    {
        ReleaseContext ctx = contextProvider.getContext();
        
        if (!Strings.isNullOrEmpty(ctx.getPassword()) && !Strings.isNullOrEmpty(ctx.getUsername()))
        {
            JGitFlowReporter.get().debugText(getClass().getSimpleName(), "using provided username and password");
            CredentialsProvider.setDefault(new UsernamePasswordCredentialsProvider(ctx.getUsername(), ctx.getPassword()));
        }
        else if (null != System.console())
        {
            JGitFlowReporter.get().debugText(getClass().getSimpleName(), "installing ssh console credentials provider");
            CredentialsProvider.setDefault(new ConsoleCredentialsProvider(prompter));
            return true;
        }

        return false;
    }

    private boolean setupSshCredentialsProvider() throws JGitFlowException
    {
        ReleaseContext ctx = contextProvider.getContext();
        
        if (ctx.isEnableSshAgent())
        {
            JGitFlowReporter.get().debugText(getClass().getSimpleName(), "installing ssh-agent credentials provider");
            SshSessionFactory.setInstance(new SshCredentialsProvider(prompter));
            return true;
        }

        return false;
    }
}
