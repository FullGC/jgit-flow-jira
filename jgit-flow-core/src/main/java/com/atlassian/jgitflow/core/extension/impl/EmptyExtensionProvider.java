package com.atlassian.jgitflow.core.extension.impl;

import java.util.Collections;
import java.util.List;

import com.atlassian.jgitflow.core.extension.*;

public class EmptyExtensionProvider implements ExtensionProvider
{

    @Override
    public ReleaseStartExtension provideReleaseStartExtension()
    {
        return new EmptyReleaseStartExtension();
    }

    @Override
    public ReleaseFinishExtension provideReleaseFinishExtension()
    {
        return new EmptyReleaseFinishExtension();
    }

    @Override
    public HotfixStartExtension provideHotfixStartExtension()
    {
        return new EmptyHotfixStartExtension();
    }

    @Override
    public HotfixFinishExtension provideHotfixFinishExtension()
    {
        return new EmptyHotfixFinishExtension();
    }

    @Override
    public FeatureStartExtension provideFeatureStartExtension()
    {
        return new EmptyFeatureStartExtension();
    }

    @Override
    public FeatureFinishExtension provideFeatureFinishExtension()
    {
        return new EmptyFeatureFinishExtension();
    }
}
