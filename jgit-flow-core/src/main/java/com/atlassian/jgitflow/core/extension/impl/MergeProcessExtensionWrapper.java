package com.atlassian.jgitflow.core.extension.impl;

import java.util.List;

import com.atlassian.jgitflow.core.extension.ExtensionCommand;

public class MergeProcessExtensionWrapper
{
    private final List<ExtensionCommand> beforeCheckout;
    private final List<ExtensionCommand> afterCheckout;
    private final List<ExtensionCommand> beforeMerge;
    private final List<ExtensionCommand> afterMerge;

    public MergeProcessExtensionWrapper(List<ExtensionCommand> beforeCheckout, List<ExtensionCommand> afterCheckout, List<ExtensionCommand> beforeMerge, List<ExtensionCommand> afterMerge)
    {
        this.beforeCheckout = beforeCheckout;
        this.afterCheckout = afterCheckout;
        this.beforeMerge = beforeMerge;
        this.afterMerge = afterMerge;
    }

    public List<ExtensionCommand> beforeCheckout() {return this.beforeCheckout;}

    public List<ExtensionCommand> afterCheckout() {return this.afterCheckout;}

    public List<ExtensionCommand> beforeMerge() {return this.beforeMerge;}

    public List<ExtensionCommand> afterMerge() {return this.afterMerge;}
}
