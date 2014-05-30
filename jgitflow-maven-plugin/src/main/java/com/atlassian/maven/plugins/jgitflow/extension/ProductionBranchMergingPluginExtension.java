package com.atlassian.maven.plugins.jgitflow.extension;

import com.atlassian.jgitflow.core.extension.impl.EmptyMasterAndDevelopMergingExtension;
import com.atlassian.maven.plugins.jgitflow.extension.command.*;

import org.codehaus.plexus.component.annotations.Requirement;

public abstract class ProductionBranchMergingPluginExtension extends EmptyMasterAndDevelopMergingExtension implements InitializingExtension
{
    @Requirement
    private EnsureOriginCommand ensureOriginCommand;

    @Requirement
    private PullDevelopCommand pullDevelopCommand;

    @Requirement
    private PullMasterCommand pullMasterCommand;

    @Requirement
    private UpdatePomsWithNonSnapshotCommand updatePomsWithNonSnapshotCommand;
    
    @Requirement
    private VerifyReleaseVersionStateAndDepsCommand verifyReleaseVersionStateAndDepsCommand;
    
    @Requirement
    private MavenBuildCommand mavenBuildCommand;

    @Requirement
    private TagMessageUpdateCommand tagMessageUpdateCommand;

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
