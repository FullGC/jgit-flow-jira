package com.atlassian.jgitflow.core.extension.impl;

import java.util.Collections;
import java.util.List;

import com.atlassian.jgitflow.core.extension.ExtensionCommand;
import com.atlassian.jgitflow.core.extension.FeatureFinishExtension;

public class EmptyFeatureFinishExtension implements FeatureFinishExtension
{
    @Override
    public List<ExtensionCommand> before()
    {
        return Collections.EMPTY_LIST;
    }
    
    @Override
    public List<ExtensionCommand> beforeFetch()
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<ExtensionCommand> afterFetch()
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<ExtensionCommand> beforeRebase()
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<ExtensionCommand> afterRebase()
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<ExtensionCommand> beforeDevelopCheckout()
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<ExtensionCommand> afterDevelopCheckout()
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<ExtensionCommand> beforeMerge()
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<ExtensionCommand> afterMerge()
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<ExtensionCommand> afterPush()
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<ExtensionCommand> after()
    {
        return Collections.EMPTY_LIST;
    }
}
