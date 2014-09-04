package com.atlassian.maven.plugins.jgitflow.provider;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.exception.JGitFlowException;

public interface JGitFlowProvider
{
    JGitFlow gitFlow() throws JGitFlowException;
}
