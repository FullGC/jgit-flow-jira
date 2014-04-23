package com.atlassian.jgitflow.core.extension;

import java.util.List;

public interface ReleaseFinishExtension
{
    List<ExtensionCommand> beforeFetch();

    List<ExtensionCommand> afterFetch();

    List<ExtensionCommand> beforeMasterCheckout();

    List<ExtensionCommand> afterMasterCheckout();

    List<ExtensionCommand> beforeMasterMerge();

    List<ExtensionCommand> afterMasterMerge();

    List<ExtensionCommand> beforeDevelopCheckout();

    List<ExtensionCommand> afterDevelopCheckout();

    List<ExtensionCommand> beforeDevelopMerge();

    List<ExtensionCommand> afterDevelopMerge();

    List<ExtensionCommand> afterPush();
}
