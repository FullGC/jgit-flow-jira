package com.atlassian.maven.plugins.jgitflow;

import java.util.List;
import java.util.Map;

import com.atlassian.maven.plugins.jgitflow.exception.ProjectRewriteException;
import com.atlassian.maven.plugins.jgitflow.helper.ProjectHelper;
import com.atlassian.maven.plugins.jgitflow.rewrite.ProjectChangeset;
import com.atlassian.maven.plugins.jgitflow.rewrite.ProjectRewriter;

import com.google.common.base.Function;
import com.google.common.collect.Maps;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static com.atlassian.maven.plugins.jgitflow.rewrite.ArtifactReleaseVersionChange.artifactReleaseVersionChange;
import static com.atlassian.maven.plugins.jgitflow.rewrite.ParentReleaseVersionChange.parentReleaseVersionChange;
import static com.atlassian.maven.plugins.jgitflow.rewrite.ProjectReleaseVersionChange.projectReleaseVersionChange;

@Mojo(name = "build-number", aggregator = true)
public class BuildNumberMojo extends AbstractJGitFlowMojo
{

    @Parameter(property = "buildNumber")
    private String buildNumber;

    @Parameter(defaultValue = "true", property = "updateDependencies")
    private boolean updateDependencies;

    @Component
    protected ProjectHelper projectHelper;

    @Component
    protected ProjectRewriter projectRewriter;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        List<MavenProject> reactorProjects = getReactorProjects();
        String key = "buildNum";

        Map<String, String> originalVersions = projectHelper.getOriginalVersions(key, reactorProjects);
        Map<String, String> featureVersions = projectHelper.getOriginalVersions(key, reactorProjects);

        Map<String, String> featureSuffixedVersions = Maps.transformValues(featureVersions, new Function<String, String>()
        {
            @Override
            public String apply(String input)
            {
                if (input.endsWith("-SNAPSHOT"))
                {
                    return StringUtils.substringBeforeLast(input, "-SNAPSHOT") + "-build" + buildNumber;
                }
                else
                {
                    return input;
                }
            }
        });

        for (MavenProject project : reactorProjects)
        {
            ProjectChangeset changes = new ProjectChangeset()
                    .with(parentReleaseVersionChange(originalVersions, featureSuffixedVersions))
                    .with(projectReleaseVersionChange(featureSuffixedVersions))
                    .with(artifactReleaseVersionChange(originalVersions, featureSuffixedVersions, updateDependencies));
            try
            {
                projectRewriter.applyChanges(project, changes);
            }
            catch (ProjectRewriteException e)
            {
                throw new MojoExecutionException("Error updating poms with build numbers versions", e);
            }
        }
    }
}
