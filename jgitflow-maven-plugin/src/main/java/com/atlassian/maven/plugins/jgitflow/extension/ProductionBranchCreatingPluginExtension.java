package com.atlassian.maven.plugins.jgitflow.extension;

import com.atlassian.jgitflow.core.extension.impl.EmptyBranchCreatingExtension;
import com.atlassian.maven.jgitflow.api.MavenJGitFlowExtension;
import com.atlassian.maven.plugins.jgitflow.extension.command.EnsureOriginCommand;
import com.atlassian.maven.plugins.jgitflow.extension.command.UpdatePomsWithSnapshotsCommand;

import org.codehaus.plexus.component.annotations.Requirement;

public abstract class ProductionBranchCreatingPluginExtension extends EmptyBranchCreatingExtension implements ExternalInitializingExtension
{
    @Requirement
    EnsureOriginCommand ensureOriginCommand;

    @Requirement
    UpdatePomsWithSnapshotsCommand updatePomsWithSnapshotCommand;

    @Override
    public void init(MavenJGitFlowExtension externalExtension)
    {

        addBeforeCommands(ensureOriginCommand);
        addAfterCreateBranchCommands(updatePomsWithSnapshotCommand);
    }
}
