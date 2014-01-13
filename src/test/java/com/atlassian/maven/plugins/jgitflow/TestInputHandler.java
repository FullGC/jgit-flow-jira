package com.atlassian.maven.plugins.jgitflow;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.codehaus.plexus.components.interactivity.InputHandler;
import com.atlassian.maven.plugins.jgitflow.ConsoleInputHandler;

/**
 * @since version
 */
public class TestInputHandler extends ConsoleInputHandler implements InputHandler
{
    private final StringBuilder sb;

    public TestInputHandler()
    {
        this.sb = new StringBuilder();
    }

    @Override
    public String readLine() throws IOException
    {
        String val = sb.toString();
        sb.setLength(0);
        
        return val;
    }

    @Override
    public String readPassword() throws IOException
    {
        return readLine();
    }

    @Override
    public List readMultipleLines() throws IOException
    {
        return new ArrayList();
    }
    
    public void setResponse(String response)
    {
        sb.setLength(0);
        sb.append(response);
    }
}
