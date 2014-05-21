package com.atlassian.maven.plugins.jgitflow.extension;

import com.atlassian.jgitflow.core.extension.HotfixFinishExtension;
import com.atlassian.maven.plugins.jgitflow.extension.command.UpdateDevelopWithHotfixVersionsCommand;
import com.atlassian.maven.plugins.jgitflow.extension.command.UpdateDevelopWithPreviousVersionsCommand;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

@Component(role = HotfixFinishPluginExtension.class)
public class HotfixFinishPluginExtension extends ProductionBranchMergingPluginExtension implements HotfixFinishExtension
{
    @Requirement
    private UpdateDevelopWithHotfixVersionsCommand updateDevelopWithHotfixVersionsCommand;
    
    @Requirement
    private UpdateDevelopWithPreviousVersionsCommand updateDevelopWithPreviousVersionsCommand;
    
    @Override
    public void init()
    {
        super.init();
        
        //we need to avoid merge conflicts from master to develop with hotfix versions
        
        //update develop to hotfix versions
        addBeforeDevelopMergeCommands(updateDevelopWithHotfixVersionsCommand);
        
        //update develop to previous development versions
        addAfterDevelopMergeCommands(updateDevelopWithPreviousVersionsCommand);
    }
}
