package com.atlassian.maven.plugins.jgitflow.extension;

import com.atlassian.jgitflow.core.extension.impl.EmptyFeatureStartExtension;
import com.atlassian.maven.plugins.jgitflow.extension.command.UpdateFeaturePomsWithSnapshotsCommand;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

@Component(role = FeatureStartPluginExtension.class)
public class FeatureStartPluginExtension extends EmptyFeatureStartExtension implements InitializingExtension
{

    @Requirement
    private UpdateFeaturePomsWithSnapshotsCommand updateFeaturePomsWithSnapshotsCommand;


    @Override
    public void init()
    {
        addAfterCreateBranchCommands(updateFeaturePomsWithSnapshotsCommand);
    }
}
