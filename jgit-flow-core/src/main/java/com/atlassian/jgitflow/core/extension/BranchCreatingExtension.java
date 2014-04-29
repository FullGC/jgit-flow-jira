package com.atlassian.jgitflow.core.extension;

import java.util.List;

public interface BranchCreatingExtension extends JGitFlowExtension
{
    List<ExtensionCommand> beforeCreateBranch();

    List<ExtensionCommand> afterCreateBranch();
}
