package com.atlassian.maven.plugins.jgitflow.helper;

import java.util.List;
import java.util.Map;

import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.JGitFlowReleaseException;

import org.apache.maven.project.MavenProject;

/**
 * Helper class for getting a map that contains module -> version strings.
 * This is used to get versions for all projects/submodule in a maven reacotr list
 */
public interface VersionProvider
{
    /**
     * Returns the (next) release versions for all of the projects in the reactor.
     * If defaultReleaseVersion is defined, it will use that for the root project as well as all sub modules if autoVersionSubmodules is true.
     * If defaultReleaseVersion is not defined, or if autoVersionSubmodules is false, it will prompt the user for the release version for any
     * version in the reactor that is currently a SNAPSHOT
     * 
     * @param cacheKey The cacheKey to use when looking for versions so we don't have to loop over the reactor everytime
     * @param reactorProjects The set of reactorProjects to loop over
     * @param ctx The ReleaseContext for JGitFlow
     * @return A Map<String,String> where the key is the project/module key and the value is the release version
     * @throws JGitFlowReleaseException
     */
    Map<String, String> getReleaseVersions(ProjectCacheKey cacheKey, List<MavenProject> reactorProjects, ReleaseContext ctx) throws JGitFlowReleaseException;

    /**
     * Returns the (next) hotfix versions for all of the projects in the reactor.
     * If defaultReleaseVersion is defined, it will use that for the root project as well as all sub modules if autoVersionSubmodules is true.
     * If defaultReleaseVersion is not defined, or if autoVersionSubmodules is false, it will prompt the user for the hotfix version for any
     * version in the reactor that is currently a SNAPSHOT
     *
     * @param cacheKey The cacheKey to use when looking for versions so we don't have to loop over the reactor everytime
     * @param reactorProjects The set of reactorProjects to loop over
     * @param ctx The ReleaseContext for JGitFlow
     * @return A Map<String,String> where the key is the project/module key and the value is the hotfix version
     * @throws JGitFlowReleaseException
     */
    Map<String, String> getHotfixVersions(ProjectCacheKey cacheKey, List<MavenProject> reactorProjects, ReleaseContext ctx, Map<String, String> lastReleaseVersions) throws JGitFlowReleaseException;

    /**
     * Returns the (next) development versions for all of the projects in the reactor.
     * If defaultDevelopmentVersion is defined, it will use that for the root project as well as all sub modules if autoVersionSubmodules is true.
     * If defaultDevelopmentVersion is not defined, or if autoVersionSubmodules is false, it will prompt the user for the development version for any
     * version in the reactor that is currently a not a SNAPSHOT
     *
     * @param cacheKey The cacheKey to use when looking for versions so we don't have to loop over the reactor everytime
     * @param reactorProjects The set of reactorProjects to loop over
     * @param ctx The ReleaseContext for JGitFlow
     * @return A Map<String,String> where the key is the project/module key and the value is the release version
     * @throws JGitFlowReleaseException
     */
    Map<String, String> getDevelopmentVersions(ProjectCacheKey cacheKey, List<MavenProject> reactorProjects, ReleaseContext ctx) throws JGitFlowReleaseException;
    
}
