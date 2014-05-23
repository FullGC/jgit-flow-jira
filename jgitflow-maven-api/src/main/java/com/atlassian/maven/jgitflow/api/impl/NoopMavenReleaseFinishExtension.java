package com.atlassian.maven.jgitflow.api.impl;

import com.atlassian.jgitflow.core.JGitFlowInfo;
import com.atlassian.maven.jgitflow.api.MavenReleaseFinishExtension;
import com.atlassian.maven.jgitflow.api.exception.MavenJGitFlowExtensionException;

public class NoopMavenReleaseFinishExtension implements MavenReleaseFinishExtension
{
    @Override
    public void onMasterBranchVersionChange(String newVersion, String oldVersion, JGitFlowInfo flow) throws MavenJGitFlowExtensionException
    {
        
    }

    @Override
    public void onTopicBranchVersionChange(String newVersion, String oldVersion, JGitFlowInfo flow) throws MavenJGitFlowExtensionException
    {

    }
}
