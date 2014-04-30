package ut.com.atlassian.jgitflow.core.testutils;

import java.util.List;

import com.atlassian.jgitflow.core.extension.ExtensionCommand;
import com.atlassian.jgitflow.core.extension.HotfixFinishExtension;

import com.google.common.collect.Lists;

public class HotfixFinishExtensionForTests extends BaseExtensionForTests<ReleaseFinishExtensionForTests> implements HotfixFinishExtension
{
    @Override
    public Iterable<ExtensionCommand> beforeMasterCheckout()
    {
        return Lists.<ExtensionCommand> newArrayList(createExtension(BEFORE_MASTER_CHECKOUT));
    }

    @Override
    public Iterable<ExtensionCommand> afterMasterCheckout()
    {
        return Lists.<ExtensionCommand> newArrayList(createExtension(AFTER_MASTER_CHECKOUT));
    }

    @Override
    public Iterable<ExtensionCommand> beforeMasterMerge()
    {
        return Lists.<ExtensionCommand> newArrayList(createExtension(BEFORE_MASTER_MERGE));
    }

    @Override
    public Iterable<ExtensionCommand> afterMasterMerge()
    {
        return Lists.<ExtensionCommand> newArrayList(createExtension(AFTER_MASTER_MERGE));
    }

    @Override
    public Iterable<ExtensionCommand> beforeDevelopCheckout()
    {
        return Lists.<ExtensionCommand> newArrayList(createExtension(BaseExtensionForTests.BEFORE_DEVELOP_CHECKOUT));
    }

    @Override
    public Iterable<ExtensionCommand> afterDevelopCheckout()
    {
        return Lists.<ExtensionCommand> newArrayList(createExtension(BaseExtensionForTests.AFTER_DEVELOP_CHECKOUT));
    }

    @Override
    public Iterable<ExtensionCommand> beforeDevelopMerge()
    {
        return Lists.<ExtensionCommand> newArrayList(createExtension(BaseExtensionForTests.BEFORE_DEVELOP_MERGE));
    }

    @Override
    public Iterable<ExtensionCommand> afterDevelopMerge()
    {
        return Lists.<ExtensionCommand> newArrayList(createExtension(BaseExtensionForTests.AFTER_DEVELOP_MERGE));
    }

    @Override
    public Iterable<ExtensionCommand> afterPush()
    {
        return Lists.<ExtensionCommand> newArrayList(createExtension(BaseExtensionForTests.AFTER_PUSH));
    }
}
