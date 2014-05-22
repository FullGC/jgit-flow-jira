package com.atlassian.maven.jgitflow.api;

import com.atlassian.jgitflow.core.JGitFlowInfo;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;

/**
 * A helper class to make committing all changes from extensions easy peasy
 */
public class JGitFlowCommitHelper
{
    /**
     * Commits ALL changes to the current branch
     *
     * @param flow          the JGitFlow instance to use
     * @param commitMessage The message for the commit
     * @throws MavenJGitFlowExtensionException
     */
    public static void commitAllChanges(JGitFlowInfo flow, String commitMessage) throws MavenJGitFlowExtensionException
    {
        try
        {
            Git git = flow.git();

            Status status = git.status().call();
            if (!status.isClean())
            {
                git.add().addFilepattern(".").call();
                git.commit().setMessage(commitMessage).call();
            }
        }
        catch (GitAPIException e)
        {
            throw new MavenJGitFlowExtensionException("error committing changes: " + e.getMessage(), e);
        }
    }
}
