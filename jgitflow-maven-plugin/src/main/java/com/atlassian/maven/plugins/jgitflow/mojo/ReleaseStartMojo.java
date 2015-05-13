package com.atlassian.maven.plugins.jgitflow.mojo;

import com.atlassian.maven.jgitflow.api.MavenReleaseStartExtension;
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
@Mojo(name = "release-start", aggregator = true, requiresDependencyResolution = ResolutionScope.TEST)
public class ReleaseStartMojo extends AbstractJGitFlowMojo
{

    /**
     * Whether to automatically assign submodules the parent version. If set to false, the user will be prompted for the
     * version of each submodules.
     */
    @Parameter(defaultValue = "false", property = "autoVersionSubmodules")
    private boolean autoVersionSubmodules = false;

    /**
     * Default version to use when preparing a release
     */
    @Parameter(property = "releaseVersion", defaultValue = "")
    private String releaseVersion = "";

    /**
     * Default version to use for new local working copy.
     */
    @Parameter(property = "developmentVersion", defaultValue = "")
    private String developmentVersion = "";

    /**
     * Suffix to append to versions on the release branch.
     */
    @Parameter(property = "releaseBranchVersionSuffix", defaultValue = "")
    private String releaseBranchVersionSuffix = "";

    /**
     * Whether, for modules which refer to each other within the same multi-module build, to update dependencies version to the release version.
     */
    @Parameter(defaultValue = "true", property = "updateDependencies")
    private boolean updateDependencies = true;

    /**
     * Whether to push release branches to the remote upstream.
     */
    @Parameter(defaultValue = "false", property = "pushReleases")
    private boolean pushReleases = false;

    @Component(hint = "release")
    FlowReleaseManager releaseManager;

    /**
     * A SHA, short SHA, or branch name to use as the starting point for the new branch
     */
    @Parameter(property = "startCommit", defaultValue = "")
    private String startCommit = "";

    /**
     * A FQCN of a compatible release start extension.
     * Extensions are used to run custom code at various points in the jgitflow lifecycle.
     * 
     * More documentation on using extensions will be available in the future
     */
    @Parameter(defaultValue = "")
    private String releaseStartExtension = "";

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        ClassLoader oldClassloader = Thread.currentThread().getContextClassLoader();

        Thread.currentThread().setContextClassLoader(getClassloader(getClasspath()));

        MavenReleaseStartExtension extensionObject = (MavenReleaseStartExtension) getExtensionInstance(releaseStartExtension);

        ReleaseContext ctx = new ReleaseContext(getBasedir());
        ctx.setAutoVersionSubmodules(autoVersionSubmodules)
           .setInteractive(getSettings().isInteractiveMode())
           .setDefaultReleaseVersion(releaseVersion)
           .setDefaultDevelopmentVersion(developmentVersion)
           .setReleaseBranchVersionSuffix(releaseBranchVersionSuffix)
           .setAllowSnapshots(allowSnapshots)
           .setUpdateDependencies(updateDependencies)
           .setEnableSshAgent(enableSshAgent)
           .setAllowUntracked(allowUntracked)
           .setPushReleases(pushReleases)
           .setStartCommit(startCommit)
           .setAllowRemote(isRemoteAllowed())
           .setDefaultOriginUrl(defaultOriginUrl)
           .setAlwaysUpdateOrigin(alwaysUpdateOrigin)
           .setPullMaster(pullMaster)
           .setPullDevelop(pullDevelop)
           .setScmCommentPrefix(scmCommentPrefix)
           .setScmCommentSuffix(scmCommentSuffix)
           .setUsername(username)
           .setPassword(password)
           .setReleaseStartExtension(extensionObject)
                .setEol(eol)
           .setFlowInitContext(getFlowInitContext().getJGitFlowContext());

        try
        {
            releaseManager.start(ctx, getReactorProjects(), session);
        }
        catch (MavenJGitFlowException e)
        {
            throw new MojoExecutionException("Error starting release: " + e.getMessage(), e);
        }
        finally
        {
            Thread.currentThread().setContextClassLoader(oldClassloader);
        }
    }
}
