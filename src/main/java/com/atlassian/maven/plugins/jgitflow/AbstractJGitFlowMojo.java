package com.atlassian.maven.plugins.jgitflow;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

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

    @Parameter(defaultValue = "false", property = "enableSshPass")
    private boolean enableSshPass = false;

    private boolean sshAgentConfigured = false;

    protected void setupSshAgentIfNeeded()
    {
        if (enableSshPass && !sshAgentConfigured)
        {
            if (null != System.console())
            {
                ConsoleCredentialsProvider.install();
            }

            JschConfigSessionFactory sessionFactory = new JschConfigSessionFactory()
            {
                @Override
                protected void configure(OpenSshConfig.Host hc, Session session)
                {
                    session.setConfig("StrictHostKeyChecking", "false");
                }

                @Override
                protected JSch createDefaultJSch(FS fs) throws JSchException
                {
                    Connector con = null;
                    JSch jsch = null;

                    try
                    {
                        if (SSHAgentConnector.isConnectorAvailable())
                        {
                            USocketFactory usf = new JNAUSocketFactory();
                            con = new SSHAgentConnector(usf);

                        }
                    }
                    catch (AgentProxyException e)
                    {
                        System.out.println(e.getMessage());
                    }

                    if (null == con)
                    {
                        jsch = super.createDefaultJSch(fs);

                        return jsch;
                    }
                    else
                    {
                        jsch = new JSch();
                        jsch.setConfig("PreferredAuthentications", "publickey");
                        IdentityRepository irepo = new RemoteIdentityRepository(con);
                        jsch.setIdentityRepository(irepo);

                        //why these in super is private, I don't know
                        knownHosts(jsch, fs);
                        identities(jsch, fs);
                        return jsch;
                    }
                }

                //copied from super class
                private void knownHosts(final JSch sch, FS fs) throws JSchException
                {
                    final File home = fs.userHome();
                    if (home == null)
                    { return; }
                    final File known_hosts = new File(new File(home, ".ssh"), "known_hosts");
                    try
                    {
                        final FileInputStream in = new FileInputStream(known_hosts);
                        try
                        {
                            sch.setKnownHosts(in);
                        }
                        finally
                        {
                            in.close();
                        }
                    }
                    catch (FileNotFoundException none)
                    {
                        // Oh well. They don't have a known hosts in home.
                    }
                    catch (IOException err)
                    {
                        // Oh well. They don't have a known hosts in home.
                    }
                }

                private void identities(final JSch sch, FS fs)
                {
                    final File home = fs.userHome();
                    if (home == null)
                    { return; }
                    final File sshdir = new File(home, ".ssh");
                    if (sshdir.isDirectory())
                    {
                        loadIdentity(sch, new File(sshdir, "identity"));
                        loadIdentity(sch, new File(sshdir, "id_rsa"));
                        loadIdentity(sch, new File(sshdir, "id_dsa"));
                    }
                }

                private void loadIdentity(final JSch sch, final File priv)
                {
                    if (priv.isFile())
                    {
                        try
                        {
                            sch.addIdentity(priv.getAbsolutePath());
                        }
                        catch (JSchException e)
                        {
                            // Instead, pretend the key doesn't exist.
                        }
                    }
                }
            };

            SshSessionFactory.setInstance(sessionFactory);

            sshAgentConfigured = true;
        }
    }

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
}
