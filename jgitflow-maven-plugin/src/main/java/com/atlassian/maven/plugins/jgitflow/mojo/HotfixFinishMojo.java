package com.atlassian.maven.plugins.jgitflow.mojo;

import com.atlassian.maven.jgitflow.api.MavenHotfixFinishExtension;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.MavenJGitFlowException;
import com.atlassian.maven.plugins.jgitflow.manager.FlowReleaseManager;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * @since version
 */
@Mojo(name = "hotfix-finish", aggregator = true, requiresDependencyResolution = ResolutionScope.TEST)
public class HotfixFinishMojo extends AbstractJGitFlowMojo
{
    /**
     * Whether to automatically assign submodules the parent version. If set to false, the user will be prompted for the
     * version of each submodules.
     *
     */
    @Parameter( defaultValue = "false", property = "autoVersionSubmodules" )
    private boolean autoVersionSubmodules = false;

    /**
     * Whether to allow SNAPSHOT dependencies. Default is to fail when finding any SNAPSHOT.
     *
     */
    @Parameter( defaultValue = "false", property = "allowSnapshots" )
    private boolean allowSnapshots = false;
    
    /**
     * Default version to use for new local working copy.
     *
     */
    @Parameter( property = "developmentVersion" , defaultValue = "")
    private String developmentVersion = "";

    @Parameter( defaultValue = "false", property = "pushHotfixes" )
    private boolean pushHotfixes = false;

    @Parameter( defaultValue = "false", property = "noDeploy" )
    private boolean noDeploy = false;

    @Parameter( defaultValue = "false", property = "keepBranch" )
    private boolean keepBranch = false;

    @Parameter( defaultValue = "false", property = "squash" )
    private boolean squash = false;

    @Parameter( defaultValue = "false", property = "noTag" )
    private boolean noTag = false;

    @Parameter( defaultValue = "false", property = "noHotfixBuild" )
    private boolean noHotfixBuild = false;

    @Parameter( defaultValue = "true", property = "useReleaseProfile" )
    private boolean useReleaseProfile = true;

    @Parameter( defaultValue = "true", property = "updateDependencies" )
    private boolean updateDependencies = true;
    
    @Parameter( property = "tagMessage" , defaultValue = "")
    private String tagMessage = "";

    @Parameter( defaultValue = "false", property = "pullMaster" )
    private boolean pullMaster = false;

    @Parameter( defaultValue = "false", property = "pullDevelop" )
    private boolean pullDevelop = false;

    @Component(hint = "hotfix")
    FlowReleaseManager releaseManager;

    @Parameter(defaultValue = "")
    private String hotfixFinishExtension = "";

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        ClassLoader oldClassloader = Thread.currentThread().getContextClassLoader();

        Thread.currentThread().setContextClassLoader(getClassloader(getClasspath()));
        
        MavenHotfixFinishExtension extensionObject = (MavenHotfixFinishExtension) getExtensionInstance(hotfixFinishExtension);
        
        ReleaseContext ctx = new ReleaseContext(getBasedir());
        ctx.setInteractive(getSettings().isInteractiveMode())
           .setAutoVersionSubmodules(autoVersionSubmodules)
           .setAllowSnapshots(allowSnapshots)
           .setDefaultDevelopmentVersion(developmentVersion)
           .setPushHotfixes(pushHotfixes)
           .setKeepBranch(keepBranch)
           .setSquash(squash)
           .setNoTag(noTag)
           .setNoDeploy(noDeploy)
           .setUseReleaseProfile(useReleaseProfile)
           .setTagMessage(tagMessage)
           .setUpdateDependencies(updateDependencies)
           .setEnableSshAgent(enableSshAgent)
           .setAllowUntracked(allowUntracked)
           .setAllowRemote(isRemoteAllowed())
           .setAlwaysUpdateOrigin(alwaysUpdateOrigin)
           .setNoBuild(noHotfixBuild)
           .setDefaultOriginUrl(defaultOriginUrl)
           .setScmCommentPrefix(scmCommentPrefix)
           .setScmCommentSuffix(scmCommentSuffix)
           .setUsername(username)
           .setPassword(password)
           .setPullMaster(pullMaster)
           .setPullDevelop(pullDevelop)
           .setFlowInitContext(getFlowInitContext().getJGitFlowContext());

        try
        {
            releaseManager.finish(ctx, getReactorProjects(),session);
        }
        catch (MavenJGitFlowException e)
        {
            throw new MojoExecutionException("Error finishing hotfix: " + e.getMessage(),e);
        }
        finally
        {
            Thread.currentThread().setContextClassLoader(oldClassloader);
        }
    }
}
