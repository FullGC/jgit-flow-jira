package com.atlassian.maven.plugins.jgitflow.rewrite;

import com.atlassian.maven.plugins.jgitflow.exception.ProjectRewriteException;

import org.apache.maven.project.MavenProject;
import org.jdom2.Element;

/**
 * @since version
 */
public interface ProjectChange
{
    boolean applyChange(MavenProject project, Element root) throws ProjectRewriteException;
}
