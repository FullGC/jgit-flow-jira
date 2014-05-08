package com.atlassian.maven.plugins.jgitflow.manager;

import java.util.List;

import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.JGitFlowReleaseException;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

/**
 * @since version
 */
public interface FlowReleaseManager
{
    void start(List<MavenProject> reactorProjects, MavenSession session) throws JGitFlowReleaseException;
    void finish(List<MavenProject> reactorProjects, MavenSession session) throws JGitFlowReleaseException;
    void deploy(List<MavenProject> reactorProjects, MavenSession session, String buildNumber, String goals) throws JGitFlowReleaseException;
}
