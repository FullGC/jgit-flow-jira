package com.atlassian.maven.plugins.jgitflow;

import com.atlassian.jgitflow.core.InitContext;

/**
 * @since version
 */
public class FlowInitContext
{
    private String masterBranchName;
    private String developBranchName;
    private String featureBranchPrefix;
    private String releaseBranchPrefix;
    private String hotfixBranchPrefix;
    private String versionTagPrefix;

    public FlowInitContext()
    {
        this.masterBranchName = "master";
        this.developBranchName = "develop";
        this.featureBranchPrefix = "feature/";
        this.releaseBranchPrefix = "release/";
        this.hotfixBranchPrefix = "hotfix/";
        this.versionTagPrefix = "";
    }

    public String getMasterBranchName()
    {
        return masterBranchName;
    }

    public void setMasterBranchName(String masterBranchName)
    {
        this.masterBranchName = masterBranchName;
    }

    public String getDevelopBranchName()
    {
        return developBranchName;
    }

    public void setDevelopBranchName(String developBranchName)
    {
        this.developBranchName = developBranchName;
    }

    public String getFeatureBranchPrefix()
    {
        return featureBranchPrefix;
    }

    public void setFeatureBranchPrefix(String featureBranchPrefix)
    {
        this.featureBranchPrefix = featureBranchPrefix;
    }

    public String getReleaseBranchPrefix()
    {
        return releaseBranchPrefix;
    }

    public void setReleaseBranchPrefix(String releaseBranchPrefix)
    {
        this.releaseBranchPrefix = releaseBranchPrefix;
    }

    public String getHotfixBranchPrefix()
    {
        return hotfixBranchPrefix;
    }

    public void setHotfixBranchPrefix(String hotfixBranchPrefix)
    {
        this.hotfixBranchPrefix = hotfixBranchPrefix;
    }

    public String getVersionTagPrefix()
    {
        return versionTagPrefix;
    }

    public void setVersionTagPrefix(String versionTagPrefix)
    {
        this.versionTagPrefix = versionTagPrefix;
    }
    
    public InitContext getJGitFlowContext()
    {
        InitContext ctx = new InitContext();
        ctx.setMaster(masterBranchName)
                .setDevelop(developBranchName)
                .setFeature(featureBranchPrefix)
                .setRelease(releaseBranchPrefix)
                .setHotfix(hotfixBranchPrefix)
                .setVersiontag(versionTagPrefix);
        
        return ctx;
    }
}
