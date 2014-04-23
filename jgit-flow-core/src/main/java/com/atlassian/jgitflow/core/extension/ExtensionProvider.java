package com.atlassian.jgitflow.core.extension;

import java.util.List;

import com.atlassian.jgitflow.core.exception.JGitFlowExtensionException;

public interface ExtensionProvider
{
    ReleaseStartExtension provideReleaseStartExtension();
    ReleaseFinishExtension provideReleaseFinishExtension();

    HotfixStartExtension provideHotfixStartExtension();
    HotfixFinishExtension provideHotfixFinishExtension();

    FeatureStartExtension provideFeatureStartExtension();
    FeatureFinishExtension provideFeatureFinishExtension();
}
