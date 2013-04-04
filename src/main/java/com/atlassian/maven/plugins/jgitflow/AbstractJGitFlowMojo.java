package com.atlassian.maven.plugins.jgitflow;

import java.io.File;
import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
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

    @Parameter( defaultValue = "${basedir}", readonly = true, required = true )
    private File basedir;
    
    @Parameter( defaultValue = "${reactorProjects}", readonly = true, required = true )
    private List<MavenProject> reactorProjects;

    @Parameter ( defaultValue = "${maven.repo.local}" )
    private File localRepoDirectory;

    @Parameter
    private FlowInitContext flowInitContext;

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
    public void setBasedir( File basedir )
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
}
