package com.atlassian.jgitflow.core.extension;

import java.util.List;

public interface HotfixFinishExtension extends JGitFlowExtension
{
    List<ExtensionCommand> beforeMasterCheckout();

    List<ExtensionCommand> afterMasterCheckout();

    List<ExtensionCommand> beforeMasterMerge();

    List<ExtensionCommand> afterMasterMerge();

    List<ExtensionCommand> beforeDevelopCheckout();

    List<ExtensionCommand> afterDevelopCheckout();

    List<ExtensionCommand> beforeDevelopMerge();

    List<ExtensionCommand> afterDevelopMerge();

}
