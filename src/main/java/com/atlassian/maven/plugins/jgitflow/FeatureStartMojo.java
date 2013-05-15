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

    
    @Component(hint = "feature")
    FlowReleaseManager releaseManager;
    
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        ReleaseContext ctx = new ReleaseContext(getBasedir());
        ctx.setInteractive(getSettings().isInteractiveMode())
                .setDefaultFeatureName(featureName)
                .setFlowInitContext(getFlowInitContext().getJGitFlowContext());

        try
        {
            releaseManager.start(ctx,getReactorProjects());
        }
        catch (JGitFlowReleaseException e)
        {
            throw new MojoExecutionException("Error starting release: " + e.getMessage(),e);
        }
    }
}
