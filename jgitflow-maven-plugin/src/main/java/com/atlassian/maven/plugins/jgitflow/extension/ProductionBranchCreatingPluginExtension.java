package com.atlassian.maven.plugins.jgitflow.extension;

import com.atlassian.jgitflow.core.extension.impl.EmptyBranchCreatingExtension;
import com.atlassian.maven.jgitflow.api.MavenJGitFlowExtension;
import com.atlassian.maven.plugins.jgitflow.extension.command.CacheVersionsCommand;
import com.atlassian.maven.plugins.jgitflow.extension.command.UpdatePomsWithSnapshotsCommand;
import com.atlassian.maven.plugins.jgitflow.extension.command.external.StartProductionExternalExecutor;

import org.codehaus.plexus.component.annotations.Requirement;

public abstract class ProductionBranchCreatingPluginExtension extends EmptyBranchCreatingExtension implements ExternalInitializingExtension
{
    @Requirement
    protected UpdatePomsWithSnapshotsCommand updatePomsWithSnapshotCommand;

    @Requirement
    protected CacheVersionsCommand cacheVersionsCommand;

    @Requirement
    protected StartProductionExternalExecutor productionExecutor;

    @Override
    public void init(MavenJGitFlowExtension externalExtension)
    {
        productionExecutor.init(externalExtension);

        addAfterCreateBranchCommands(
                cacheVersionsCommand
                , updatePomsWithSnapshotCommand
                , productionExecutor
        );
    }
}
