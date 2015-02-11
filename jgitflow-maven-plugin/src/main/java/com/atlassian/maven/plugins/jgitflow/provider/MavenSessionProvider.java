package com.atlassian.maven.plugins.jgitflow.provider;

import org.apache.maven.execution.MavenSession;

public interface MavenSessionProvider
{
    MavenSession getSession();

    void setSession(MavenSession session);
}
