package com.atlassian.maven.plugins.jgitflow.extension;

import com.atlassian.jgitflow.core.extension.ExtensionCommand;
import com.atlassian.jgitflow.core.extension.FeatureStartExtension;
import com.atlassian.jgitflow.core.extension.impl.EmptyBranchCreatingExtension;
import com.atlassian.jgitflow.core.extension.impl.EmptyFeatureStartExtension;
import com.atlassian.maven.plugins.jgitflow.extension.command.EnsureOriginCommand;
import com.atlassian.maven.plugins.jgitflow.extension.command.UpdateFeaturePomsWithSnapshotsCommand;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

@Component(role = FeatureStartPluginExtension.class)
public class FeatureStartPluginExtension extends EmptyFeatureStartExtension implements InitializingExtension
{
    @Requirement
    private EnsureOriginCommand ensureOriginCommand;
    
    @Requirement
    private UpdateFeaturePomsWithSnapshotsCommand updateFeaturePomsWithSnapshotsCommand;
            
    
    @Override
    public void init()
    {
        addBeforeCommands(ensureOriginCommand);
        addAfterCreateBranchCommands(updateFeaturePomsWithSnapshotsCommand);
    }
}
