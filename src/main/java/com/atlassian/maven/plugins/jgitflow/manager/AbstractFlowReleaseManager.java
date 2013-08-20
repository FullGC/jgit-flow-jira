package com.atlassian.maven.plugins.jgitflow.manager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.JGitFlowReporter;
import com.atlassian.maven.plugins.jgitflow.MavenJGitFlowConfiguration;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.JGitFlowReleaseException;
import com.atlassian.maven.plugins.jgitflow.exception.ProjectRewriteException;
import com.atlassian.maven.plugins.jgitflow.exception.ReactorReloadException;
import com.atlassian.maven.plugins.jgitflow.helper.MavenExecutionHelper;
import com.atlassian.maven.plugins.jgitflow.helper.ProjectHelper;
import com.atlassian.maven.plugins.jgitflow.rewrite.MavenProjectRewriter;
import com.atlassian.maven.plugins.jgitflow.rewrite.ProjectChangeset;
import com.atlassian.maven.plugins.jgitflow.util.ConsoleCredentialsProvider;

import com.google.common.base.Function;
import com.google.common.collect.Maps;
import com.jcraft.jsch.IdentityRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.agentproxy.AgentProxyException;
import com.jcraft.jsch.agentproxy.Connector;
import com.jcraft.jsch.agentproxy.RemoteIdentityRepository;
import com.jcraft.jsch.agentproxy.USocketFactory;
import com.jcraft.jsch.agentproxy.connector.SSHAgentConnector;
import com.jcraft.jsch.agentproxy.usocket.JNAUSocketFactory;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.RuntimeInformation;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.release.util.ReleaseUtil;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.util.FS;

import static com.atlassian.maven.plugins.jgitflow.rewrite.ArtifactReleaseVersionChange.artifactReleaseVersionChange;
import static com.atlassian.maven.plugins.jgitflow.rewrite.ParentReleaseVersionChange.parentReleaseVersionChange;
import static com.atlassian.maven.plugins.jgitflow.rewrite.ProjectReleaseVersionChange.projectReleaseVersionChange;

/**
 * @since version
 */
public abstract class AbstractFlowReleaseManager extends AbstractLogEnabled implements FlowReleaseManager
{
    protected static final String ls = System.getProperty("line.separator");
    private static final SecureRandom random = new SecureRandom();

    protected ProjectHelper projectHelper;
    protected MavenProjectRewriter projectRewriter;
    protected MavenExecutionHelper mavenExecutionHelper;
    protected MavenJGitFlowConfigManager configManager;
    protected RuntimeInformation runtimeInformation;

    private boolean sshAgentConfigured = false;
    private boolean sshConsoleInstalled = false;
    private boolean headerWritten = false;
    
