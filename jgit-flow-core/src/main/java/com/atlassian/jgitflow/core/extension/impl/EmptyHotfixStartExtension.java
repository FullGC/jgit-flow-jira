package com.atlassian.jgitflow.core.extension.impl;

import java.util.Collections;
import java.util.List;

import com.atlassian.jgitflow.core.extension.ExtensionCommand;
import com.atlassian.jgitflow.core.extension.HotfixStartExtension;

public class EmptyHotfixStartExtension implements HotfixStartExtension
{

    @Override
    public List<ExtensionCommand> beforeFetch()
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<ExtensionCommand> afterFetch()
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<ExtensionCommand> beforeCreateBranch()
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<ExtensionCommand> afterCreateBranch()
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<ExtensionCommand> afterPush()
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<ExtensionCommand> before()
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<ExtensionCommand> after()
    {
        return Collections.EMPTY_LIST;
    }
}
