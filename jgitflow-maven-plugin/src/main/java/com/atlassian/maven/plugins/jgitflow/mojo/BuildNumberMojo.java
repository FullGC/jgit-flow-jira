package com.atlassian.maven.plugins.jgitflow.mojo;

import java.util.List;
import java.util.Map;

import com.atlassian.jgitflow.core.exception.JGitFlowException;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.MavenJGitFlowException;
import com.atlassian.maven.plugins.jgitflow.exception.ProjectRewriteException;
import com.atlassian.maven.plugins.jgitflow.helper.JGitFlowSetupHelper;
import com.atlassian.maven.plugins.jgitflow.helper.ProjectHelper;
import com.atlassian.maven.plugins.jgitflow.provider.*;
import com.atlassian.maven.plugins.jgitflow.rewrite.ProjectChangeset;
import com.atlassian.maven.plugins.jgitflow.rewrite.ProjectRewriter;

import com.google.common.base.Function;
import com.google.common.collect.Maps;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.execution.MavenSession;
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
    private boolean updateDependencies = true;

    @Parameter(defaultValue = "-build", property = "buildNumberVersionSuffix")
    private String buildNumberVersionSuffix = "-build";

    @Component
    protected ProjectHelper projectHelper;

    @Component
    protected ProjectRewriter projectRewriter;

    @Component
    protected VersionProvider versionProvider;

    @Component
    protected ContextProvider contextProvider;

    @Component
    protected MavenSessionProvider sessionProvider;

    @Component
    protected ReactorProjectsProvider projectsProvider;

    @Component
    protected JGitFlowSetupHelper setupHelper;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        List<MavenProject> reactorProjects = getReactorProjects();

        try
        {
            runPreflight(reactorProjects);
        }
        catch (Exception e)
        {
            throw new MojoExecutionException("Error setting up build number mojo: " + e.getMessage(), e);
        }

        Map<String, String> originalVersions = versionProvider.getOriginalVersions(ProjectCacheKey.BUILD_NUMBER, reactorProjects);

        Map<String, String> featureSuffixedVersions = Maps.transformValues(originalVersions, new Function<String, String>()
        {
            @Override
            public String apply(String input)
            {
                if (input.endsWith("-SNAPSHOT"))
                {
                    return StringUtils.substringBeforeLast(input, "-SNAPSHOT") + buildNumberVersionSuffix + buildNumber;
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

    protected void setupProviders(MavenSession session, List<MavenProject> projects)
    {
        contextProvider.setContext(new ReleaseContext(getBasedir()).setAlwaysUpdateOrigin(alwaysUpdateOrigin).setDefaultOriginUrl(defaultOriginUrl));
        sessionProvider.setSession(session);
        projectsProvider.setReactorProjects(projects);
    }

    public void runPreflight(List<MavenProject> reactorProjects) throws JGitFlowException, MavenJGitFlowException
    {
        setupProviders(session, reactorProjects);

        setupHelper.runCommonSetup();
    }
}
