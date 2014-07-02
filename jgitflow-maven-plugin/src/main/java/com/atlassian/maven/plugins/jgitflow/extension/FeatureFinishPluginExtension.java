package com.atlassian.maven.plugins.jgitflow.extension;

import com.atlassian.jgitflow.core.extension.impl.EmptyFeatureFinishExtension;
import com.atlassian.maven.plugins.jgitflow.extension.command.MavenBuildCommand;
import com.atlassian.maven.plugins.jgitflow.extension.command.UpdateFeaturePomsWithFinalVersionsCommand;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

@Component(role = FeatureFinishPluginExtension.class)
public class FeatureFinishPluginExtension extends EmptyFeatureFinishExtension implements InitializingExtension
{
    @Requirement
    private UpdateFeaturePomsWithFinalVersionsCommand updateFeaturePomsWithFinalVersionsCommand;

    @Requirement
    private MavenBuildCommand mavenBuildCommand;

    @Override
    public void init()
    {
        addAfterTopicCheckoutCommands(
                updateFeaturePomsWithFinalVersionsCommand,
                mavenBuildCommand
        );
    }
}
