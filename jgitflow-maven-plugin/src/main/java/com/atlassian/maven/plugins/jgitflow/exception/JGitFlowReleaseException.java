package com.atlassian.maven.plugins.jgitflow.exception;

/**
 * @since version
 */
public class JGitFlowReleaseException extends Exception
{
    public JGitFlowReleaseException()
    {
    }

    public JGitFlowReleaseException(String message)
    {
        super(message);
    }

    public JGitFlowReleaseException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public JGitFlowReleaseException(Throwable cause)
    {
        super(cause);
    }

}
