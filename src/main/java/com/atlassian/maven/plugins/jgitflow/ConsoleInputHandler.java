package com.atlassian.maven.plugins.jgitflow;

import java.io.Console;
import java.io.IOException;

import org.codehaus.plexus.components.interactivity.AbstractInputHandler;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Disposable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.eclipse.jgit.console.ConsoleText;

/**
 * @since version
 */
public class ConsoleInputHandler extends AbstractInputHandler implements Initializable, Disposable
{
    private final Console console = System.console();


    @Override
    public void dispose()
    {
        if(null == console)
        {
            return;
        }
        
        try
        {
            console.reader().close();
        }
        catch (IOException e)
        {
            getLogger().error( "Error closing input stream must be ignored", e );
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
        if(null == console)
        {
            return "";
        }
        
        return console.readLine();
    }

    @Override
    public String readPassword() throws IOException
    {
        if(null == console)
        {
            return "";
        }
        
        return new String(console.readPassword());
    }
}
