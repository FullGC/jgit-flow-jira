package com.atlassian.maven.plugins.jgitflow.extension;

import com.atlassian.jgitflow.core.extension.impl.EmptyBranchCreatingExtension;
import com.atlassian.maven.plugins.jgitflow.BranchType;
import com.atlassian.maven.plugins.jgitflow.extension.command.EnsureOriginCommand;

import org.codehaus.plexus.component.annotations.Requirement;

public abstract class ProductionBranchPluginExtension extends EmptyBranchCreatingExtension implements InitializingExtension
{
    private final BranchType branchType;

    @Requirement
    EnsureOriginCommand ensureOriginCommand;

    protected ProductionBranchPluginExtension(BranchType branchType)
    {
        this.branchType = branchType;
    }

    @Override
    public void init()
    {
        addBeforeCommands(ensureOriginCommand);
    }
}
