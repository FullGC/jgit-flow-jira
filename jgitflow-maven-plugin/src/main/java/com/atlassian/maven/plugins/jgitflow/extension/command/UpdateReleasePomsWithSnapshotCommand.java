package com.atlassian.maven.plugins.jgitflow.extension.command;

import com.atlassian.maven.plugins.jgitflow.BranchType;

import org.codehaus.plexus.component.annotations.Component;

@Component(role = UpdateReleasePomsWithSnapshotCommand.class)
public class UpdateReleasePomsWithSnapshotCommand extends UpdatePomsWithSnapshotsCommand
{
    public UpdateReleasePomsWithSnapshotCommand()
    {
        super(BranchType.RELEASE);
    }
}
