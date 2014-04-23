package com.atlassian.jgitflow.core.extension;

import java.util.List;

import com.atlassian.jgitflow.core.exception.JGitFlowExtensionException;

public interface ExtensionProvider
{
    List<ReleaseStartExtension> provideReleaseStartExtensions();
    List<ReleaseFinishExtension> provideReleaseFinishExtensions();

    List<HotfixStartExtension> provideHotfixStartExtensions();
    List<HotfixFinishExtension> provideHotfixFinishExtensions();

    List<FeatureStartExtension> provideFeatureStartExtensions();
    List<FeatureFinishExtension> provideFeatureFinishExtensions();
}
