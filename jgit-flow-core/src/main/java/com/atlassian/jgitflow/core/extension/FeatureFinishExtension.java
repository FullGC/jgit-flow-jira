package com.atlassian.jgitflow.core.extension;

import java.util.List;

public interface FeatureFinishExtension extends JGitFlowExtension
{
    List<ExtensionCommand> beforeFetch();

    List<ExtensionCommand> afterFetch();

    List<ExtensionCommand> beforeRebase();
    
    List<ExtensionCommand> afterRebase();

    List<ExtensionCommand> beforeDevelopCheckout();

    List<ExtensionCommand> afterDevelopCheckout();

    List<ExtensionCommand> beforeDevelopMerge();

    List<ExtensionCommand> afterDevelopMerge();

    List<ExtensionCommand> afterPush();
}
