package com.atlassian.maven.plugins.jgitflow.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import com.atlassian.maven.plugins.jgitflow.PrettyPrompter;
import com.jcraft.jsch.IdentityRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.agentproxy.AgentProxy;
import com.jcraft.jsch.agentproxy.AgentProxyException;
import com.jcraft.jsch.agentproxy.Connector;
import com.jcraft.jsch.agentproxy.RemoteIdentityRepository;
import com.jcraft.jsch.agentproxy.USocketFactory;
import com.jcraft.jsch.agentproxy.connector.SSHAgentConnector;
import com.jcraft.jsch.agentproxy.usocket.JNAUSocketFactory;

import org.codehaus.plexus.logging.Logger;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.util.FS;

/**
 * @author a500148
 */
public class SshCredentialsProvider extends JschConfigSessionFactory
{
    private PrettyPrompter prompter;
    private final Logger logger;
    
    public SshCredentialsProvider(PrettyPrompter prompter, Logger logger)
    {
        this.prompter = prompter;
        this.logger = logger;
    }

    @Override
    protected void configure(OpenSshConfig.Host hc, Session session)
    {
        session.setConfig("StrictHostKeyChecking", "no");
        session.setUserInfo(new SshUserInfo(prompter));
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
            logger.warn("Error connecting to ssh-agent: " + e.getMessage());
        }

        if (null == con)
        {
        	logger.info("Use default SSH connector");
            jsch = super.createDefaultJSch(fs);

            return jsch;
        }
        else
        {
        	logger.info("Use ssh-agent connector");
            jsch = new JSch();
            jsch.setConfig("PreferredAuthentications", "publickey");
            IdentityRepository irepo = new RemoteIdentityRepository(con);
            jsch.setIdentityRepository(irepo);

            //why these in super is private, I don't know
            knownHosts(jsch, fs);
            AgentProxy ap = new AgentProxy(con);
            identities(jsch, fs, ap);
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

    private void identities(final JSch sch, FS fs, AgentProxy ap)
    {
    	// only add identities if there are none there yet
    	if (ap.getIdentities().length > 0) {
    		logger.info("ssh-agent already has some identities which are reused");
    		return;
    	}
    	logger.info("ssh-agent does not yet have an identity, adding the default OpenSSH ones");
    	
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
}
