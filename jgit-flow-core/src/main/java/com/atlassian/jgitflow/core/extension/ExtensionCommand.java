package com.atlassian.jgitflow.core.extension;

import com.atlassian.jgitflow.core.exception.JGitFlowExtensionException;

public interface ExtensionCommand
{
    void execute() throws JGitFlowExtensionException;
}
