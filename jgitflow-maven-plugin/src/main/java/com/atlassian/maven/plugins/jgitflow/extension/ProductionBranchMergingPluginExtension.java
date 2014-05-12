package com.atlassian.maven.plugins.jgitflow.extension;

import com.atlassian.jgitflow.core.extension.impl.EmptyMasterAndDevelopMergingExtension;
import com.atlassian.maven.plugins.jgitflow.BranchType;
import com.atlassian.maven.plugins.jgitflow.extension.command.*;

import org.codehaus.plexus.component.annotations.Requirement;

public abstract class ProductionBranchMergingPluginExtension extends EmptyMasterAndDevelopMergingExtension implements InitializingExtension
{
    @Requirement
    EnsureOriginCommand ensureOriginCommand;

    @Requirement
    PullDevelopCommand pullDevelopCommand;

    @Requirement
    PullMasterCommand pullMasterCommand;

    @Requirement
    UpdatePomsWithNonSnapshotCommand updatePomsWithNonSnapshotCommand;
    
    @Requirement
    VerifyReleaseVersionStateAndDepsCommand verifyReleaseVersionStateAndDepsCommand;
    
    @Requirement
    MavenBuildCommand mavenBuildCommand;

    @Requirement
    TagMessageUpdateCommand tagMessageUpdateCommand;

    @Override
    public void init()
    {
        addBeforeCommands(ensureOriginCommand);
        addAfterFetchCommands(pullDevelopCommand,pullMasterCommand);
        
        addAfterTopicCheckoutCommands(
                updatePomsWithNonSnapshotCommand,
                verifyReleaseVersionStateAndDepsCommand,
                mavenBuildCommand
        );
        
        addBeforeTagCommands(tagMessageUpdateCommand);
    }
}
