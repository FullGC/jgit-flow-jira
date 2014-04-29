package com.atlassian.jgitflow.core.extension;

import java.util.List;

public interface JGitFlowExtension
{
    List<ExtensionCommand> before();

    List<ExtensionCommand> after();

    List<ExtensionCommand> beforeFetch();

    List<ExtensionCommand> afterFetch();

    List<ExtensionCommand> afterPush();
}
