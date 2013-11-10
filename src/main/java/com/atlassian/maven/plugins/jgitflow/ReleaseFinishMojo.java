package com.atlassian.maven.plugins.jgitflow;

import com.atlassian.maven.plugins.jgitflow.exception.JGitFlowReleaseException;
import com.atlassian.maven.plugins.jgitflow.manager.FlowReleaseManager;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * @since version
 */
@Mojo(name = "release-finish", aggregator = true)
public class ReleaseFinishMojo extends AbstractJGitFlowMojo
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
    @Parameter( property = "developmentVersion" )
    private String developmentVersion;

    @Parameter( defaultValue = "false", property = "pushReleases" )
    private boolean pushReleases = false;

    @Parameter( defaultValue = "false", property = "noDeploy" )
    private boolean noDeploy = false;

    @Parameter( defaultValue = "false", property = "keepBranch" )
    private boolean keepBranch = false;

    @Parameter( defaultValue = "false", property = "squash" )
    private boolean squash = false;

    @Parameter( defaultValue = "false", property = "noTag" )
    private boolean noTag = false;

    @Parameter( defaultValue = "false", property = "noReleaseBuild" )
    private boolean noReleaseBuild = false;

    @Parameter( defaultValue = "false", property = "noReleaseMerge" )
    private boolean noReleaseMerge = false;

    @Parameter( defaultValue = "true", property = "useReleaseProfile" )
    private boolean useReleaseProfile = true;

    @Parameter( defaultValue = "false", property = "pullMaster" )
    private boolean pullMaster = false;

    @Parameter( defaultValue = "false", property = "pullDevelop" )
    private boolean pullDevelop = false;
    
    @Parameter( defaultValue = "true", property = "updateDependencies" )
    private boolean updateDependencies = true;

    @Parameter( property = "tagMessage" )
    private String tagMessage;

    @Parameter( property = "releaseBranchVersionSuffix", defaultValue = "")
    private String releaseBranchVersionSuffix;
    
    @Component(hint = "release")
    FlowReleaseManager releaseManager;
    
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        ReleaseContext ctx = new ReleaseContext(getBasedir());
        ctx.setInteractive(getSettings().isInteractiveMode())
                .setAutoVersionSubmodules(autoVersionSubmodules)
                .setDefaultDevelopmentVersion(developmentVersion)
                .setReleaseBranchVersionSuffix(releaseBranchVersionSuffix)
                .setPushReleases(pushReleases)
                .setKeepBranch(keepBranch)
                .setSquash(squash)
                .setNoTag(noTag)
                .setNoBuild(noReleaseBuild)
                .setNoDeploy(noDeploy)
                .setUseReleaseProfile(useReleaseProfile)
                .setTagMessage(tagMessage)
                .setUpdateDependencies(updateDependencies)
                .setAllowSnapshots(allowSnapshots)
                .setEnableSshAgent(enableSshAgent)
                .setAllowUntracked(allowUntracked)
                .setNoReleaseMerge(noReleaseMerge)
                .setAllowRemote(isRemoteAllowed())
                .setDefaultOriginUrl(defaultOriginUrl)
                .setScmCommentPrefix(scmCommentPrefix)
                .setPullMaster(pullMaster)
                .setPullDevelop(pullDevelop)
                .setFlowInitContext(getFlowInitContext().getJGitFlowContext());

        try
        {
            releaseManager.finish(ctx,getReactorProjects(),session);
        }
        catch (JGitFlowReleaseException e)
        {
            throw new MojoExecutionException("Error finishing release: " + e.getMessage(),e);
        }
    }
}
