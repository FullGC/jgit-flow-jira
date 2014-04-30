package com.atlassian.jgitflow.core.extension.impl;

import java.util.Collections;
import java.util.List;

import com.atlassian.jgitflow.core.extension.ExtensionCommand;
import com.atlassian.jgitflow.core.extension.ReleaseFinishExtension;

public class EmptyReleaseFinishExtension implements ReleaseFinishExtension
{

    @Override
    public Iterable<ExtensionCommand> beforeFetch()
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public Iterable<ExtensionCommand> afterFetch()
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public Iterable<ExtensionCommand> beforeMasterCheckout()
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public Iterable<ExtensionCommand> afterMasterCheckout()
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public Iterable<ExtensionCommand> beforeMasterMerge()
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public Iterable<ExtensionCommand> afterMasterMerge()
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public Iterable<ExtensionCommand> beforeDevelopCheckout()
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public Iterable<ExtensionCommand> afterDevelopCheckout()
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public Iterable<ExtensionCommand> beforeDevelopMerge()
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public Iterable<ExtensionCommand> afterDevelopMerge()
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public Iterable<ExtensionCommand> afterPush()
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public Iterable<ExtensionCommand> before()
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public Iterable<ExtensionCommand> after()
    {
        return Collections.EMPTY_LIST;
    }
}
