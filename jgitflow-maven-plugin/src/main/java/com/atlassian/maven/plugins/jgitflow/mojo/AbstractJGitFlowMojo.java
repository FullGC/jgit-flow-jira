package com.atlassian.maven.plugins.jgitflow.mojo;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

import com.atlassian.maven.jgitflow.api.MavenJGitFlowExtension;
import com.atlassian.maven.plugins.jgitflow.FlowInitContext;
import com.atlassian.maven.plugins.jgitflow.provider.ContextProvider;
import com.atlassian.maven.plugins.jgitflow.provider.MavenSessionProvider;
import com.atlassian.maven.plugins.jgitflow.provider.ReactorProjectsProvider;

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

    @Parameter(defaultValue = "${flowInitContext}")
    private FlowInitContext flowInitContext;

    @Parameter(defaultValue = "false", property = "enableSshAgent")
    protected boolean enableSshAgent = false;

    @Parameter(defaultValue = "false", property = "allowUntracked")
    protected boolean allowUntracked = false;

    @Parameter(property = "offline", defaultValue = "${settings.offline}")
    protected boolean offline;

    @Parameter(property = "localOnly", defaultValue = "false")
    protected boolean localOnly = false;

    @Parameter(property = "defaultOriginUrl", defaultValue = "")
    protected String defaultOriginUrl = "";

    @Parameter(property = "scmCommentPrefix", defaultValue = "")
    protected String scmCommentPrefix = "";

    @Parameter(property = "scmCommentSuffix", defaultValue = "")
    protected String scmCommentSuffix = "";

    @Parameter(property = "username", defaultValue = "")
    protected String username = "";

    @Parameter(property = "password", defaultValue = "")
    protected String password = "";

    @Parameter(defaultValue = "true", property = "alwaysUpdateOrigin")
    protected boolean alwaysUpdateOrigin = true;

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
     * @param basedir The build's base directory, must not be <code>null</code>.
     */
    public void setBasedir(File basedir)
    {
        this.basedir = basedir;
    }

    /**
     * Gets the list of projects in the build reactor.
     *
     * @return The list of reactor project, never <code>null</code>.
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
        if(Strings.isNullOrEmpty(classname))
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
            throw new MojoExecutionException("Unable to load maven jgitflow extension class '" + classname + "'",e);
        }
    }

    public ClassLoader getClassloader(String classpath)
    {
        List<String> pathList = Arrays.asList(classpath.split(File.pathSeparator));

        List<URL> urls = new ArrayList<URL>( pathList.size() );
        for ( String filename : pathList )
        {
            try
            {
                urls.add( new File( filename ).toURL() );
            }
            catch ( MalformedURLException e )
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

            URL[] pluginUrls = ((URLClassLoader)Thread.currentThread().getContextClassLoader()).getURLs();
            for(URL pluginUrl : pluginUrls)
            {
                allPaths.add(new File(pluginUrl.getFile()).getPath());
            }

            for(String path : allPaths) {
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
