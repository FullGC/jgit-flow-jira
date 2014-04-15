package com.atlassian.maven.plugins.jgitflow.manager;

import java.io.IOException;

import com.atlassian.maven.plugins.jgitflow.MavenJGitFlowConfiguration;

import org.eclipse.jgit.api.Git;

/**
 * @since version
 */
public interface MavenJGitFlowConfigManager
{
    public static final String CONFIG_FILENAME = ".maven-jgitflow";
    
    MavenJGitFlowConfiguration getConfiguration(Git git) throws IOException;
    void saveConfiguration(MavenJGitFlowConfiguration newConfig, Git git) throws IOException;
}
