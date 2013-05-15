package com.atlassian.maven.plugins.jgitflow.helper;

import java.util.List;
import java.util.Map;

import com.atlassian.jgitflow.core.JGitFlow;
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
    
    String getReleaseVersion(ReleaseContext ctx, MavenProject rootProject) throws JGitFlowReleaseException;
    
    String getHotfixVersion(ReleaseContext ctx, MavenProject rootProject, String lastRelease) throws JGitFlowReleaseException;
    
    String getDevelopmentVersion(ReleaseContext ctx, MavenProject rootProject) throws JGitFlowReleaseException;

    Map<String,String> getOriginalVersions(List<MavenProject> reactorProjects);

    Map<String,String> getReleaseVersions(List<MavenProject> reactorProjects, ReleaseContext ctx) throws JGitFlowReleaseException;

    Map<String,String> getHotfixVersions(List<MavenProject> reactorProjects, ReleaseContext ctx, Map<String,String> lastReleaseVersions) throws JGitFlowReleaseException;

    Map<String,String> getDevelopmentVersions(List<MavenProject> reactorProjects, ReleaseContext ctx) throws JGitFlowReleaseException;

    void ensureOrigin(List<MavenProject> reactorProjects, JGitFlow flow) throws JGitFlowReleaseException;

    void commitAllChanges(Git git, String message) throws JGitFlowReleaseException;
    
    List<String> checkForNonReactorSnapshots(List<MavenProject> reactorProjects) throws JGitFlowReleaseException;

    String getFeatureStartName(ReleaseContext ctx, JGitFlow flow) throws JGitFlowReleaseException;
    
    String getFeatureFinishName(ReleaseContext ctx, JGitFlow flow) throws JGitFlowReleaseException;
}
