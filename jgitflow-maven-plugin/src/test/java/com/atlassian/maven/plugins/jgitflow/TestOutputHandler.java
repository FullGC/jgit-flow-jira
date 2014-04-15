package com.atlassian.maven.plugins.jgitflow;

import java.io.IOException;

import org.codehaus.plexus.components.interactivity.OutputHandler;

/**
 * @since version
 */
public class TestOutputHandler implements OutputHandler
{
    private final StringBuilder sb;

    public TestOutputHandler()
    {
        this.sb = new StringBuilder();
    }

    @Override
    public void write(String line) throws IOException
    {
        sb.append(line);
    }

    @Override
    public void writeLine(String line) throws IOException
    {
        sb.append(line).append("\n");
    }
    
    public String getValue()
    {
        return sb.toString();
    }
    
    public void clear()
    {
        sb.setLength(0);
    }
}
