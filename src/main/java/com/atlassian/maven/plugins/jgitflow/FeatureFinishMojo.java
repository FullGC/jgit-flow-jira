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
@Mojo(name = "feature-finish", aggregator = true)
public class FeatureFinishMojo extends AbstractJGitFlowMojo
{
    
    /**
     * Default name of the feature. This option is primarily useful when starting the goal in non-interactive mode.
     *
     */
    @Parameter( property = "featureName", defaultValue = "")
    private String featureName;
    
    @Parameter( defaultValue = "false", property = "keepBranch" )
    private boolean keepBranch = false;

    @Parameter( defaultValue = "false", property = "squash" )
    private boolean squash = false;

    @Parameter( defaultValue = "false", property = "featureRebase" )
    private boolean featureRebase = false;

    @Parameter( defaultValue = "false", property = "enableFeatureVersions" )
    private boolean enableFeatureVersions = false;

    @Parameter( defaultValue = "false", property = "pushFeatures" )
    private boolean pushFeatures = false;

    @Parameter( defaultValue = "false", property = "noFeatureMerge" )
    private boolean noFeatureMerge = false;

    @Parameter( defaultValue = "false", property = "noFeatureBuild" )
    private boolean noFeatureBuild = false;

    @Parameter( defaultValue = "false", property = "pullMaster" )
    private boolean pullMaster = false;

    @Parameter( defaultValue = "false", property = "pullDevelop" )
    private boolean pullDevelop = false;
    
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
                .setPushFeatures(pushFeatures)
                .setAllowRemote(isRemoteAllowed())
                .setNoFeatureMerge(noFeatureMerge)
                .setNoBuild(noFeatureBuild)
                .setDefaultOriginUrl(defaultOriginUrl)
                .setScmCommentPrefix(scmCommentPrefix)
                .setPullMaster(pullMaster)
                .setPullDevelop(pullDevelop)
                .setFlowInitContext(getFlowInitContext().getJGitFlowContext());

        try
        {
            releaseManager.finish(ctx, getReactorProjects(), session);
        }
        catch (JGitFlowReleaseException e)
        {
            throw new MojoExecutionException("Error finishing feature: " + e.getMessage(),e);
        }
    }
}
