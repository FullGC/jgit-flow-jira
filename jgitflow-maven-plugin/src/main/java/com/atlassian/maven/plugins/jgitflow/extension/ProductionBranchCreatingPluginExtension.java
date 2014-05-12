package com.atlassian.maven.plugins.jgitflow.extension;

import com.atlassian.jgitflow.core.extension.impl.EmptyBranchCreatingExtension;
import com.atlassian.maven.plugins.jgitflow.BranchType;
import com.atlassian.maven.plugins.jgitflow.extension.command.EnsureOriginCommand;
import com.atlassian.maven.plugins.jgitflow.extension.command.UpdatePomsWithSnapshotsCommand;

import org.codehaus.plexus.component.annotations.Requirement;

public abstract class ProductionBranchCreatingPluginExtension extends EmptyBranchCreatingExtension implements InitializingExtension
{
    @Requirement
    EnsureOriginCommand ensureOriginCommand;

    @Requirement
    UpdatePomsWithSnapshotsCommand updatePomsWithSnapshotCommand;

    @Override
    public void init()
    {
        addBeforeCommands(ensureOriginCommand);
        addAfterCreateBranchCommands(updatePomsWithSnapshotCommand);
    }
}
