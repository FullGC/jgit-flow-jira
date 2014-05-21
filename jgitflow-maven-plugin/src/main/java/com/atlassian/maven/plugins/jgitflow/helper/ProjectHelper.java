package com.atlassian.maven.plugins.jgitflow.helper;

import java.util.List;

import com.atlassian.maven.plugins.jgitflow.VersionState;
import com.atlassian.maven.plugins.jgitflow.exception.MavenJGitFlowException;
import com.atlassian.maven.plugins.jgitflow.provider.ProjectCacheKey;

import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.Git;

/**
 * @since version
 */
public interface ProjectHelper
{
    public static final String AT_PARENT = "parent";
    public static final String AT_DEPENDENCY = "dependency";
    public static final String AT_DEPENDENCY_MGNT = "dependency management";
    public static final String AT_PLUGIN = "plugin";
    public static final String AT_PLUGIN_MGNT = "plugin management";
    public static final String AT_REPORT = "report";
    public static final String AT_EXTENSIONS = "extensions";
    
    void commitAllChanges(Git git, String message) throws MavenJGitFlowException;

    void commitAllPoms(Git git, List<MavenProject> reactorProjects, String message) throws MavenJGitFlowException;

    void checkPomForVersionState(VersionState state, List<MavenProject> reactorProjects) throws MavenJGitFlowException;

    List<String> checkForNonReactorSnapshots(ProjectCacheKey cacheKey, List<MavenProject> reactorProjects) throws MavenJGitFlowException;

}
