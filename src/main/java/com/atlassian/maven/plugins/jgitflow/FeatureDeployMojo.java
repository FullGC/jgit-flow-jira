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
@Mojo(name = "feature-deploy", aggregator = true)
public class FeatureDeployMojo extends AbstractJGitFlowMojo
{
    
    /**
     * Default name of the feature. This option is primarily useful when starting the goal in non-interactive mode.
     *
     */
    @Parameter( property = "featureName", defaultValue = "")
    private String featureName;

    @Parameter( property = "goals", defaultValue = "")
    private String goals;

    @Parameter(property = "buildNumber")
    private String buildNumber;

    @Component(hint = "feature")
    FlowReleaseManager releaseManager;
    
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        ReleaseContext ctx = new ReleaseContext(getBasedir());
        ctx.setInteractive(getSettings().isInteractiveMode())
                .setNoDeploy(false)
                .setEnableFeatureVersions(true)
                .setFlowInitContext(getFlowInitContext().getJGitFlowContext());

        try
        {
            releaseManager.deploy(ctx, getReactorProjects(), session, buildNumber, goals);
        }
        catch (JGitFlowReleaseException e)
        {
            throw new MojoExecutionException("Error finishing feature: " + e.getMessage(),e);
        }
    }
}
