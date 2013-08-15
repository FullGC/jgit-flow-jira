package com.atlassian.maven.plugins.jgitflow.helper;

import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.ReactorReloadException;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.shared.release.exec.MavenExecutorException;

/**
 * @since version
 */
public interface MavenExecutionHelper
{
    void execute(MavenProject rootProject, ReleaseContext ctx, MavenSession session) throws MavenExecutorException;
    void execute(MavenProject rootProject, ReleaseContext ctx, MavenSession session, String goals) throws MavenExecutorException;
    MavenSession reloadReactor(MavenProject rootProject, MavenSession oldSession) throws ReactorReloadException;
}