    protected void writeReportHeader(ReleaseContext ctx, JGitFlowReporter reporter)
    {
        if(!headerWritten)
        {
            String mvnVersion = runtimeInformation.getApplicationVersion().toString();
            Package mvnFlowPkg = getClass().getPackage();
            String mvnFlowVersion = mvnFlowPkg.getImplementationVersion();
            
            String shortName = getClass().getSimpleName();
    
            reporter.debugText(shortName,"# Maven JGitFlow Plugin")
              .debugText(shortName,JGitFlowReporter.P)
              .debugText(shortName,"  ## Configuration")
              .debugText(shortName,JGitFlowReporter.EOL)
              .debugText(shortName,"    Maven Version: " + mvnVersion)
              .debugText(shortName,JGitFlowReporter.EOL)
              .debugText(shortName,"    Maven JGitFlow Plugin Version: " + mvnFlowVersion)
              .debugText(shortName,JGitFlowReporter.EOL)
              .debugText(shortName,"    args: " + ctx.getArgs())
              .debugText(shortName,"    base dir: " + ctx.getBaseDir().getAbsolutePath())
              .debugText(shortName,"    default development version: " + ctx.getDefaultDevelopmentVersion())
              .debugText(shortName,"    default feature name: " + ctx.getDefaultFeatureName())
              .debugText(shortName,"    default release version: " + ctx.getDefaultReleaseVersion())
              .debugText(shortName,"    release branch version suffix: " + ctx.getReleaseBranchVersionSuffix())
              .debugText(shortName,"    tag message: " + ctx.getTagMessage())
              .debugText(shortName,"    allow snapshots: " + ctx.isAllowSnapshots())
              .debugText(shortName,"    auto version submodules: " + ctx.isAutoVersionSubmodules())
              .debugText(shortName,"    enable feature versions: " + ctx.isEnableFeatureVersions())
              .debugText(shortName,"    enable ssh agent: " + ctx.isEnableSshAgent())
              .debugText(shortName,"    feature rebase: " + ctx.isFeatureRebase())
              .debugText(shortName,"    interactive: " + ctx.isInteractive())
              .debugText(shortName,"    keep branch: " + ctx.isKeepBranch())
              .debugText(shortName,"    no build: " + ctx.isNoBuild())
              .debugText(shortName,"    no deploy: " + ctx.isNoDeploy())
              .debugText(shortName,"    no tag: " + ctx.isNoTag())
              .debugText(shortName,"    pushFeatures: " + ctx.isPushFeatures())
              .debugText(shortName,"    pushReleases: " + ctx.isPushReleases())
              .debugText(shortName,"    pushHotfixes: " + ctx.isPushHotfixes())
              .debugText(shortName,"    squash: " + ctx.isSquash())
              .debugText(shortName,"    update dependencies: " + ctx.isUpdateDependencies())
              .debugText(shortName,"    use release profile: " + ctx.isUseReleaseProfile())
              .debugText(shortName,JGitFlowReporter.HR);
            
            reporter.flush();
            this.headerWritten = true;
        }
    }
    protected void setupCredentialProviders(ReleaseContext ctx, JGitFlowReporter reporter)
    {
        if(!ctx.isRemoteAllowed())
        {
            return;
        }

        if (!sshConsoleInstalled)
        {
            sshConsoleInstalled = projectHelper.setupUserPasswordCredentialsProvider(ctx, reporter);
        }

        if (!sshAgentConfigured)
        {
            sshAgentConfigured = projectHelper.setupSshCredentialsProvider(ctx, reporter);
        }
    }
    protected String getReleaseLabel(String key, ReleaseContext ctx, List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        Map<String, String> releaseVersions = projectHelper.getReleaseVersions(key, reactorProjects, ctx);
        MavenProject rootProject = ReleaseUtil.getRootProject(reactorProjects);
        String rootProjectId = ArtifactUtils.versionlessKey(rootProject.getGroupId(), rootProject.getArtifactId());
        return releaseVersions.get(rootProjectId);
    }

    protected String getHotfixLabel(String key, ReleaseContext ctx, List<MavenProject> reactorProjects, MavenJGitFlowConfiguration config) throws JGitFlowReleaseException
    {
        Map<String, String> hotfixVersions = projectHelper.getHotfixVersions(key, reactorProjects, ctx, config.getLastReleaseVersions());
        MavenProject rootProject = ReleaseUtil.getRootProject(reactorProjects);
        String rootProjectId = ArtifactUtils.versionlessKey(rootProject.getGroupId(), rootProject.getArtifactId());
        return hotfixVersions.get(rootProjectId);
    }

    protected String getDevelopmentLabel(String key, ReleaseContext ctx, List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        Map<String, String> developmentVersions = projectHelper.getDevelopmentVersions(key, reactorProjects, ctx);
        MavenProject rootProject = ReleaseUtil.getRootProject(reactorProjects);
        String rootProjectId = ArtifactUtils.versionlessKey(rootProject.getGroupId(), rootProject.getArtifactId());
        return developmentVersions.get(rootProjectId);
    }
    
    protected String getFeatureStartName(ReleaseContext ctx, JGitFlow flow) throws JGitFlowReleaseException
    {
        return projectHelper.getFeatureStartName(ctx, flow);
    }
    
    protected String getFeatureFinishName(ReleaseContext ctx, JGitFlow flow) throws JGitFlowReleaseException
    {
        return projectHelper.getFeatureFinishName(ctx, flow);
    }

