package com.atlassian.maven.plugins.jgitflow.provider;

import javax.inject.Singleton;

import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.provider.ContextProvider;

import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;

@Component(role = ContextProvider.class)
public class DefaultContextProvider implements ContextProvider
{
    private static final DefaultContextProvider INSTANCE = new DefaultContextProvider();
    private ReleaseContext context;

    @Override
    public ReleaseContext getContext()
    {
        return INSTANCE.context;
    }

    @Override
    public void setContext(ReleaseContext context)
    {
        INSTANCE.context = context;
    }
}
