package com.atlassian.maven.plugins.jgitflow.mojo;

import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.MavenJGitFlowException;
import com.atlassian.maven.plugins.jgitflow.manager.FlowReleaseManager;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * @since version
 */
@Mojo(name = "feature-finish", aggregator = true)
public class FeatureFinishMojo extends AbstractJGitFlowMojo
{

    /**
     * Default name of the feature. This option is primarily useful when starting the goal in non-interactive mode.
     */
    @Parameter(property = "featureName", defaultValue = "")
    private String featureName = "";

    /**
     * Whether to keep the feature branch after finishing the release.
     * If set to false, the branch will be deleted.
     */
    @Parameter(defaultValue = "false", property = "keepBranch")
    private boolean keepBranch = false;

    /**
     * Whether to squash commits into a single commit before merging.
     */
    @Parameter(defaultValue = "false", property = "squash")
    private boolean squash = false;

    /**
     * Whether to rebase the feature branch before merging.
     */
    @Parameter(defaultValue = "false", property = "featureRebase")
    private boolean featureRebase = false;

    /**
     * Whether to append the feature name to the version on the feature branch.
     */
    @Parameter(defaultValue = "false", property = "enableFeatureVersions")
    private boolean enableFeatureVersions = false;

    /**
     * Whether to push feature branches to the remote upstream.
     */
    @Parameter(defaultValue = "false", property = "pushFeatures")
    private boolean pushFeatures = false;

    /**
     * Whether to use NO_FF as the merge strategy
     */
    @Parameter(defaultValue = "false", property = "suppressFastForward")
    private boolean suppressFastForward = false;

    /**
     * Whether to turn off merging changes from the feature branch to develop
     */
    @Parameter(defaultValue = "false", property = "noFeatureMerge")
    private boolean noFeatureMerge = false;

    /**
     * Whether to turn off project building. If true the project will NOT be built during feature finish
     */
    @Parameter(defaultValue = "false", property = "noFeatureBuild")
    private boolean noFeatureBuild = false;

    @Component(hint = "feature")
    FlowReleaseManager releaseManager;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        ReleaseContext ctx = new ReleaseContext(getBasedir());
        ctx.setInteractive(getSettings().isInteractiveMode())
           .setNoDeploy(true)
           .setEnableFeatureVersions(enableFeatureVersions)
           .setKeepBranch(keepBranch)
           .setSquash(squash)
           .setFeatureRebase(featureRebase)
           .setDefaultFeatureName(featureName)
           .setEnableSshAgent(enableSshAgent)
           .setAllowUntracked(allowUntracked)
           .setAllowSnapshots(allowSnapshots)
           .setPushFeatures(pushFeatures)
           .setAllowRemote(isRemoteAllowed())
           .setAlwaysUpdateOrigin(alwaysUpdateOrigin)
           .setNoFeatureMerge(noFeatureMerge)
           .setSuppressFastForward(suppressFastForward)
           .setNoBuild(noFeatureBuild)
           .setDefaultOriginUrl(defaultOriginUrl)
           .setScmCommentPrefix(scmCommentPrefix)
           .setScmCommentSuffix(scmCommentSuffix)
           .setUsername(username)
           .setPassword(password)
           .setPullMaster(pullMaster)
           .setPullDevelop(pullDevelop)
           .setUseReleaseProfile(false)
           .setFlowInitContext(getFlowInitContext().getJGitFlowContext());

        try
        {
            releaseManager.finish(ctx, getReactorProjects(), session);
        }
        catch (MavenJGitFlowException e)
        {
            throw new MojoExecutionException("Error finishing feature: " + e.getMessage(), e);
        }
    }
}