    protected void updatePomsWithReleaseVersion(String key, final String releaseLabel, ReleaseContext ctx, List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        Map<String, String> originalVersions = projectHelper.getOriginalVersions(key, reactorProjects);
        Map<String, String> releaseSnapshotVersions = projectHelper.getOriginalVersions(key, reactorProjects);

        final String releaseSuffix = (StringUtils.isBlank(ctx.getReleaseBranchVersionSuffix())) ? "" : "-" + ctx.getReleaseBranchVersionSuffix();
        
        Map<String, String> releaseVersions = Maps.transformValues(releaseSnapshotVersions,new Function<String, String>() {
            @Override
            public String apply(String input)
            {
                if(input.equalsIgnoreCase(releaseLabel + releaseSuffix + "-SNAPSHOT"))
                {
                    return StringUtils.substringBeforeLast(input,releaseSuffix + "-SNAPSHOT");
                }
                else
                {
                    return input;
                }
            }
        });

        getLogger().info("updating poms for all projects...");
        if(!getLogger().isDebugEnabled())
        {
            getLogger().info("turn on debug logging with -X to see exact changes");
        }
        for (MavenProject project : reactorProjects)
        {
            ProjectChangeset changes = new ProjectChangeset()
                    .with(parentReleaseVersionChange(originalVersions, releaseVersions))
                    .with(projectReleaseVersionChange(releaseVersions))
                    .with(artifactReleaseVersionChange(originalVersions, releaseVersions, ctx.isUpdateDependencies()));
            try
            {
                getLogger().info("updating pom for " + project.getName() + "...");

                projectRewriter.applyChanges(project, changes);

                logChanges(changes);
            }
            catch (ProjectRewriteException e)
            {
                throw new JGitFlowReleaseException("Error updating poms with release versions", e);
            }
        }
    }

    protected void updatePomsWithReleaseSnapshotVersion(String key, final String releaseLabel, ReleaseContext ctx, List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        Map<String, String> originalVersions = projectHelper.getOriginalVersions(key, reactorProjects);
        Map<String, String> releaseVersions = projectHelper.getReleaseVersions(key, reactorProjects, ctx);
        
        final String releaseSuffix = (StringUtils.isBlank(ctx.getReleaseBranchVersionSuffix())) ? "" : "-" + ctx.getReleaseBranchVersionSuffix();
        Map<String, String> releaseSnapshotVersions = Maps.transformValues(releaseVersions,new Function<String, String>() {
            @Override
            public String apply(String input)
            {
                if(input.equalsIgnoreCase(releaseLabel))
                {
                    return input + releaseSuffix + "-SNAPSHOT";
                }
                else
                {
                    return input;
                }
            }
        });
        
        getLogger().info("updating poms for all projects...");
        if(!getLogger().isDebugEnabled())
        {
            getLogger().info("turn on debug logging with -X to see exact changes");
        }
        for (MavenProject project : reactorProjects)
        {
            ProjectChangeset changes = new ProjectChangeset()
                    .with(parentReleaseVersionChange(originalVersions, releaseSnapshotVersions))
                    .with(projectReleaseVersionChange(releaseSnapshotVersions))
                    .with(artifactReleaseVersionChange(originalVersions, releaseSnapshotVersions, ctx.isUpdateDependencies()));
            try
            {
                getLogger().info("updating pom for " + project.getName() + "...");

                projectRewriter.applyChanges(project, changes);

                logChanges(changes);
            }
            catch (ProjectRewriteException e)
            {
                throw new JGitFlowReleaseException("Error updating poms with release versions", e);
            }
        }
    }

    protected void updatePomsWithFeatureVersion(String key, final String featureVersion, ReleaseContext ctx, List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        Map<String, String> originalVersions = projectHelper.getOriginalVersions(key, reactorProjects);
        Map<String, String> featureVersions = projectHelper.getOriginalVersions(key, reactorProjects);

        Map<String, String> featureSuffixedVersions = Maps.transformValues(featureVersions,new Function<String, String>() {
            @Override
            public String apply(String input)
            {
                if(input.endsWith("-SNAPSHOT"))
                {
                    return StringUtils.substringBeforeLast(input,"-SNAPSHOT") + "-" + featureVersion + "-SNAPSHOT";
                }
                else
                {
                    return input;
                }
            }
        });

        getLogger().info("updating poms for all projects...");
        if(!getLogger().isDebugEnabled())
        {
            getLogger().info("turn on debug logging with -X to see exact changes");
        }
        for (MavenProject project : reactorProjects)
        {
            ProjectChangeset changes = new ProjectChangeset()
                    .with(parentReleaseVersionChange(originalVersions, featureSuffixedVersions))
                    .with(projectReleaseVersionChange(featureSuffixedVersions))
                    .with(artifactReleaseVersionChange(originalVersions, featureSuffixedVersions, ctx.isUpdateDependencies()));
            try
            {
                getLogger().info("updating pom for " + project.getName() + "...");

                projectRewriter.applyChanges(project, changes);

                logChanges(changes);
            }
            catch (ProjectRewriteException e)
            {
                throw new JGitFlowReleaseException("Error updating poms with feature versions", e);
            }
        }
    }

