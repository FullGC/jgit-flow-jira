package com.atlassian.maven.plugins.jgitflow.provider;

import com.atlassian.maven.plugins.jgitflow.ReleaseContext;

import org.codehaus.plexus.component.annotations.Component;

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
