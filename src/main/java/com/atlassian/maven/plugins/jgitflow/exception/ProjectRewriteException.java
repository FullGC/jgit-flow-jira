package com.atlassian.maven.plugins.jgitflow.exception;

/**
 * @since version
 */
public class ProjectRewriteException extends Exception
{
    public ProjectRewriteException()
    {
    }

    public ProjectRewriteException(String message)
    {
        super(message);
    }

    public ProjectRewriteException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public ProjectRewriteException(Throwable cause)
    {
        super(cause);
    }
}
