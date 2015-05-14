package com.atlassian.maven.plugins.jgitflow.mojo;

import com.atlassian.maven.jgitflow.api.MavenReleaseFinishExtension;
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
@Mojo(name = "release-finish", aggregator = true, requiresDependencyResolution = ResolutionScope.TEST)
public class ReleaseFinishMojo extends AbstractJGitFlowMojo
{
    /**
     * Whether to automatically assign submodules the parent version. If set to false, the user will be prompted for the
     * version of each submodules.
     */
    @Parameter(defaultValue = "false", property = "autoVersionSubmodules")
    private boolean autoVersionSubmodules = false;

    /**
     * Whether to push release branches to the remote upstream.
     */
    @Parameter(defaultValue = "false", property = "pushReleases")
    private boolean pushReleases = false;

    /**
     * Whether to turn off maven deployment. If false the "deploy" goal is called. If true the "install" goal is called
     */
    @Parameter(defaultValue = "false", property = "noDeploy")
    private boolean noDeploy = false;

    /**
     * Whether to keep the release branch after finishing the release.
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
     * Whether to turn off tagging the release in git.
     */
    @Parameter(defaultValue = "false", property = "noTag")
    private boolean noTag = false;

    /**
     * Whether to turn off project building. If true the project will NOT be built during release finish
     */
    @Parameter(defaultValue = "false", property = "noReleaseBuild")
    private boolean noReleaseBuild = false;

    /**
     * Whether to turn off merging changes from the release branch to master and develop
     */
    @Parameter(defaultValue = "false", property = "noReleaseMerge")
    private boolean noReleaseMerge = false;

    /**
     * Whether to use the release profile that adds sources and javadocs to the released artifact, if appropriate. 
     * If set to true, the plugin sets the property "performRelease" to true, which activates the profile "release-profile", which is inherited from the super pom.
     */
    @Parameter(defaultValue = "true", property = "useReleaseProfile")
    private boolean useReleaseProfile = true;

    /**
     * Whether, for modules which refer to each other within the same multi-module build, to update dependencies version to the release version.
     */
    @Parameter(defaultValue = "true", property = "updateDependencies")
    private boolean updateDependencies = true;

    /**
     * Commit message to use when tagging the release.
     * 
     * If not set, the default message is "tagging release ${version}".
     */
    @Parameter(property = "tagMessage", defaultValue = "")
    private String tagMessage = "";

    /**
     * Suffix to append to versions on the release branch.
     */
    @Parameter(property = "releaseBranchVersionSuffix", defaultValue = "")
    private String releaseBranchVersionSuffix = "";

    /**
     * A FQCN of a compatible release finish extension.
     * Extensions are used to run custom code at various points in the jgitflow lifecycle.
     *
     * More documentation on using extensions will be available in the future
     */
    @Parameter(defaultValue = "")
    private String releaseFinishExtension = "";

    /**
     * ALL of the explicit arguments to be passed to the internal maven build.
     * If not set, the plugin will use the args from the initial maven build.
     */
    @Parameter(property = "arguments", defaultValue = "")
    private String arguments = "";

    /**
     * The space-separated list of gaols to run when doing a maven deploy
     */
    @Parameter(property = "goals", defaultValue = "clean deploy")
    private String goals = "";

    @Component(hint = "release")
    FlowReleaseManager releaseManager;

    /**
     * If set to true, only the first parent/project version will be used across all version updates
     * @see <a href="https://ecosystem.atlassian.net/browse/MJF-204">https://ecosystem.atlassian.net/browse/MJF-204</a>
     */
    @Parameter(defaultValue = "false", property = "consistentProjectVersions")
    protected boolean consistentProjectVersions = false;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        ClassLoader oldClassloader = Thread.currentThread().getContextClassLoader();

        Thread.currentThread().setContextClassLoader(getClassloader(getClasspath()));

        MavenReleaseFinishExtension extensionObject = (MavenReleaseFinishExtension) getExtensionInstance(releaseFinishExtension);

        ReleaseContext ctx = new ReleaseContext(getBasedir());
        ctx.setInteractive(getSettings().isInteractiveMode())
           .setAutoVersionSubmodules(autoVersionSubmodules)
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
           .setAlwaysUpdateOrigin(alwaysUpdateOrigin)
           .setDefaultOriginUrl(defaultOriginUrl)
           .setScmCommentPrefix(scmCommentPrefix)
           .setScmCommentSuffix(scmCommentSuffix)
           .setUsername(username)
           .setPassword(password)
           .setPullMaster(pullMaster)
           .setPullDevelop(pullDevelop)
           .setArgs(arguments)
           .setGoals(goals)
           .setReleaseFinishExtension(extensionObject)
           .setFlowInitContext(getFlowInitContext().getJGitFlowContext())
                .setEol(eol)
           .setConsistentProjectVersions(consistentProjectVersions);

        try
        {
            releaseManager.finish(ctx, getReactorProjects(), session);
        }
        catch (MavenJGitFlowException e)
        {
            throw new MojoExecutionException("Error finishing release: " + e.getMessage(), e);
        }
        finally
        {
            Thread.currentThread().setContextClassLoader(oldClassloader);
        }
    }
}
