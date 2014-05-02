package com.atlassian.maven.plugins.jgitflow.helper;

import java.util.List;
import java.util.Map;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.JGitFlowReporter;
import com.atlassian.maven.plugins.jgitflow.VersionState;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.JGitFlowReleaseException;

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
    
    void fixCygwinIfNeeded(JGitFlow flow) throws JGitFlowReleaseException;
    
    Map<String,String> getOriginalVersions(ProjectCacheKey cacheKey, List<MavenProject> reactorProjects);

    void ensureOrigin(String defaultRemote, boolean alwaysUpdateOrigin, JGitFlow flow) throws JGitFlowReleaseException;

    void commitAllChanges(Git git, String message) throws JGitFlowReleaseException;

    void commitAllPoms(Git git, List<MavenProject> reactorProjects, String message) throws JGitFlowReleaseException;

    void checkPomForVersionState(VersionState state, List<MavenProject> reactorProjects) throws JGitFlowReleaseException;

    List<String> checkForNonReactorSnapshots(ProjectCacheKey cacheKey, List<MavenProject> reactorProjects) throws JGitFlowReleaseException;

    boolean setupUserPasswordCredentialsProvider(ReleaseContext ctx, JGitFlowReporter reporter);

    boolean setupSshCredentialsProvider(ReleaseContext ctx, JGitFlowReporter reporter);
}
