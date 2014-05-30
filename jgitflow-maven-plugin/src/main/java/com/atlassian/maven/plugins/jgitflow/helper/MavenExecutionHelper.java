package com.atlassian.maven.plugins.jgitflow.helper;

import java.io.IOException;
import java.util.List;

import com.atlassian.jgitflow.core.exception.JGitFlowException;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.ReactorReloadException;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.shared.release.exec.MavenExecutorException;
import org.eclipse.jgit.api.errors.GitAPIException;

/**
 * @since version
 */
public interface MavenExecutionHelper
{
    void execute(MavenProject rootProject, MavenSession session) throws MavenExecutorException;
    void execute(MavenProject rootProject, MavenSession session, String goals) throws MavenExecutorException;
    MavenSession reloadReactor(MavenProject rootProject, MavenSession oldSession) throws ReactorReloadException;
    MavenSession getSessionForBranch(String branchName, MavenProject rootProject, MavenSession oldSession) throws JGitFlowException, IOException, GitAPIException, ReactorReloadException;
}
