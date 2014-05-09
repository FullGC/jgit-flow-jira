package com.atlassian.maven.plugins.jgitflow.extension;

import com.atlassian.jgitflow.core.extension.ReleaseStartExtension;
import com.atlassian.maven.plugins.jgitflow.BranchType;
import com.atlassian.maven.plugins.jgitflow.extension.command.UpdateReleasePomsWithSnapshotCommand;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

@Component(role = ReleaseStartPluginExtension.class)
public class ReleaseStartPluginExtension extends ProductionBranchPluginExtension implements ReleaseStartExtension
{
    @Requirement
    UpdateReleasePomsWithSnapshotCommand updateReleasePomsWithSnapshotCommand;
    
    public ReleaseStartPluginExtension()
    {
        super(BranchType.RELEASE);
    }

    @Override
    public void init()
    {
        super.init();
        
        addAfterCreateBranchCommands(updateReleasePomsWithSnapshotCommand);
    }
    
}
