package com.atlassian.maven.plugins.jgitflow.provider;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.exception.JGitFlowException;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;

import org.codehaus.plexus.component.annotations.Component;

@Component(role = JGitFlowProvider.class)
public class DefaultJGitFlowProvider implements JGitFlowProvider
{
    private JGitFlow jgitFlow;

    @Override
    public JGitFlow gitFlow(ReleaseContext ctx) throws JGitFlowException
    {
        if (null == jgitFlow)
        {
            jgitFlow = JGitFlow.forceInit(ctx.getBaseDir(), ctx.getFlowInitContext(), ctx.getDefaultOriginUrl());
        }

        return jgitFlow;
    }
}
