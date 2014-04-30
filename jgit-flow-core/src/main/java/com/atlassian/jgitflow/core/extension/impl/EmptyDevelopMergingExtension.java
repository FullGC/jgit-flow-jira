package com.atlassian.jgitflow.core.extension.impl;

import java.util.Arrays;
import java.util.List;

import com.atlassian.jgitflow.core.extension.DevelopMergingExtension;
import com.atlassian.jgitflow.core.extension.ExtensionCommand;

import com.google.common.collect.Iterables;

import static com.google.common.collect.Lists.newArrayList;

public abstract class EmptyDevelopMergingExtension extends EmptyJGitFlowExtension implements DevelopMergingExtension
{
    private final List<ExtensionCommand> beforeDevelopCheckout;
    private final List<ExtensionCommand> afterDevelopCheckout;
    private final List<ExtensionCommand> beforeDevelopMerge;
    private final List<ExtensionCommand> afterDevelopMerge;

    protected EmptyDevelopMergingExtension()
    {
        this.beforeDevelopCheckout = newArrayList();
        this.afterDevelopCheckout = newArrayList();
        this.beforeDevelopMerge = newArrayList();
        this.afterDevelopMerge = newArrayList();
    }

    public void addBeforeDevelopCheckoutCommands(ExtensionCommand ... commands)
    {
        beforeDevelopCheckout.addAll(Arrays.asList(commands));
    }

    public void addAfterDevelopCheckoutCommands(ExtensionCommand ... commands)
    {
        afterDevelopCheckout.addAll(Arrays.asList(commands));
    }

    public void addBeforeDevelopMergeCommands(ExtensionCommand ... commands)
    {
        beforeDevelopMerge.addAll(Arrays.asList(commands));
    }

    public void addAfterDevelopMergeCommands(ExtensionCommand ... commands)
    {
        afterDevelopMerge.addAll(Arrays.asList(commands));
    }
    
    @Override
    public Iterable<ExtensionCommand> beforeDevelopCheckout()
    {
        return Iterables.unmodifiableIterable(beforeDevelopCheckout);
    }

    @Override
    public Iterable<ExtensionCommand> afterDevelopCheckout()
    {
        return Iterables.unmodifiableIterable(afterDevelopCheckout);
    }

    @Override
    public Iterable<ExtensionCommand> beforeDevelopMerge()
    {
        return Iterables.unmodifiableIterable(beforeDevelopMerge);
    }

    @Override
    public Iterable<ExtensionCommand> afterDevelopMerge()
    {
        return Iterables.unmodifiableIterable(afterDevelopMerge);
    }
}
