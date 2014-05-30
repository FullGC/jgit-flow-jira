package com.atlassian.maven.plugins.jgitflow.extension.command.external;

import com.atlassian.jgitflow.core.BranchType;
import com.atlassian.jgitflow.core.JGitFlowInfo;
import com.atlassian.maven.jgitflow.api.FinishProductionBranchExtension;
import com.atlassian.maven.jgitflow.api.MavenJGitFlowExtension;
import com.atlassian.maven.jgitflow.api.exception.MavenJGitFlowExtensionException;
import com.atlassian.maven.plugins.jgitflow.helper.BranchHelper;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

@Component(role = FinishProductionExternalExecutor.class)
public class FinishProductionExternalExecutor extends CachedVersionExternalExecutor
{
    @Override
    public void execute(MavenJGitFlowExtension extension, String newVersion, String oldVersion, JGitFlowInfo flow) throws MavenJGitFlowExtensionException
    {
        if(null == extension || !FinishProductionBranchExtension.class.isAssignableFrom(extension.getClass()))
        {
            return;    
        }
        
        FinishProductionBranchExtension finishExtension = (FinishProductionBranchExtension) extension;
        try
        {
            BranchType type = branchHelper.getCurrentBranchType();
            
            switch(type)
            {
                case MASTER:
                    finishExtension.onMasterBranchVersionChange(newVersion,oldVersion,flow);
                    break;
                case HOTFIX:
                    finishExtension.onTopicBranchVersionChange(newVersion,oldVersion,flow);
                    break;
                case RELEASE:
                    finishExtension.onTopicBranchVersionChange(newVersion,oldVersion,flow);
                    break;
            }
        }
        catch (Exception e)
        {
            throw new MavenJGitFlowExtensionException("Error running external extension",e);
        }
    }

    
}
