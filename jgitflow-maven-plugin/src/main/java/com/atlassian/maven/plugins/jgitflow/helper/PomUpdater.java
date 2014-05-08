package com.atlassian.maven.plugins.jgitflow.helper;

import java.util.List;
import java.util.Map;

import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.VersionType;
import com.atlassian.maven.plugins.jgitflow.exception.JGitFlowReleaseException;
import com.atlassian.maven.plugins.jgitflow.provider.ProjectCacheKey;

import org.apache.maven.project.MavenProject;

public interface PomUpdater
{

    void removeSnapshotFromPomVersions(ProjectCacheKey cacheKey, String versionLabel, String versionSuffix, List<MavenProject> reactorProjects) throws JGitFlowReleaseException;

    void addSnapshotToPomVersions(ProjectCacheKey cacheKey, VersionType versionType, String versionLabel, String versionSuffix, List<MavenProject> reactorProjects) throws JGitFlowReleaseException;

    void copyPomVersionsFromProject(List<MavenProject> projectsToUpdate, List<MavenProject> projectsToCopy) throws JGitFlowReleaseException;

    void copyPomVersionsFromMap(List<MavenProject> projectsToUpdate, Map<String,String> versionsToCopy) throws JGitFlowReleaseException;

    void updatePomsWithNextDevelopmentVersion(ProjectCacheKey cacheKey, List<MavenProject> reactorProjects) throws JGitFlowReleaseException;

    void addFeatureVersionToSnapshotVersions(ProjectCacheKey cacheKey, String featureVersion, List<MavenProject> reactorProjects) throws JGitFlowReleaseException;

    void removeFeatureVersionFromSnapshotVersions(ProjectCacheKey cacheKey, String featureVersion, List<MavenProject> reactorProjects) throws JGitFlowReleaseException;
    void removeSnapshotFromFeatureVersions(ProjectCacheKey cacheKey, final String featureVersion, List<MavenProject> reactorProjects) throws JGitFlowReleaseException;
}
