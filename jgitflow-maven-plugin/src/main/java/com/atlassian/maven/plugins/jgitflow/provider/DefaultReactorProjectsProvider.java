package com.atlassian.maven.plugins.jgitflow.provider;

import java.util.List;

import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;

@Component(role = ReactorProjectsProvider.class)
public class DefaultReactorProjectsProvider implements ReactorProjectsProvider
{
    private static final DefaultReactorProjectsProvider INSTANCE = new DefaultReactorProjectsProvider();

    private List<MavenProject> projects;

    @Override
    public List<MavenProject> getReactorProjects()
    {
        return INSTANCE.projects;
    }

    @Override
    public void setReactorProjects(List<MavenProject> projects)
    {
        INSTANCE.projects = projects;
    }
}
