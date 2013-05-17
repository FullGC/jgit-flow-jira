package com.atlassian.maven.plugins.jgitflow.exception;

/**
 * @since version
 */
public class ReactorReloadException extends Exception
{
    public ReactorReloadException()
    {
    }

    public ReactorReloadException(String message)
    {
        super(message);
    }

    public ReactorReloadException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public ReactorReloadException(Throwable cause)
    {
        super(cause);
    }
}
