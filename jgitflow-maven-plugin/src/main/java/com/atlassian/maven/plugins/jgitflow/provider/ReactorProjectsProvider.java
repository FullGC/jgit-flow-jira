package com.atlassian.maven.plugins.jgitflow.provider;

import java.util.List;

import org.apache.maven.project.MavenProject;

public interface ReactorProjectsProvider
{
    List<MavenProject> getReactorProjects();
    void setReactorProjects(List<MavenProject> projects);
}
