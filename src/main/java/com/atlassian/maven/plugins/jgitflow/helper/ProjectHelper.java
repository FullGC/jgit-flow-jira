package com.atlassian.maven.plugins.jgitflow.helper;

import java.util.List;
import java.util.Map;

import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.JGitFlowReleaseException;

import org.apache.maven.project.MavenProject;

/**
 * @since version
 */
public interface ProjectHelper
{
    String getReleaseVersion(ReleaseContext ctx, MavenProject rootProject) throws JGitFlowReleaseException;

    String getDevelopmentVersion(ReleaseContext ctx, MavenProject rootProject) throws JGitFlowReleaseException;

    Map<String,String> getOriginalVersions(List<MavenProject> reactorProjects);

    Map<String,String> getReleaseVersions(List<MavenProject> reactorProjects, ReleaseContext ctx) throws JGitFlowReleaseException;

    Map<String,String> getDevelopmentVersions(List<MavenProject> reactorProjects, ReleaseContext ctx) throws JGitFlowReleaseException;
}
