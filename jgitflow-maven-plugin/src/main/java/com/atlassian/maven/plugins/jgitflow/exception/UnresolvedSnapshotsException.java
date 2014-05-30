package com.atlassian.maven.plugins.jgitflow.exception;

/**
 * @since version
 */
public class UnresolvedSnapshotsException extends MavenJGitFlowException
{
    public UnresolvedSnapshotsException()
    {
    }

    public UnresolvedSnapshotsException(String message)
    {
        super(message);
    }

    public UnresolvedSnapshotsException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public UnresolvedSnapshotsException(Throwable cause)
    {
        super(cause);
    }
}
