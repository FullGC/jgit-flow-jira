package com.atlassian.maven.plugins.jgitflow.provider;

import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.component.annotations.Component;

@Component(role = MavenSessionProvider.class)
public class DefaultMavenSessionProvider implements MavenSessionProvider
{
    private static final DefaultMavenSessionProvider INSTANCE = new DefaultMavenSessionProvider();

    private MavenSession session;

    @Override
    public MavenSession getSession()
    {
        return INSTANCE.session;
    }

    @Override
    public void setSession(MavenSession session)
    {
        INSTANCE.session = session;
    }
}
