package com.atlassian.maven.plugins.jgitflow.helper;

import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

public class SessionAndProjects
{
    private final MavenSession session;
    private final List<MavenProject> projects;

    public SessionAndProjects(MavenSession session, List<MavenProject> projects)
    {
        this.session = session;
        this.projects = projects;
    }

    public MavenSession getSession()
    {
        return session;
    }

    public List<MavenProject> getProjects()
    {
        return projects;
    }
}
