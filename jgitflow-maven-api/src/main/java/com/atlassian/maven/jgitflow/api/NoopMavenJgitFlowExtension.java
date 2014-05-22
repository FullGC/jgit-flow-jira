package com.atlassian.maven.jgitflow.api;

import com.atlassian.jgitflow.core.JGitFlowInfo;

public class NoopMavenJgitFlowExtension implements MavenJGitFlowExtension
{

    @Override
    public void onTopicBranchVersionChange(String newVersion, String oldVersion, JGitFlowInfo flow) throws MavenJGitFlowExtensionException
    {
        //do nothing, override if needed
    }

    @Override
    public void onDevelopBranchVersionChange(String newVersion, String oldVersion, JGitFlowInfo flow) throws MavenJGitFlowExtensionException
    {
        //do nothing, override if needed
    }

    @Override
    public void onMasterBranchVersionChange(String newVersion, String oldVersion, JGitFlowInfo flow) throws MavenJGitFlowExtensionException
    {
        //do nothing, override if needed
    }
}
