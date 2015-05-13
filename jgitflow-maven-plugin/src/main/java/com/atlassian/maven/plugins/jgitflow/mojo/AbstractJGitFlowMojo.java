package com.atlassian.maven.plugins.jgitflow.mojo;

import java.io.File;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

import com.atlassian.maven.jgitflow.api.MavenJGitFlowExtension;
import com.atlassian.maven.plugins.jgitflow.FlowInitContext;

import com.google.common.base.Strings;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;

/**
 * @since version
 */
public abstract class AbstractJGitFlowMojo extends AbstractMojo
{
    @Component
    protected MavenProject project;

    @Component
    protected MavenSession session;

    @Component
    private Settings settings;

    @Parameter(defaultValue = "${basedir}", readonly = true, required = true)
    private File basedir;

    @Parameter(defaultValue = "${reactorProjects}", readonly = true, required = true)
    private List<MavenProject> reactorProjects;

    /**
     * This parameter permits you to configure branch and tag names, as shown in the following example:
     * 
     * <pre>
     * &lt;flowInitContext&gt;
     *   &lt;masterBranchName&gt;master&lt;/masterBranchName&gt;
     *   &lt;developBranchName&gt;develop&lt;/developBranchName&gt;
     *   &lt;featureBranchPrefix&gt;feature-&lt;/featureBranchPrefix&gt;
     *   &lt;releaseBranchPrefix&gt;release-&lt;/releaseBranchPrefix&gt;
     *   &lt;hotfixBranchPrefix&gt;hotfix-&lt;/hotfixBranchPrefix&gt;
     *   &lt;versionTagPrefix&gt;stable-&lt;/versionTagPrefix&gt;
     * &lt;/flowInitContext&gt;
     * </pre>
     * 
     */
    @Parameter(defaultValue = "${flowInitContext}")
    private FlowInitContext flowInitContext;

    /**
     * Whether to enable using an ssh-agent.
     */
    @Parameter(defaultValue = "false", property = "enableSshAgent")
    protected boolean enableSshAgent = false;

    /**
     * Whether to allow SNAPSHOT dependencies. Default is to fail when finding any SNAPSHOT.
     */
    @Parameter(defaultValue = "false", property = "allowSnapshots")
    protected boolean allowSnapshots = false;

    /**
     * Whether to allow untracked files when checking if the working tree is clean.
     */
    @Parameter(defaultValue = "false", property = "allowUntracked")
    protected boolean allowUntracked = false;

    /**
     * Whether to turn off all operations that require network access.
     * <p></p>
     * <p></p>
     * NOTE: THIS IS NOT CURRENTLY IMPLEMENTED!
     */
    @Parameter(property = "offline", defaultValue = "${settings.offline}")
    protected boolean offline;

    /**
     * Whether to turn off all operations access the remote git repository.
     * This will still allow network access to download dependencies and such.
     * <br />
     * <br />
     * NOTE: THIS IS NOT CURRENTLY IMPLEMENTED!
     */
    @Parameter(property = "localOnly", defaultValue = "false")
    protected boolean localOnly = false;

    /**
     * Default url to use if origin remote is not found in .git/config.
     * This is highly useful for CI servers that don't do proper clones.
     */
    @Parameter(property = "defaultOriginUrl", defaultValue = "")
    protected String defaultOriginUrl = "";

    /**
     * The message prefix to use for all SCM changes.
     */
    @Parameter(property = "scmCommentPrefix", defaultValue = "")
    protected String scmCommentPrefix = "";

    /**
     * The message suffix to use for all SCM changes.
     */
    @Parameter(property = "scmCommentSuffix", defaultValue = "")
    protected String scmCommentSuffix = "";

    /**
     * The username to use when using user/pass authentication
     */
    @Parameter(property = "username", defaultValue = "")
    protected String username = "";

    /**
     * The password to use when using user/pass authentication
     */
    @Parameter(property = "password", defaultValue = "")
    protected String password = "";

    /**
     * Whether to always overwrite the origin url in the .git/config file
     * This is useful to ensure the proper origin url is used in CI environments
     */
    @Parameter(defaultValue = "true", property = "alwaysUpdateOrigin")
    protected boolean alwaysUpdateOrigin = true;

    /**
     * Whether to pull the master branch when jgitflow is initialized
     */
    @Parameter(defaultValue = "false", property = "pullMaster")
    protected boolean pullMaster = false;

    /**
     * Whether to pull the develop branch when jgitflow is initialized
     */
    @Parameter(defaultValue = "false", property = "pullDevelop")
    protected boolean pullDevelop = false;

    Settings getSettings()
    {
        return settings;
    }

    protected final File getBasedir()
    {
        return basedir;
    }

    /**
     * Sets the base directory of the build.
     *
     * @param basedir The build's base directory, must not be {@code null}.
     */
    public void setBasedir(File basedir)
    {
        this.basedir = basedir;
    }

    /**
     * Gets the list of projects in the build reactor.
     *
     * @return The list of reactor project, never {@code null}.
     */
    public List<MavenProject> getReactorProjects()
    {
        return reactorProjects;
    }

    public FlowInitContext getFlowInitContext()
    {
        return flowInitContext;
    }

    public void setFlowInitContext(FlowInitContext flowInitContext)
    {
        this.flowInitContext = flowInitContext;
    }

    public boolean isRemoteAllowed()
    {
        return (!offline && !localOnly);
    }

    public MavenJGitFlowExtension getExtensionInstance(String classname) throws MojoExecutionException
    {
        if (Strings.isNullOrEmpty(classname))
        {
            return null;
        }

        try
        {
            Class<?> providerClass = Thread.currentThread().getContextClassLoader().loadClass(classname);
            Constructor ctr = providerClass.getConstructor();

            return (MavenJGitFlowExtension) ctr.newInstance();
        }
        catch (Exception e)
        {
            throw new MojoExecutionException("Unable to load maven jgitflow extension class '" + classname + "'", e);
        }
    }

    public ClassLoader getClassloader(String classpath)
    {
        List<String> pathList = Arrays.asList(classpath.split(File.pathSeparator));

        List<URL> urls = new ArrayList<URL>(pathList.size());
        for (String filename : pathList)
        {
            try
            {
                urls.add(new File(filename).toURL());
            }
            catch (MalformedURLException e)
            {
                //ignore
            }
        }

        return new URLClassLoader((URL[]) urls.toArray(new URL[urls.size()]), Thread.currentThread().getContextClassLoader());
    }

    protected String getClasspath() throws MojoExecutionException
    {
        Set<String> allPaths = new HashSet<String>();
        StringBuffer finalPath = new StringBuffer(File.pathSeparator + project.getBuild().getOutputDirectory());

        try
        {
            allPaths.addAll(project.getCompileClasspathElements());
            allPaths.addAll(project.getRuntimeClasspathElements());
            allPaths.addAll(project.getSystemClasspathElements());

            URL[] pluginUrls = ((URLClassLoader) Thread.currentThread().getContextClassLoader()).getURLs();
            for (URL pluginUrl : pluginUrls)
            {
                allPaths.add(new File(pluginUrl.getFile()).getPath());
            }

            for (String path : allPaths)
            {
                finalPath.append(File.pathSeparator);
                finalPath.append(path);
            }

            return finalPath.toString();
        }
        catch (DependencyResolutionRequiredException e)
        {
            throw new MojoExecutionException("Dependencies must be resolved", e);
        }
    }
}
