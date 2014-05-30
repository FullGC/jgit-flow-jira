package com.atlassian.maven.plugins.jgitflow.extension;

import com.atlassian.maven.jgitflow.api.MavenJGitFlowExtension;

public interface ExternalInitializingExtension
{
    void init(MavenJGitFlowExtension externalExtension);
}
