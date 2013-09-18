package com.atlassian.maven.plugins.jgitflow;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import com.atlassian.jgitflow.core.JGitFlowReporter;

import com.jcraft.jsch.IdentityRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.agentproxy.AgentProxyException;
import com.jcraft.jsch.agentproxy.Connector;
import com.jcraft.jsch.agentproxy.RemoteIdentityRepository;
import com.jcraft.jsch.agentproxy.USocketFactory;
import com.jcraft.jsch.agentproxy.connector.SSHAgentConnector;
import com.jcraft.jsch.agentproxy.usocket.JNAUSocketFactory;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.eclipse.jgit.console.ConsoleCredentialsProvider;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.util.FS;

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

    @Parameter( property = "defaultOriginUrl", defaultValue = "")
    protected String defaultOriginUrl;
    
    @Parameter(property = "scmCommentPrefix", defaultValue = "")
    protected String scmCommentPrefix = "";

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
}
