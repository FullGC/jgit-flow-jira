package com.atlassian.jgitflow.core.extension;

public interface DevelopMergingExtension extends JGitFlowExtension
{
    Iterable<ExtensionCommand> beforeDevelopCheckout();

    Iterable<ExtensionCommand> afterDevelopCheckout();

    Iterable<ExtensionCommand> beforeDevelopMerge();

    Iterable<ExtensionCommand> afterDevelopMerge();
}
