package com.atlassian.maven.plugins.jgitflow.extension.command.external;

import com.atlassian.maven.jgitflow.api.exception.MavenJGitFlowExtensionException;
import com.atlassian.maven.plugins.jgitflow.helper.BranchHelper;
import com.atlassian.maven.plugins.jgitflow.provider.ReactorProjectsProvider;
import com.atlassian.maven.plugins.jgitflow.provider.VersionCacheProvider;
import com.atlassian.maven.plugins.jgitflow.provider.VersionProvider;

import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.release.util.ReleaseUtil;
import org.codehaus.plexus.component.annotations.Requirement;

public abstract class CachedVersionExternalExecutor extends ExternalCommandExecutor
{
    @Requirement
    protected VersionCacheProvider versionCacheProvider;

    @Requirement
    protected VersionProvider versionProvider;

    @Requirement
    protected BranchHelper branchHelper;

    @Requirement
    protected ReactorProjectsProvider reactorProjectsProvider;
    
    @Override
    public String getOldVersion() throws MavenJGitFlowExtensionException
    {
        MavenProject rootProject = ReleaseUtil.getRootProject(reactorProjectsProvider.getReactorProjects());

        return versionCacheProvider.getCachedVersions().get(ArtifactUtils.versionlessKey(rootProject.getGroupId(), rootProject.getArtifactId()));
    }

    @Override
    public String getNewVersion() throws MavenJGitFlowExtensionException
    {
        try
        {
            return versionProvider.getRootVersion(branchHelper.getProjectsForCurrentBranch());
        }
        catch (Exception e)
        {
            throw new MavenJGitFlowExtensionException("Error calculating new version", e);
        }
    }
}
