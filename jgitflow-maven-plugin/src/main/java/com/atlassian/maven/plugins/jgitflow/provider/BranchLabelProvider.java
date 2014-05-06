package com.atlassian.maven.plugins.jgitflow.provider;

import java.util.List;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.VersionType;
import com.atlassian.maven.plugins.jgitflow.exception.JGitFlowReleaseException;

import org.apache.maven.project.MavenProject;

public interface BranchLabelProvider
{
    String getVersionLabel(VersionType versionType, ProjectCacheKey cacheKey, ReleaseContext ctx, List<MavenProject> reactorProjects) throws JGitFlowReleaseException;

    String getFeatureStartName(ReleaseContext ctx, JGitFlow flow) throws JGitFlowReleaseException;

    String getFeatureFinishName(ReleaseContext ctx, JGitFlow flow) throws JGitFlowReleaseException;
}
