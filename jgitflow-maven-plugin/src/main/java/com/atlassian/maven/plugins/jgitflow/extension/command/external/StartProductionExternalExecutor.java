package com.atlassian.maven.plugins.jgitflow.extension.command.external;

import com.atlassian.jgitflow.core.BranchType;
import com.atlassian.jgitflow.core.JGitFlowInfo;
import com.atlassian.maven.jgitflow.api.MavenJGitFlowExtension;
import com.atlassian.maven.jgitflow.api.StartProductionBranchExtension;
import com.atlassian.maven.jgitflow.api.exception.MavenJGitFlowExtensionException;

import org.codehaus.plexus.component.annotations.Component;

@Component(role = StartProductionExternalExecutor.class)
public class StartProductionExternalExecutor extends CachedVersionExternalExecutor
{
    @Override
    public void execute(MavenJGitFlowExtension extension, String newVersion, String oldVersion, JGitFlowInfo flow) throws MavenJGitFlowExtensionException
    {
        if(null == extension || !StartProductionBranchExtension.class.isAssignableFrom(extension.getClass()))
        {
            return;
        }

        StartProductionBranchExtension startExtension = (StartProductionBranchExtension) extension;
        try
        {
            BranchType type = branchHelper.getCurrentBranchType();

            switch(type)
            {
                case HOTFIX:
                    startExtension.onTopicBranchVersionChange(newVersion,oldVersion,flow);
                    break;
                case RELEASE:
                    startExtension.onTopicBranchVersionChange(newVersion,oldVersion,flow);
                    break;
            }
        }
        catch (Exception e)
        {
            throw new MavenJGitFlowExtensionException("Error running external extension",e);
        }
    }
}
