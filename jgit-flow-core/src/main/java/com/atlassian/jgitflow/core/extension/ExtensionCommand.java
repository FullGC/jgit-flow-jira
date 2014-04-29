package com.atlassian.jgitflow.core.extension;

import com.atlassian.jgitflow.core.GitFlowConfiguration;
import com.atlassian.jgitflow.core.JGitFlowReporter;
import com.atlassian.jgitflow.core.exception.JGitFlowExtensionException;

import org.eclipse.jgit.api.Git;

public interface ExtensionCommand
{
    void execute(GitFlowConfiguration configuration, Git git, JGitFlowReporter reporter) throws JGitFlowExtensionException;

    ExtensionFailStrategy failStrategy();
}
