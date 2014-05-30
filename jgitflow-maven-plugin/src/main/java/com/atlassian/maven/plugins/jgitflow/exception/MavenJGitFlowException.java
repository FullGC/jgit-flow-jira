package com.atlassian.maven.plugins.jgitflow.exception;

/**
 * @since version
 */
public class MavenJGitFlowException extends Exception
{
    public MavenJGitFlowException()
    {
    }

    public MavenJGitFlowException(String message)
    {
        super(message);
    }

    public MavenJGitFlowException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public MavenJGitFlowException(Throwable cause)
    {
        super(cause);
    }

}
