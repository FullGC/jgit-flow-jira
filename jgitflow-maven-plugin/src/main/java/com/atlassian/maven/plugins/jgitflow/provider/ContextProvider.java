package com.atlassian.maven.plugins.jgitflow.provider;

import com.atlassian.maven.plugins.jgitflow.ReleaseContext;

public interface ContextProvider
{
    ReleaseContext getContext();

    void setContext(ReleaseContext context);
}
