package com.atlassian.maven.jgitflow.api;

import com.atlassian.jgitflow.core.JGitFlowInfo;

/**
 * This interface is meant to be implemented by external projects that need to tie into the jgitflow-maven-plugin lifecycle.
 * For ease of use, implementors can just extend {@link com.atlassian.maven.jgitflow.api.NoopMavenJgitFlowExtension} and override what they need to
 */
public interface MavenJGitFlowExtension
{
    /**
     * Called when the version changes on "topic" branches.
     * These are release/hotfix/feature branches.
     * <p/>
     * This method is called AFTER the poms have been committed.
     * Any changes made to the project within this method will need to also be committed within this method.
     * A helper class is provided to make this easier. {@link com.atlassian.maven.jgitflow.api.JGitFlowCommitHelper}
     *
     * @throws MavenJGitFlowExtensionException
     */
    void onTopicBranchVersionChange(String newVersion, String oldVersion, JGitFlowInfo flow) throws MavenJGitFlowExtensionException;

    /**
     * Called when the version changes on the develop branch.
     * <p/>
     * This method is called AFTER the poms have been committed.
     * Any changes made to the project within this method will need to also be committed within this method.
     * A helper class is provided to make this easier. {@link com.atlassian.maven.jgitflow.api.JGitFlowCommitHelper}
     *
     * @throws MavenJGitFlowExtensionException
     */
    void onDevelopBranchVersionChange(String newVersion, String oldVersion, JGitFlowInfo flow) throws MavenJGitFlowExtensionException;

    /**
     * Called when the version changes on the master branch.
     * <p/>
     * This method is called AFTER the poms have been committed.
     * Any changes made to the project within this method will need to also be committed within this method.
     * A helper class is provided to make this easier. {@link com.atlassian.maven.jgitflow.api.JGitFlowCommitHelper}
     *
     * @throws MavenJGitFlowExtensionException
     */
    void onMasterBranchVersionChange(String newVersion, String oldVersion, JGitFlowInfo flow) throws MavenJGitFlowExtensionException;
}
