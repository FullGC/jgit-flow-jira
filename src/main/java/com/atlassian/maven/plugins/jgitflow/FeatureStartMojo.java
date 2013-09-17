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
@Mojo(name = "feature-start", aggregator = true)
public class FeatureStartMojo extends AbstractJGitFlowMojo
{
    
    /**
     * Default name of the feature. This option is primarily useful when starting the goal in non-interactive mode.
     *
     */
    @Parameter( property = "featureName" )
    private String featureName;

    @Parameter( defaultValue = "false", property = "enableFeatureVersions" )
    private boolean enableFeatureVersions = false;

    @Parameter( defaultValue = "false", property = "pushFeatures" )
    private boolean pushFeatures = false;

    @Parameter( property = "startCommit", defaultValue = "")
    private String startCommit;
    
    @Component(hint = "feature")
    FlowReleaseManager releaseManager;
    
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        ReleaseContext ctx = new ReleaseContext(getBasedir());
        ctx.setInteractive(getSettings().isInteractiveMode())
                .setDefaultFeatureName(featureName)
                .setEnableFeatureVersions(enableFeatureVersions)
                .setEnableSshAgent(enableSshAgent)
                .setAllowUntracked(allowUntracked)
                .setPushFeatures(pushFeatures)
                .setStartCommit(startCommit)
                .setAllowRemote(isRemoteAllowed())
                .setDefaultOriginUrl(defaultOriginUrl)
                .setScmCommentPrefix(scmCommentPrefix)
                .setFlowInitContext(getFlowInitContext().getJGitFlowContext());

        try
        {
            releaseManager.start(ctx,getReactorProjects(),session);
        }
        catch (JGitFlowReleaseException e)
        {
            throw new MojoExecutionException("Error starting feature: " + e.getMessage(),e);
        }
    }
}
