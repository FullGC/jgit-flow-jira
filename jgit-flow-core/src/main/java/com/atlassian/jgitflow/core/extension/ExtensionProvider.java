package com.atlassian.jgitflow.core.extension;

public interface ExtensionProvider
{
    ReleaseStartExtension provideReleaseStartExtension();

    ReleaseFinishExtension provideReleaseFinishExtension();

    HotfixStartExtension provideHotfixStartExtension();

    HotfixFinishExtension provideHotfixFinishExtension();

    FeatureStartExtension provideFeatureStartExtension();

    FeatureFinishExtension provideFeatureFinishExtension();
}