    protected void updatePomsWithNonFeatureVersion(String key, final String featureVersion, ReleaseContext ctx, List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        Map<String, String> originalVersions = projectHelper.getOriginalVersions(key, reactorProjects);
        Map<String, String> featureSuffixedVersions = projectHelper.getOriginalVersions(key, reactorProjects);

        final String featureSuffix = "-" + featureVersion + "-SNAPSHOT";

        Map<String, String> featureVersions = Maps.transformValues(featureSuffixedVersions,new Function<String, String>() {
            @Override
            public String apply(String input)
            {
                if(input.endsWith(featureSuffix))
                {
                    return StringUtils.substringBeforeLast(input,featureSuffix) + "-SNAPSHOT";
                }
                else
                {
                    return input;
                }
            }
        });

        getLogger().info("updating poms for all projects...");
        if(!getLogger().isDebugEnabled())
        {
            getLogger().info("turn on debug logging with -X to see exact changes");
        }
        for (MavenProject project : reactorProjects)
        {
            ProjectChangeset changes = new ProjectChangeset()
                    .with(parentReleaseVersionChange(originalVersions, featureVersions))
                    .with(projectReleaseVersionChange(featureVersions))
                    .with(artifactReleaseVersionChange(originalVersions, featureVersions, ctx.isUpdateDependencies()));
            try
            {
                getLogger().info("updating pom for " + project.getName() + "...");

                projectRewriter.applyChanges(project, changes);

                logChanges(changes);
            }
            catch (ProjectRewriteException e)
            {
                throw new JGitFlowReleaseException("Error updating poms with non-feature versions", e);
            }
        }
    }

    protected void updatePomsWithVersionCopy(ReleaseContext ctx, List<MavenProject> projectsToUpdate, List<MavenProject> projectsWithVersions) throws JGitFlowReleaseException
    {
        Map<String, String> originalVersions = projectHelper.getOriginalVersions(randomName("copy"), projectsToUpdate);
        Map<String, String> releaseVersions = projectHelper.getOriginalVersions(randomName("copy"), projectsWithVersions);

        getLogger().info("updating poms for all projects...");
        if(!getLogger().isDebugEnabled())
        {
            getLogger().info("turn on debug logging with -X to see exact changes");
        }
        for (MavenProject project : projectsToUpdate)
        {
            ProjectChangeset changes = new ProjectChangeset()
                    .with(parentReleaseVersionChange(originalVersions, releaseVersions))
                    .with(projectReleaseVersionChange(releaseVersions))
                    .with(artifactReleaseVersionChange(originalVersions, releaseVersions, ctx.isUpdateDependencies()));
            try
            {
                getLogger().info("updating pom for " + project.getName() + "...");

                projectRewriter.applyChanges(project, changes);

                logChanges(changes);
            }
            catch (ProjectRewriteException e)
            {
                throw new JGitFlowReleaseException("Error updating poms with release versions", e);
            }
        }
    }

    protected void updatePomsWithPreviousVersions(String key, ReleaseContext ctx, List<MavenProject> reactorProjects, MavenJGitFlowConfiguration config) throws JGitFlowReleaseException
    {
        Map<String, String> originalVersions = projectHelper.getOriginalVersions(key, reactorProjects);
        Map<String, String> preHotfixVersions = config.getPreHotfixVersions();
        
        if(null == preHotfixVersions || preHotfixVersions.isEmpty())
        {
            //uh, not sure what to do here other than set to next develop version
            updatePomsWithDevelopmentVersion(key, ctx,reactorProjects);
            
            return;
        }

        getLogger().info("updating poms for all projects...");
        if(!getLogger().isDebugEnabled())
        {
            getLogger().info("turn on debug logging with -X to see exact changes");
        }
        for (MavenProject project : reactorProjects)
        {
            ProjectChangeset changes = new ProjectChangeset()
                    .with(parentReleaseVersionChange(originalVersions, preHotfixVersions))
                    .with(projectReleaseVersionChange(preHotfixVersions))
                    .with(artifactReleaseVersionChange(originalVersions, preHotfixVersions, ctx.isUpdateDependencies()));
            try
            {
                getLogger().info("updating pom for " + project.getName() + "...");

                projectRewriter.applyChanges(project, changes);

                logChanges(changes);
            }
            catch (ProjectRewriteException e)
            {
                throw new JGitFlowReleaseException("Error updating poms with development versions", e);
            }
        }
    }
    
