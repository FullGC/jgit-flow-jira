package com.atlassian.maven.plugins.jgitflow.extension;

import com.atlassian.jgitflow.core.extension.ReleaseFinishExtension;
import com.atlassian.maven.plugins.jgitflow.extension.command.UpdateDevelopPomsWithMasterVersion;
import com.atlassian.maven.plugins.jgitflow.extension.command.UpdateDevelopWithPreviousVersionsCommand;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

@Component(role = ReleaseFinishPluginExtension.class)
public class ReleaseFinishPluginExtension extends ProductionBranchMergingPluginExtension implements ReleaseFinishExtension
{
    @Requirement
    private UpdateDevelopPomsWithMasterVersion updateDevelopPomsWithMasterVersion;
    
    @Requirement
    private UpdateDevelopWithPreviousVersionsCommand updateDevelopWithPreviousVersionsCommand;
    
    @Override
    public void init()
    {
        super.init();
        
        addBeforeDevelopMergeCommands(updateDevelopPomsWithMasterVersion);
        addAfterDevelopMergeCommands(updateDevelopWithPreviousVersionsCommand);
    }
}
