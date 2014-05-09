package com.atlassian.maven.plugins.jgitflow.helper;

import com.atlassian.jgitflow.core.exception.JGitFlowException;
import com.atlassian.maven.plugins.jgitflow.exception.MavenJGitFlowException;

public interface JGitFlowSetupHelper
{
    void runCommonSetup() throws MavenJGitFlowException;
    void fixCygwinIfNeeded() throws MavenJGitFlowException;
    void ensureOrigin() throws MavenJGitFlowException;
    void setupCredentialProviders() throws JGitFlowException;
}
