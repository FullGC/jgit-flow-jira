package com.atlassian.maven.plugins.jgitflow.extension.command.external;

import com.atlassian.jgitflow.core.JGitFlowInfo;
import com.atlassian.maven.jgitflow.api.MavenJGitFlowExtension;
import com.atlassian.maven.jgitflow.api.exception.MavenJGitFlowExtensionException;

public interface ExternalCommand
{
    void execute(MavenJGitFlowExtension extension, String newVersion, String oldVersion, JGitFlowInfo flow) throws MavenJGitFlowExtensionException;

    String getOldVersion() throws MavenJGitFlowExtensionException;

    String getNewVersion() throws MavenJGitFlowExtensionException;
}
