package com.atlassian.maven.jgitflow.api.exception;

public class MavenJGitFlowExtensionException extends Exception
{
    public MavenJGitFlowExtensionException()
    {
    }

    public MavenJGitFlowExtensionException(String message)
    {
        super(message);
    }

    public MavenJGitFlowExtensionException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public MavenJGitFlowExtensionException(Throwable cause)
    {
        super(cause);
    }
}
