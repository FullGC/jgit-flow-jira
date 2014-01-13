package com.atlassian.maven.plugins.jgitflow;

import java.io.*;

import org.codehaus.plexus.components.interactivity.AbstractInputHandler;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Disposable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.eclipse.jgit.console.ConsoleText;

import jline.ConsoleReader;
import jline.UnixTerminal;

/**
 * @since version
 */
public class ConsoleInputHandler extends AbstractInputHandler implements Initializable, Disposable
{
    private final Console console = System.console();
    private ConsoleReader jline;

    public ConsoleInputHandler()
    {
        if(null == console)
        {
            try
            {
                this.jline = new ConsoleReader();
            }
            catch (IOException e)
            {
                this.jline = null;
            }
        }
    }

    public void setCygwinTerminal()
    {
        try
        {
            this.jline = new ConsoleReader(new FileInputStream(FileDescriptor.in)
                                            ,new PrintWriter(
                                                    new OutputStreamWriter(System.out,
                                                            System.getProperty("jline.WindowsTerminal.output.encoding", System.getProperty("file.encoding"))))
                                            ,null
                                            ,new UnixTerminal());
        }
        catch (IOException e)
        {
            this.jline = null;
        }
    }
    
    @Override
    public void dispose()
    {
        if(noConsole())
        {
            return;
        }

        if(null != console)
        {
            try
            {
                console.reader().close();
            }
            catch (IOException e)
            {
                getLogger().error( "Error closing input stream must be ignored", e );
            }
        }
    }

    @Override
    public void initialize() throws InitializationException
    {
        //do nothing
    }

    @Override
    public String readLine() throws IOException
    {
        if(null != console)
        {
            return console.readLine();
        }
        
        if(null != jline)
        {
            return jline.readLine();
        }
        
        return "";
    }

    @Override
    public String readPassword() throws IOException
    {
        if(null != console)
        {
            return new String(console.readPassword());
        }

        if(null != jline)
        {
            return jline.readLine(new Character('*'));
        }

        return "";
    }
    
    private boolean noConsole()
    {
        return (null == console && null == jline);
    }
}
