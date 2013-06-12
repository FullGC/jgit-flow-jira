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
@Mojo(name = "hotfix-finish", aggregator = true)
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
    @Parameter( property = "developmentVersion" )
    private String developmentVersion;

    /**
     * Will or not push changes to the upstream repository.
     * <code>true</code> by default
     */
    @Parameter( defaultValue = "true", property = "pushChanges" )
    private boolean pushChanges = true;

    @Parameter( defaultValue = "false", property = "noDeploy" )
    private boolean noDeploy = false;

    @Parameter( defaultValue = "false", property = "keepBranch" )
    private boolean keepBranch = false;

    @Parameter( defaultValue = "false", property = "squash" )
    private boolean squash = false;

    @Parameter( defaultValue = "false", property = "noTag" )
    private boolean noTag = false;

    @Parameter( defaultValue = "true", property = "useReleaseProfile" )
    private boolean useReleaseProfile = true;

    @Parameter( defaultValue = "true", property = "updateDependencies" )
    private boolean updateDependencies;
    
    @Parameter( property = "tagMessage" )
    private String tagMessage;

    @Component(hint = "hotfix")
    FlowReleaseManager releaseManager;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        setupSshAgentIfNeeded();

        ReleaseContext ctx = new ReleaseContext(getBasedir());
        ctx.setInteractive(getSettings().isInteractiveMode())
           .setAutoVersionSubmodules(autoVersionSubmodules)
           .setAllowSnapshots(allowSnapshots)
           .setDefaultDevelopmentVersion(developmentVersion)
           .setPush(pushChanges)
           .setKeepBranch(keepBranch)
           .setSquash(squash)
           .setNoTag(noTag)
           .setNoDeploy(noDeploy)
           .setUseReleaseProfile(useReleaseProfile)
           .setTagMessage(tagMessage)
           .setUpdateDependencies(updateDependencies)
           .setFlowInitContext(getFlowInitContext().getJGitFlowContext());

        try
        {
            releaseManager.finish(ctx,getReactorProjects(),session);
        }
        catch (JGitFlowReleaseException e)
        {
            throw new MojoExecutionException("Error finishing hotfix: " + e.getMessage(),e);
        }
    }
}
