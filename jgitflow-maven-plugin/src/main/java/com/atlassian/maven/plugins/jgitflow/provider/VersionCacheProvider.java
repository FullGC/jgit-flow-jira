package com.atlassian.maven.plugins.jgitflow.provider;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.atlassian.jgitflow.core.exception.JGitFlowException;
import com.atlassian.maven.plugins.jgitflow.exception.ReactorReloadException;
import com.atlassian.maven.plugins.jgitflow.helper.BranchHelper;

import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.jgit.api.errors.GitAPIException;

@Component(role = VersionCacheProvider.class)
public class VersionCacheProvider
{
    private static final VersionCacheProvider INSTANCE = new VersionCacheProvider();
    private Map<String, String> cache;
    
    @Requirement
    BranchHelper branchHelper;
    
    @Requirement
    VersionProvider versionProvider;
    
    public Map<String,String> cacheCurrentBranchVersions() throws GitAPIException, JGitFlowException, ReactorReloadException, IOException
    {
        List<MavenProject> projects = branchHelper.getProjectsForCurrentBranch();
        INSTANCE.cache = versionProvider.getOriginalVersions(projects);
        
        return INSTANCE.cache;
    }
    
    public Map<String,String> getCachedVersions()
    {
        return INSTANCE.cache;
    }
}
