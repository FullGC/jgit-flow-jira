package com.atlassian.jgitflow.core.extension;

public interface MasterMergingExtension extends JGitFlowExtension
{
    Iterable<ExtensionCommand> beforeMasterCheckout();

    Iterable<ExtensionCommand> afterMasterCheckout();

    Iterable<ExtensionCommand> beforeMasterMerge();

    Iterable<ExtensionCommand> afterMasterMerge();
}
