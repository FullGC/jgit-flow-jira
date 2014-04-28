package ut.com.atlassian.jgitflow.core.testutils;

import java.util.List;

import com.atlassian.jgitflow.core.extension.ExtensionCommand;
import com.atlassian.jgitflow.core.extension.HotfixStartExtension;

import com.google.common.collect.Lists;

public class HotfixStartExtensionForTests extends BaseExtensionForTests<HotfixStartExtensionForTests> implements HotfixStartExtension
{
    @Override
    public List<ExtensionCommand> beforeCreateBranch()
    {
        return Lists.<ExtensionCommand> newArrayList(createExtension(BEFORE_CREATE_BRANCH));
    }

    @Override
    public List<ExtensionCommand> afterCreateBranch()
    {
        return Lists.<ExtensionCommand> newArrayList(createExtension(AFTER_CREATE_BRANCH));
    }

    @Override
    public List<ExtensionCommand> afterPush()
    {
        return Lists.<ExtensionCommand> newArrayList(createExtension(AFTER_PUSH));
    }
}
