package com.atlassian.maven.plugins.jgitflow;

import java.util.HashMap;
import java.util.Map;

/**
 * @since version
 */
public class MavenJGitFlowConfiguration
{
    private Map<String,String> lastReleaseVersions;
    private Map<String,String> preHotfixVersions;

    public Map<String, String> getLastReleaseVersions()
    {
        if(null == lastReleaseVersions)
        {
            lastReleaseVersions = new HashMap<String, String>();
        }
        
        return lastReleaseVersions;
    }

    public void setLastReleaseVersions(Map<String, String> lastReleaseVersions)
    {
        this.lastReleaseVersions = lastReleaseVersions;
    }

    public Map<String, String> getPreHotfixVersions()
    {
        if(null == preHotfixVersions)
        {
            preHotfixVersions = new HashMap<String, String>();
        }
        
        return preHotfixVersions;
    }

    public void setPreHotfixVersions(Map<String, String> preHotfixVersions)
    {
        this.preHotfixVersions = preHotfixVersions;
    }
}
