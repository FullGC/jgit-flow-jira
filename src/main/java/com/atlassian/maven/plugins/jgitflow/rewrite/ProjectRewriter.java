package com.atlassian.maven.plugins.jgitflow.rewrite;

import com.atlassian.maven.plugins.jgitflow.exception.ProjectRewriteException;

import org.apache.maven.project.MavenProject;

/**
 * @since version
 */
public interface ProjectRewriter
{
    void applyChanges(MavenProject project, ProjectChangeset changes) throws ProjectRewriteException;
}