    protected void updatePomsWithHotfixVersion(String key, ReleaseContext ctx, List<MavenProject> reactorProjects, MavenJGitFlowConfiguration config) throws JGitFlowReleaseException
    {
        Map<String, String> originalVersions = projectHelper.getOriginalVersions(key, reactorProjects);
        Map<String, String> hotfixVersions = projectHelper.getHotfixVersions(key, reactorProjects, ctx, config.getLastReleaseVersions());

        getLogger().info("updating poms for all projects...");
        if(!getLogger().isDebugEnabled())
        {
            getLogger().info("turn on debug logging with -X to see exact changes");
        }
        for (MavenProject project : reactorProjects)
        {
            ProjectChangeset changes = new ProjectChangeset()
                    .with(parentReleaseVersionChange(originalVersions, hotfixVersions))
                    .with(projectReleaseVersionChange(hotfixVersions))
                    .with(artifactReleaseVersionChange(originalVersions, hotfixVersions, ctx.isUpdateDependencies()));
            try
            {
                getLogger().info("updating pom for " + project.getName() + "...");

                projectRewriter.applyChanges(project, changes);

                logChanges(changes);
            }
            catch (ProjectRewriteException e)
            {
                throw new JGitFlowReleaseException("Error updating poms with hotfix versions", e);
            }
        }
    }

    protected void updatePomsWithHotfixVersion(String key, final String hotfixLabel, ReleaseContext ctx, List<MavenProject> reactorProjects, MavenJGitFlowConfiguration config) throws JGitFlowReleaseException
    {
        Map<String, String> originalVersions = projectHelper.getOriginalVersions(key, reactorProjects);
        Map<String, String> hotfixSnapshotVersions = projectHelper.getOriginalVersions(key, reactorProjects);

        Map<String, String> hotfixVersions = Maps.transformValues(hotfixSnapshotVersions,new Function<String, String>() {
            @Override
            public String apply(String input)
            {
                if(input.equalsIgnoreCase(hotfixLabel + "-SNAPSHOT"))
                {
                    return StringUtils.substringBeforeLast(input,"-SNAPSHOT");
                }
                else
                {
                    return input;
                }
            }
        });

        getLogger().info("updating poms for all projects...");
        if(!getLogger().isDebugEnabled())
        {
            getLogger().info("turn on debug logging with -X to see exact changes");
        }
        for (MavenProject project : reactorProjects)
        {
            ProjectChangeset changes = new ProjectChangeset()
                    .with(parentReleaseVersionChange(originalVersions, hotfixVersions))
                    .with(projectReleaseVersionChange(hotfixVersions))
                    .with(artifactReleaseVersionChange(originalVersions, hotfixVersions, ctx.isUpdateDependencies()));
            try
            {
                getLogger().info("updating pom for " + project.getName() + "...");

                projectRewriter.applyChanges(project, changes);

                logChanges(changes);
            }
            catch (ProjectRewriteException e)
            {
                throw new JGitFlowReleaseException("Error updating poms with hotfix versions", e);
            }
        }
    }

