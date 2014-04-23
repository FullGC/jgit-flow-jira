package com.atlassian.jgitflow.core.extension;

import java.util.List;

public interface FeatureStartExtension extends JGitFlowExtension
{
    List<ExtensionCommand> beforeFetch();

    List<ExtensionCommand> afterFetch();

    List<ExtensionCommand> beforeCreateBranch();

    List<ExtensionCommand> afterCreateBranch();

    List<ExtensionCommand> afterPush();
}
