package com.atlassian.maven.plugins.jgitflow.helper;

import com.atlassian.maven.plugins.jgitflow.ReleaseContext;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.release.exec.MavenExecutorException;

/**
 * @since version
 */
public interface MavenExecutionHelper
{
    void execute(MavenProject rootProject, ReleaseContext ctx, MavenSession session) throws MavenExecutorException;
}
