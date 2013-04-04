package com.atlassian.maven.plugins.jgitflow;

import java.io.File;

import com.atlassian.jgitflow.core.InitContext;

/**
 * @since version
 */
public class ReleaseContext
{
    private boolean allowSnapshots;
    private boolean interactive;
    private boolean autoVersionSubmodules;
    private boolean updateDependencies;
    private String defaultReleaseVersion;
    private InitContext flowInitContext;
    private final File baseDir;
    
    public ReleaseContext(File baseDir)
    {
        this.baseDir = baseDir;
        this.allowSnapshots = false;
        this.defaultReleaseVersion = null;
        this.interactive = true;
        this.autoVersionSubmodules = false;
        this.updateDependencies = true;
        this.flowInitContext = new InitContext();
    }

    public boolean isAllowSnapshots()
    {
        return allowSnapshots;
    }

    public ReleaseContext setAllowSnapshots(boolean allowSnapshots)
    {
        this.allowSnapshots = allowSnapshots;
        return this;
    }

    public String getDefaultReleaseVersion()
    {
        return defaultReleaseVersion;
    }

    public ReleaseContext setDefaultReleaseVersion(String defaultReleaseVersion)
    {
        this.defaultReleaseVersion = defaultReleaseVersion;
        return this;
    }

    public boolean isInteractive()
    {
        return interactive;
    }

    public ReleaseContext setInteractive(boolean interactive)
    {
        this.interactive = interactive;
        return this;
    }

    public boolean isAutoVersionSubmodules()
    {
        return autoVersionSubmodules;
    }

    public ReleaseContext setAutoVersionSubmodules(boolean autoVersionSubmodules)
    {
        this.autoVersionSubmodules = autoVersionSubmodules;
        return this;
    }

    public InitContext getFlowInitContext()
    {
        return flowInitContext;
    }

    public ReleaseContext setFlowInitContext(InitContext flowInitContext)
    {
        this.flowInitContext = flowInitContext;
        return this;
    }

    public boolean isUpdateDependencies()
    {
        return updateDependencies;
    }

    public ReleaseContext setUpdateDependencies(boolean updateDependencies)
    {
        this.updateDependencies = updateDependencies;
        return this;
    }

    public File getBaseDir()
    {
        return baseDir;
    }
}
