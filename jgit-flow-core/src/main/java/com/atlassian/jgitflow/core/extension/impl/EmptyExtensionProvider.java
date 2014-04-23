package com.atlassian.jgitflow.core.extension.impl;

import java.util.Collections;
import java.util.List;

import com.atlassian.jgitflow.core.extension.*;

public class EmptyExtensionProvider implements ExtensionProvider
{

    @Override
    public List<ReleaseStartExtension> provideReleaseStartExtensions()
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<ReleaseFinishExtension> provideReleaseFinishExtensions()
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<HotfixStartExtension> provideHotfixStartExtensions()
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<HotfixFinishExtension> provideHotfixFinishExtensions()
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<FeatureStartExtension> provideFeatureStartExtensions()
    {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<FeatureFinishExtension> provideFeatureFinishExtensions()
    {
        return Collections.EMPTY_LIST;
    }
}
