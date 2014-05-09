package com.atlassian.maven.plugins.jgitflow.provider;

import java.util.List;

import com.atlassian.maven.plugins.jgitflow.VersionType;
import com.atlassian.maven.plugins.jgitflow.exception.MavenJGitFlowException;

import org.apache.maven.project.MavenProject;

public interface BranchLabelProvider
{
    String getVersionLabel(VersionType versionType, ProjectCacheKey cacheKey, List<MavenProject> reactorProjects) throws MavenJGitFlowException;

    String getFeatureStartName() throws MavenJGitFlowException;

    String getFeatureFinishName() throws MavenJGitFlowException;
}
