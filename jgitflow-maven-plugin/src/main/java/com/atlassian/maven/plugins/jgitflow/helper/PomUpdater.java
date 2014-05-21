package com.atlassian.maven.plugins.jgitflow.helper;

import java.util.List;
import java.util.Map;

import com.atlassian.maven.plugins.jgitflow.VersionType;
import com.atlassian.maven.plugins.jgitflow.exception.MavenJGitFlowException;
import com.atlassian.maven.plugins.jgitflow.provider.ProjectCacheKey;

import org.apache.maven.project.MavenProject;

public interface PomUpdater
{

    void removeSnapshotFromPomVersions(ProjectCacheKey cacheKey, String versionLabel, String versionSuffix, List<MavenProject> reactorProjects) throws MavenJGitFlowException;

    void addSnapshotToPomVersions(ProjectCacheKey cacheKey, VersionType versionType, String versionLabel, String versionSuffix, List<MavenProject> reactorProjects) throws MavenJGitFlowException;

    void copyPomVersionsFromProject(List<MavenProject> projectsToUpdate, List<MavenProject> projectsToCopy) throws MavenJGitFlowException;

    void copyPomVersionsFromMap(List<MavenProject> projectsToUpdate, Map<String,String> versionsToCopy) throws MavenJGitFlowException;

    void updatePomsWithNextDevelopmentVersion(ProjectCacheKey cacheKey, List<MavenProject> reactorProjects) throws MavenJGitFlowException;

    void addFeatureVersionToSnapshotVersions(ProjectCacheKey cacheKey, String featureVersion, List<MavenProject> reactorProjects) throws MavenJGitFlowException;

    void removeFeatureVersionFromSnapshotVersions(ProjectCacheKey cacheKey, String featureVersion, List<MavenProject> reactorProjects) throws MavenJGitFlowException;
    void removeSnapshotFromFeatureVersions(ProjectCacheKey cacheKey, final String featureVersion, List<MavenProject> reactorProjects) throws MavenJGitFlowException;
}
