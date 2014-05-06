package com.atlassian.maven.plugins.jgitflow.provider;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.exception.*;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;

public interface JGitFlowProvider
{
    JGitFlow gitFlow(ReleaseContext ctx) throws JGitFlowException;
}
