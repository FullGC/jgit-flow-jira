package com.atlassian.maven.plugins.jgitflow.provider;

import java.util.List;
import java.util.Map;

import com.atlassian.maven.plugins.jgitflow.VersionType;
import com.atlassian.maven.plugins.jgitflow.exception.MavenJGitFlowException;

import org.apache.maven.project.MavenProject;

/**
 * Helper class for getting a map that contains module -> version strings.
 * This is used to get versions for all projects/submodule in a maven reacotr list
 */
public interface VersionProvider
{
    Map<String, String> getNextVersionsForType(VersionType versionType, ProjectCacheKey cacheKey, List<MavenProject> reactorProjects) throws MavenJGitFlowException;

    /**
     * Returns the (next) release versions for all of the projects in the reactor.
     * If defaultReleaseVersion is defined, it will use that for the root project as well as all sub modules if autoVersionSubmodules is true.
     * If defaultReleaseVersion is not defined, or if autoVersionSubmodules is false, it will prompt the user for the release version for any
     * version in the reactor that is currently a SNAPSHOT
     *
     * @param cacheKey        The cacheKey to use when looking for versions so we don't have to loop over the reactor everytime
     * @param reactorProjects The set of reactorProjects to loop over
     * @return A Map<String,String> where the key is the project/module key and the value is the release version
     * @throws com.atlassian.maven.plugins.jgitflow.exception.MavenJGitFlowException
     */
    Map<String, String> getNextReleaseVersions(ProjectCacheKey cacheKey, List<MavenProject> reactorProjects) throws MavenJGitFlowException;

    /**
     * Returns the (next) hotfix versions for all of the projects in the reactor.
     * If defaultReleaseVersion is defined, it will use that for the root project as well as all sub modules if autoVersionSubmodules is true.
     * If defaultReleaseVersion is not defined, or if autoVersionSubmodules is false, it will prompt the user for the hotfix version for any
     * version in the reactor that is currently a SNAPSHOT
     *
     * @param cacheKey        The cacheKey to use when looking for versions so we don't have to loop over the reactor everytime
     * @param reactorProjects The set of reactorProjects to loop over
     * @return A Map<String,String> where the key is the project/module key and the value is the hotfix version
     * @throws com.atlassian.maven.plugins.jgitflow.exception.MavenJGitFlowException
     */
    Map<String, String> getNextHotfixVersions(ProjectCacheKey cacheKey, List<MavenProject> reactorProjects) throws MavenJGitFlowException;

    /**
     * Returns the (next) development versions for all of the projects in the reactor.
     * If defaultDevelopmentVersion is defined, it will use that for the root project as well as all sub modules if autoVersionSubmodules is true.
     * If defaultDevelopmentVersion is not defined, or if autoVersionSubmodules is false, it will prompt the user for the development version for any
     * version in the reactor that is currently a not a SNAPSHOT
     *
     * @param cacheKey        The cacheKey to use when looking for versions so we don't have to loop over the reactor everytime
     * @param reactorProjects The set of reactorProjects to loop over
     * @return A Map<String,String> where the key is the project/module key and the value is the release version
     * @throws com.atlassian.maven.plugins.jgitflow.exception.MavenJGitFlowException
     */
    Map<String, String> getNextDevelopmentVersions(ProjectCacheKey cacheKey, List<MavenProject> reactorProjects) throws MavenJGitFlowException;

    /**
     * Returns the last release versions for all of the projects in the reactor.
     *
     * @return A Map<String,String> where the key is the project/module key and the value is the release version
     * @throws com.atlassian.maven.plugins.jgitflow.exception.MavenJGitFlowException
     */
    Map<String, String> getLastReleaseVersions(MavenProject rootProject) throws MavenJGitFlowException;

    /**
     * Returns the current versions for all of the projects in the reactor.
     *
     * @param cacheKey        The cacheKey to use when looking for versions so we don't have to loop over the reactor everytime
     * @param reactorProjects The set of reactorProjects to loop over
     * @return A Map<String,String> where the key is the project/module key and the value is the version
     */
    Map<String, String> getOriginalVersions(ProjectCacheKey cacheKey, List<MavenProject> reactorProjects);

    /**
     * Returns the current versions for all of the projects in the reactor.
     * This method never looks up from cache.
     *
     * @param reactorProjects The set of reactorProjects to loop over
     * @return A Map<String,String> where the key is the project/module key and the value is the version
     */
    Map<String, String> getOriginalVersions(List<MavenProject> reactorProjects);

    String getRootVersion(ProjectCacheKey cacheKey, List<MavenProject> reactorProjects);

    String getRootVersion(List<MavenProject> reactorProjects);

}
