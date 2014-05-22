package com.atlassian.maven.plugins.jgitflow.extension;

import com.atlassian.jgitflow.core.extension.ReleaseStartExtension;
import com.atlassian.maven.plugins.jgitflow.extension.command.UpdateDevelopWithNextDevVersionCommand;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

@Component(role = ReleaseStartPluginExtension.class)
public class ReleaseStartPluginExtension extends ProductionBranchCreatingPluginExtension implements ReleaseStartExtension
{
    @Requirement
    private UpdateDevelopWithNextDevVersionCommand updateDevelopWithNextDevVersionCommand;

    @Override
    public void init()
    {
        super.init();
        addAfterCreateBranchCommands(updateDevelopWithNextDevVersionCommand);
    }
}
