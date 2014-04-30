package com.atlassian.jgitflow.core.extension;

public interface ReleaseFinishExtension extends JGitFlowExtension
{
    Iterable<ExtensionCommand> beforeMasterCheckout();

    Iterable<ExtensionCommand> afterMasterCheckout();

    Iterable<ExtensionCommand> beforeMasterMerge();

    Iterable<ExtensionCommand> afterMasterMerge();

    Iterable<ExtensionCommand> beforeDevelopCheckout();

    Iterable<ExtensionCommand> afterDevelopCheckout();

    Iterable<ExtensionCommand> beforeDevelopMerge();

    Iterable<ExtensionCommand> afterDevelopMerge();


}
