package com.atlassian.maven.plugins.jgitflow.helper;

import java.util.List;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.maven.plugins.jgitflow.MavenJGitFlowConfiguration;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.JGitFlowReleaseException;

import org.apache.maven.project.MavenProject;

public interface BranchLabelProvider
{
    String getReleaseLabel(ProjectCacheKey cacheKey, ReleaseContext ctx, List<MavenProject> reactorProjects) throws JGitFlowReleaseException;
    String getHotfixLabel(ProjectCacheKey cacheKey, ReleaseContext ctx, List<MavenProject> reactorProjects, MavenJGitFlowConfiguration config) throws JGitFlowReleaseException;
    String getDevelopmentLabel(ProjectCacheKey cacheKey, ReleaseContext ctx, List<MavenProject> reactorProjects) throws JGitFlowReleaseException;
    String getFeatureStartName(ReleaseContext ctx, JGitFlow flow) throws JGitFlowReleaseException;
    String getFeatureFinishName(ReleaseContext ctx, JGitFlow flow) throws JGitFlowReleaseException;
}
