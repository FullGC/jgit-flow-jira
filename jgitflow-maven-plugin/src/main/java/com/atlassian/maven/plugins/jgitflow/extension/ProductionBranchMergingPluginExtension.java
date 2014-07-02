package com.atlassian.maven.plugins.jgitflow.extension;

import com.atlassian.jgitflow.core.extension.impl.EmptyMasterAndDevelopMergingExtension;
import com.atlassian.maven.jgitflow.api.MavenJGitFlowExtension;
import com.atlassian.maven.plugins.jgitflow.extension.command.*;
import com.atlassian.maven.plugins.jgitflow.extension.command.external.FinishProductionExternalExecutor;

import org.codehaus.plexus.component.annotations.Requirement;

public abstract class ProductionBranchMergingPluginExtension extends EmptyMasterAndDevelopMergingExtension implements ExternalInitializingExtension
{
    @Requirement
    private UpdatePomsWithNonSnapshotCommand updatePomsWithNonSnapshotCommand;

    @Requirement
    private VerifyReleaseVersionStateAndDepsCommand verifyReleaseVersionStateAndDepsCommand;

    @Requirement
    private MavenBuildCommand mavenBuildCommand;

    @Requirement
    private TagMessageUpdateCommand tagMessageUpdateCommand;

    @Requirement
    private FinishProductionExternalExecutor productionExecutor;

    @Requirement
    private CacheVersionsCommand cacheVersionsCommand;

    @Override
    public void init(MavenJGitFlowExtension externalExtension)
    {
        productionExecutor.init(externalExtension);

        addAfterTopicCheckoutCommands(
                cacheVersionsCommand,
                updatePomsWithNonSnapshotCommand,
                verifyReleaseVersionStateAndDepsCommand,
                productionExecutor,
                mavenBuildCommand
        );

        addAfterMasterCheckoutCommands(cacheVersionsCommand);
        addAfterMasterMergeCommands(productionExecutor);
        addBeforeTagCommands(tagMessageUpdateCommand);
    }
}