    protected void updatePomsWithHotfixSnapshotVersion(String key, final String hotfixLabel, ReleaseContext ctx, List<MavenProject> reactorProjects, MavenJGitFlowConfiguration config) throws JGitFlowReleaseException
    {
        Map<String, String> originalVersions = projectHelper.getOriginalVersions(key, reactorProjects);
        Map<String, String> hotfixVersions = projectHelper.getHotfixVersions(key, reactorProjects, ctx,config.getLastReleaseVersions());

        Map<String, String> hotfixSnapshotVersions = Maps.transformValues(hotfixVersions,new Function<String, String>() {
            @Override
            public String apply(String input)
            {
                if(input.equalsIgnoreCase(hotfixLabel))
                {
                    return input + "-SNAPSHOT";
                }
                else
                {
                    return input;
                }
            }
        });

        getLogger().info("updating poms for all projects...");
        if(!getLogger().isDebugEnabled())
        {
            getLogger().info("turn on debug logging with -X to see exact changes");
        }
        for (MavenProject project : reactorProjects)
        {
            ProjectChangeset changes = new ProjectChangeset()
                    .with(parentReleaseVersionChange(originalVersions, hotfixSnapshotVersions))
                    .with(projectReleaseVersionChange(hotfixSnapshotVersions))
                    .with(artifactReleaseVersionChange(originalVersions, hotfixSnapshotVersions, ctx.isUpdateDependencies()));
            try
            {
                getLogger().info("updating pom for " + project.getName() + "...");

                projectRewriter.applyChanges(project, changes);

                logChanges(changes);
            }
            catch (ProjectRewriteException e)
            {
                throw new JGitFlowReleaseException("Error updating poms with release versions", e);
            }
        }
    }

    protected void updatePomsWithDevelopmentVersion(String key, ReleaseContext ctx, List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        Map<String, String> originalVersions = projectHelper.getOriginalVersions(key, reactorProjects);
        Map<String, String> developmentVersions = projectHelper.getDevelopmentVersions(key, reactorProjects, ctx);

        getLogger().info("updating poms for all projects...");
        
        if(!getLogger().isDebugEnabled())
        {
            getLogger().info("turn on debug logging with -X to see exact changes");
        }
        for (MavenProject project : reactorProjects)
        {
            ProjectChangeset changes = new ProjectChangeset()
                    .with(parentReleaseVersionChange(originalVersions, developmentVersions))
                    .with(projectReleaseVersionChange(developmentVersions))
                    .with(artifactReleaseVersionChange(originalVersions, developmentVersions, ctx.isUpdateDependencies()));
            try
            {
                getLogger().info("updating pom for " + project.getName() + "...");

                projectRewriter.applyChanges(project, changes);

                logChanges(changes);
            }
            catch (ProjectRewriteException e)
            {
                throw new JGitFlowReleaseException("Error updating poms with development versions", e);
            }
        }
    }

    protected void checkPomForSnapshot(List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        getLogger().info("Checking for SNAPSHOT version in projects...");
        boolean hasSnapshotProject = false;
        for (MavenProject project : reactorProjects)
        {
            if (ArtifactUtils.isSnapshot(project.getVersion()))
            {
                hasSnapshotProject = true;
                break;
            }
        }

        if (!hasSnapshotProject)
        {
            throw new JGitFlowReleaseException("Unable to find SNAPSHOT version in reactor projects!");
        }
    }

    protected void checkPomForRelease(List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        getLogger().info("Checking for release version in projects...");
        boolean hasSnapshotProject = false;
        for (MavenProject project : reactorProjects)
        {
            if (ArtifactUtils.isSnapshot(project.getVersion()))
            {
                hasSnapshotProject = true;
                break;
            }
        }

        if (hasSnapshotProject)
        {
            throw new JGitFlowReleaseException("Some reactor projects contain SNAPSHOT versions!");
        }
    }
    
    protected void logChanges(ProjectChangeset changes)
    {
        if(getLogger().isDebugEnabled())
        {
            for (String desc : changes.getChangeDescriptionsOrSummaries())
            {
                getLogger().debug("  " + desc);
            }
        }
    }
    
    protected MavenSession getSessionForBranch(JGitFlow flow, String branchName, List<MavenProject> originalProjects, MavenSession oldSession) throws GitAPIException, ReactorReloadException, IOException
    {
        String originalBranch = flow.git().getRepository().getBranch();
        
        flow.git().checkout().setName(branchName).call();

        //reload the reactor projects
        MavenProject rootProject = ReleaseUtil.getRootProject(originalProjects);
        MavenSession newSession = mavenExecutionHelper.reloadReactor(rootProject,oldSession);
        
        flow.git().checkout().setName(originalBranch).call();
        
        return newSession;
    }

    protected String randomName(String base)
    {
        long n = random.nextLong();
        n = (n == Long.MIN_VALUE) ? 0 : Math.abs(n);

        return base + Long.toString(n);
    }

    @Override
    public void deploy(ReleaseContext ctx, List<MavenProject> reactorProjects, MavenSession session, String buildNumber, String goals) throws JGitFlowReleaseException
    {
        //do nothing. override if you need to
    }
}
