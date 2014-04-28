package ut.com.atlassian.jgitflow.core.testutils;

import java.util.List;

import com.atlassian.jgitflow.core.extension.ExtensionCommand;
import com.atlassian.jgitflow.core.extension.FeatureFinishExtension;

import com.google.common.collect.Lists;

public class FeatureFinishExtensionForTests extends BaseExtensionForTests<FeatureFinishExtensionForTests> implements FeatureFinishExtension
{
    @Override
    public List<ExtensionCommand> beforeRebase()
    {
        return Lists.<ExtensionCommand> newArrayList(createExtension(BEFORE_REBASE));
    }

    @Override
    public List<ExtensionCommand> afterRebase()
    {
        return Lists.<ExtensionCommand> newArrayList(createExtension(AFTER_REBASE));
    }

    @Override
    public List<ExtensionCommand> beforeDevelopCheckout()
    {
        return Lists.<ExtensionCommand> newArrayList(createExtension(BEFORE_DEVELOP_CHECKOUT));
    }

    @Override
    public List<ExtensionCommand> afterDevelopCheckout()
    {
        return Lists.<ExtensionCommand> newArrayList(createExtension(AFTER_DEVELOP_CHECKOUT));
    }

    @Override
    public List<ExtensionCommand> beforeDevelopMerge()
    {
        return Lists.<ExtensionCommand> newArrayList(createExtension(BEFORE_DEVELOP_MERGE));
    }

    @Override
    public List<ExtensionCommand> afterDevelopMerge()
    {
        return Lists.<ExtensionCommand> newArrayList(createExtension(AFTER_DEVELOP_MERGE));
    }

    @Override
    public List<ExtensionCommand> afterPush()
    {
        return Lists.<ExtensionCommand> newArrayList(createExtension(AFTER_PUSH));
    }
}
