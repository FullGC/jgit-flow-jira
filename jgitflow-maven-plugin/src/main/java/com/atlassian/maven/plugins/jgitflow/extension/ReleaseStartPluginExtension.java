package com.atlassian.maven.plugins.jgitflow.extension;

import com.atlassian.jgitflow.core.extension.ReleaseStartExtension;
import com.atlassian.maven.jgitflow.api.MavenJGitFlowExtension;
import com.atlassian.maven.plugins.jgitflow.extension.command.UpdateDevelopWithNextDevVersionCommand;
import com.atlassian.maven.plugins.jgitflow.extension.command.external.StartReleaseExternalExecutor;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

@Component(role = ReleaseStartPluginExtension.class)
public class ReleaseStartPluginExtension extends ProductionBranchCreatingPluginExtension implements ReleaseStartExtension
{
    @Requirement
    private UpdateDevelopWithNextDevVersionCommand updateDevelopWithNextDevVersionCommand;
    
    @Requirement
    private StartReleaseExternalExecutor releaseExecutor;

    @Override
    public void init(MavenJGitFlowExtension externalExtension)
    {
        super.init(externalExtension);
        
        releaseExecutor.init(externalExtension);
        
        addAfterCreateBranchCommands(
                cacheVersionsCommand
                ,updateDevelopWithNextDevVersionCommand
                ,releaseExecutor
        );
    }
}
