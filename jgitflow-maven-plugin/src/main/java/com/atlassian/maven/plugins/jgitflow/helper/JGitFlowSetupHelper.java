package com.atlassian.maven.plugins.jgitflow.helper;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.JGitFlowReporter;
import com.atlassian.jgitflow.core.exception.JGitFlowException;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.JGitFlowReleaseException;

public interface JGitFlowSetupHelper
{
    void runCommonSetup() throws JGitFlowReleaseException;
    void fixCygwinIfNeeded() throws JGitFlowReleaseException;
    void ensureOrigin() throws JGitFlowReleaseException;
    void setupCredentialProviders() throws JGitFlowException;
}
