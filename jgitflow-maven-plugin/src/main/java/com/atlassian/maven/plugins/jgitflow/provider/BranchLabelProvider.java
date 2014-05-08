package com.atlassian.maven.plugins.jgitflow.provider;

import java.util.List;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.VersionType;
import com.atlassian.maven.plugins.jgitflow.exception.JGitFlowReleaseException;

import org.apache.maven.project.MavenProject;

public interface BranchLabelProvider
{
    String getVersionLabel(VersionType versionType, ProjectCacheKey cacheKey, List<MavenProject> reactorProjects) throws JGitFlowReleaseException;

    String getFeatureStartName() throws JGitFlowReleaseException;

    String getFeatureFinishName() throws JGitFlowReleaseException;
}
