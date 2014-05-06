package com.atlassian.maven.plugins.jgitflow.helper;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.JGitFlowReporter;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.JGitFlowReleaseException;

public interface JGitFlowSetupHelper
{
    void runCommonSetup(JGitFlow flow, ReleaseContext ctx) throws JGitFlowReleaseException;
    void fixCygwinIfNeeded(JGitFlow flow) throws JGitFlowReleaseException;
    void ensureOrigin(String defaultRemote, boolean alwaysUpdateOrigin, JGitFlow flow) throws JGitFlowReleaseException;
    void setupCredentialProviders(ReleaseContext ctx, JGitFlowReporter reporter);
}
