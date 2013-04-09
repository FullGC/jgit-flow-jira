package com.atlassian.maven.plugins.jgitflow.manager;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.exception.HotfixBranchExistsException;
import com.atlassian.jgitflow.core.exception.JGitFlowException;
import com.atlassian.jgitflow.core.exception.JGitFlowIOException;
import com.atlassian.jgitflow.core.exception.ReleaseBranchExistsException;
import com.atlassian.jgitflow.core.util.GitHelper;
import com.atlassian.maven.plugins.jgitflow.MavenJGitFlowConfiguration;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.JGitFlowReleaseException;
import com.atlassian.maven.plugins.jgitflow.exception.ProjectRewriteException;
import com.atlassian.maven.plugins.jgitflow.helper.MavenExecutionHelper;
import com.atlassian.maven.plugins.jgitflow.helper.ProjectHelper;
import com.atlassian.maven.plugins.jgitflow.rewrite.MavenProjectRewriter;
import com.atlassian.maven.plugins.jgitflow.rewrite.ProjectChangeset;

import com.google.common.base.Strings;

import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Scm;
import org.apache.maven.project.MavenProject;
import org.apache.maven.scm.provider.ScmUrlUtils;
import org.apache.maven.shared.release.ReleaseExecutionException;
import org.apache.maven.shared.release.exec.MavenExecutorException;
import org.apache.maven.shared.release.util.ReleaseUtil;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.transport.RefSpec;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

import static com.atlassian.maven.plugins.jgitflow.rewrite.ArtifactReleaseVersionChange.artifactReleaseVersionChange;
import static com.atlassian.maven.plugins.jgitflow.rewrite.ParentReleaseVersionChange.parentReleaseVersionChange;
import static com.atlassian.maven.plugins.jgitflow.rewrite.ProjectReleaseVersionChange.projectReleaseVersionChange;
import static com.atlassian.maven.plugins.jgitflow.rewrite.ScmDefaultHeadTagChange.scmDefaultHeadTagChange;
import static com.atlassian.maven.plugins.jgitflow.rewrite.ScmDefaultTagChange.scmDefaultTagChange;

/**
 * @since version
 */
public abstract class AbstractFlowReleaseManager extends AbstractLogEnabled implements FlowReleaseManager
{
    private static final String ls = System.lineSeparator();
    protected ProjectHelper projectHelper;
    protected MavenProjectRewriter projectRewriter;
    protected MavenExecutionHelper mavenExecutionHelper;
    protected MavenJGitFlowConfigManager configManager;

    protected void startRelease(ReleaseContext ctx, List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {

        checkPomForSnapshot(reactorProjects);

        JGitFlow flow = null;
        String releaseLabel = getReleaseLabel(ctx, reactorProjects);
        try
        {
            flow = JGitFlow.getOrInit(ctx.getBaseDir(), ctx.getFlowInitContext());

            if(ctx.isPush() || !ctx.isNoTag())
            {
                ensureOrigin(reactorProjects, flow);
            }
            
            flow.releaseStart(releaseLabel).call();
        }
        catch (ReleaseBranchExistsException e)
        {
            //since the release branch already exists, just check it out
            try
            {
                flow.git().checkout().setName(flow.getReleaseBranchPrefix() + releaseLabel).call();
            }
            catch (GitAPIException e1)
            {
                throw new JGitFlowReleaseException("Error checking out existing release branch: " + e1.getMessage(), e1);
            }
        }
        catch (JGitFlowException e)
        {
            throw new JGitFlowReleaseException("Error starting release: " + e.getMessage(), e);
        }

        updatePomsWithReleaseVersion(ctx, reactorProjects);

        commitAllChanges(flow.git(), "updating poms for " + releaseLabel + " release");
    }

    protected void startHotfix(ReleaseContext ctx, List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        checkPomForSnapshot(reactorProjects);

        Map<String, String> originalVersions = projectHelper.getOriginalVersions(reactorProjects);

        JGitFlow flow = null;
        String hotfixLabel = "";
        MavenJGitFlowConfiguration config = null;
        try
        {
            flow = JGitFlow.getOrInit(ctx.getBaseDir(), ctx.getFlowInitContext());

            if(ctx.isPush() || !ctx.isNoTag())
            {
                ensureOrigin(reactorProjects, flow);
            }
            
            config = configManager.getConfiguration(flow.git());

            hotfixLabel = getHotfixLabel(ctx, reactorProjects, config);
            flow.hotfixStart(hotfixLabel).call();
        }
        catch (HotfixBranchExistsException e)
        {
            //since the release branch already exists, just check it out
            try
            {
                flow.git().checkout().setName(flow.getHotfixBranchPrefix() + hotfixLabel).call();
            }
            catch (GitAPIException e1)
            {
                throw new JGitFlowReleaseException("Error checking out existing hotfix branch: " + e1.getMessage(), e1);
            }
        }
        catch (JGitFlowException e)
        {
            throw new JGitFlowReleaseException("Error starting hotfix: " + e.getMessage(), e);
        }
        catch (IOException e)
        {
            throw new JGitFlowReleaseException("Error starting hotfix: " + e.getMessage(), e);
        }

        updatePomsWithHotfixVersion(ctx, reactorProjects, config);

        commitAllChanges(flow.git(), "updating poms for " + hotfixLabel + " hotfix");

        try
        {
            //save original versions to file so we can use them when we finish
            config.setPreHotfixVersions(originalVersions);
            configManager.saveConfiguration(config, flow.git());
        }
        catch (IOException e)
        {
            //just ignore for now
        }
    }

    protected void finishRelease(ReleaseContext ctx, List<MavenProject> reactorProjects, MavenSession session) throws JGitFlowReleaseException
    {
        checkPomForRelease(reactorProjects);
        JGitFlow flow = null;

        Map<String, String> originalVersions = projectHelper.getOriginalVersions(reactorProjects);
        String releaseLabel = getReleaseLabel(ctx, reactorProjects);
        MavenProject rootProject = ReleaseUtil.getRootProject(reactorProjects);
        
        if(!ctx.isNoBuild())
        {
            try
            {
                mavenExecutionHelper.execute(rootProject, ctx, session);
            }
            catch (MavenExecutorException e)
            {
                throw new JGitFlowReleaseException("Error building: " + e.getMessage(), e);
            }
        }
        
        try
        {
            flow = JGitFlow.getOrInit(ctx.getBaseDir(), ctx.getFlowInitContext());
            
            if(ctx.isPush() || !ctx.isNoTag())
            {
                ensureOrigin(reactorProjects, flow);
            }

            getLogger().info("running jgitflow release finish...");
            flow.releaseFinish(releaseLabel)
                .setPush(ctx.isPush())
                .setKeepBranch(ctx.isKeepBranch())
                .setNoTag(ctx.isNoTag())
                .setSquash(ctx.isSquash())
                .setMessage(ReleaseUtil.interpolate(ctx.getTagMessage(), rootProject.getModel()))
                .call();

            //make sure we're on develop
            flow.git().checkout().setName(flow.getDevelopBranchName()).call();
            
            String developLabel = getDevelopmentLabel(ctx, reactorProjects);
            updatePomsWithDevelopmentVersion(ctx, reactorProjects);

            commitAllChanges(flow.git(), "updating poms for " + developLabel + " development");
            
            if(ctx.isPush())
            {
                RefSpec developSpec = new RefSpec(ctx.getFlowInitContext().getDevelop());
                flow.git().push().setRemote(Constants.DEFAULT_REMOTE_NAME).setRefSpecs(developSpec).call();
            }

            MavenJGitFlowConfiguration config = configManager.getConfiguration(flow.git());
            config.setLastReleaseVersions(originalVersions);
            configManager.saveConfiguration(config,flow.git());
            
        }
        catch (JGitFlowException e)
        {
            throw new JGitFlowReleaseException("Error releasing: " + e.getMessage(), e);
        }
        catch (GitAPIException e)
        {
            throw new JGitFlowReleaseException("Error releasing: " + e.getMessage(), e);
        }
        catch (ReleaseExecutionException e)
        {
            throw new JGitFlowReleaseException("Error releasing: " + e.getMessage(), e);
        }
        catch (IOException e)
        {
            //ignore
        }
    }

    protected void finishHotfix(ReleaseContext ctx, List<MavenProject> reactorProjects, MavenSession session) throws JGitFlowReleaseException
    {
        checkPomForRelease(reactorProjects);
        JGitFlow flow = null;

        MavenProject rootProject = ReleaseUtil.getRootProject(reactorProjects);
        
        if(!ctx.isNoBuild())
        {
            try
            {
                mavenExecutionHelper.execute(rootProject, ctx, session);
            }
            catch (MavenExecutorException e)
            {
                throw new JGitFlowReleaseException("Error building: " + e.getMessage(), e);
            }
        }

        try
        {
            Map<String, String> originalVersions = projectHelper.getOriginalVersions(reactorProjects);
            flow = JGitFlow.getOrInit(ctx.getBaseDir(), ctx.getFlowInitContext());

            MavenJGitFlowConfiguration config = configManager.getConfiguration(flow.git());
            String hotfixLabel = getReleaseLabel(ctx, reactorProjects);
            
            //We need to commit the hotfix versioned poms to develop to avoid a merge conflict
            flow.git().checkout().setName(flow.getDevelopBranchName()).call();
            updatePomsWithReleaseVersion(ctx, reactorProjects);
            flow.git().add().addFilepattern(".").call();
            flow.git().commit().setMessage("updating develop with hotfix versions to avoid merge conflicts").call();
            
            flow.git().checkout().setName(flow.getHotfixBranchPrefix() + hotfixLabel);

            if(ctx.isPush() || !ctx.isNoTag())
            {
                ensureOrigin(reactorProjects, flow);
            }

            getLogger().info("running jgitflow hotfix finish...");
            flow.hotfixFinish(hotfixLabel)
                .setPush(ctx.isPush())
                .setKeepBranch(ctx.isKeepBranch())
                .setNoTag(ctx.isNoTag())
                .setMessage(ReleaseUtil.interpolate(ctx.getTagMessage(), rootProject.getModel()))
                .call();

            //make sure we're on develop
            flow.git().checkout().setName(flow.getDevelopBranchName()).call();

            updatePomsWithPreviousVersions(ctx, reactorProjects, config);

            commitAllChanges(flow.git(), "updating poms for development");

            config.setLastReleaseVersions(originalVersions);
            configManager.saveConfiguration(config,flow.git());
        }
        catch (JGitFlowException e)
        {
            throw new JGitFlowReleaseException("Error releasing: " + e.getMessage(), e);
        }
        catch (GitAPIException e)
        {
            throw new JGitFlowReleaseException("Error releasing: " + e.getMessage(), e);
        }
        catch (ReleaseExecutionException e)
        {
            throw new JGitFlowReleaseException("Error releasing: " + e.getMessage(), e);
        }
        catch (IOException e)
        {
            throw new JGitFlowReleaseException("Error releasing: " + e.getMessage(), e);
        }
    }

    protected String getReleaseLabel(ReleaseContext ctx, List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        Map<String, String> releaseVersions = projectHelper.getReleaseVersions(reactorProjects, ctx);
        MavenProject rootProject = ReleaseUtil.getRootProject(reactorProjects);
        String rootProjectId = ArtifactUtils.versionlessKey(rootProject.getGroupId(), rootProject.getArtifactId());
        return releaseVersions.get(rootProjectId);
    }

    protected String getHotfixLabel(ReleaseContext ctx, List<MavenProject> reactorProjects, MavenJGitFlowConfiguration config) throws JGitFlowReleaseException
    {
        Map<String, String> hotfixVersions = projectHelper.getHotfixVersions(reactorProjects, ctx, config.getLastReleaseVersions());
        MavenProject rootProject = ReleaseUtil.getRootProject(reactorProjects);
        String rootProjectId = ArtifactUtils.versionlessKey(rootProject.getGroupId(), rootProject.getArtifactId());
        return hotfixVersions.get(rootProjectId);
    }

    protected String getDevelopmentLabel(ReleaseContext ctx, List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        Map<String, String> developmentVersions = projectHelper.getDevelopmentVersions(reactorProjects, ctx);
        MavenProject rootProject = ReleaseUtil.getRootProject(reactorProjects);
        String rootProjectId = ArtifactUtils.versionlessKey(rootProject.getGroupId(), rootProject.getArtifactId());
        return developmentVersions.get(rootProjectId);
    }

    protected void updatePomsWithReleaseVersion(ReleaseContext ctx, List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        Map<String, String> originalVersions = projectHelper.getOriginalVersions(reactorProjects);
        Map<String, String> releaseVersions = projectHelper.getReleaseVersions(reactorProjects, ctx);

        getLogger().info("updating poms for all projects...");
        getLogger().info("turn on debug logging with -X to see exact changes");
        for (MavenProject project : reactorProjects)
        {
            ProjectChangeset changes = new ProjectChangeset()
                    .with(parentReleaseVersionChange(originalVersions, releaseVersions))
                    .with(projectReleaseVersionChange(releaseVersions))
                    .with(artifactReleaseVersionChange(originalVersions, releaseVersions, ctx.isUpdateDependencies()))
                    .with(scmDefaultTagChange(releaseVersions));
            try
            {
                getLogger().info("updating pom for " + project.getName() + "...");

                for (String desc : changes.getChangeDescriptionsOrSummaries())
                {
                    getLogger().debug("  " + desc);
                }

                projectRewriter.applyChanges(project, changes);
            }
            catch (ProjectRewriteException e)
            {
                throw new JGitFlowReleaseException("Error updating poms with release versions", e);
            }
        }
    }

    protected void updatePomsWithPreviousVersions(ReleaseContext ctx, List<MavenProject> reactorProjects, MavenJGitFlowConfiguration config) throws JGitFlowReleaseException
    {
        Map<String, String> originalVersions = projectHelper.getOriginalVersions(reactorProjects);
        Map<String, String> preHotfixVersions = config.getPreHotfixVersions();
        
        if(null == preHotfixVersions || preHotfixVersions.isEmpty())
        {
            //uh, not sure what to do here other than set to next develop version
            updatePomsWithDevelopmentVersion(ctx,reactorProjects);
            
            return;
        }

        getLogger().info("updating poms for all projects...");
        getLogger().info("turn on debug logging with -X to see exact changes");
        for (MavenProject project : reactorProjects)
        {
            ProjectChangeset changes = new ProjectChangeset()
                    .with(parentReleaseVersionChange(originalVersions, preHotfixVersions))
                    .with(projectReleaseVersionChange(preHotfixVersions))
                    .with(artifactReleaseVersionChange(originalVersions, preHotfixVersions, ctx.isUpdateDependencies()))
                    .with(scmDefaultTagChange(preHotfixVersions));
            try
            {
                getLogger().info("updating pom for " + project.getName() + "...");

                for (String desc : changes.getChangeDescriptionsOrSummaries())
                {
                    getLogger().debug("  " + desc);
                }

                projectRewriter.applyChanges(project, changes);
            }
            catch (ProjectRewriteException e)
            {
                throw new JGitFlowReleaseException("Error updating poms with development versions", e);
            }
        }
    }
    
    protected void updatePomsWithHotfixVersion(ReleaseContext ctx, List<MavenProject> reactorProjects, MavenJGitFlowConfiguration config) throws JGitFlowReleaseException
    {
        Map<String, String> originalVersions = projectHelper.getOriginalVersions(reactorProjects);
        Map<String, String> hotfixVersions = projectHelper.getHotfixVersions(reactorProjects, ctx, config.getLastReleaseVersions());

        getLogger().info("updating poms for all projects...");
        getLogger().info("turn on debug logging with -X to see exact changes");
        for (MavenProject project : reactorProjects)
        {
            ProjectChangeset changes = new ProjectChangeset()
                    .with(parentReleaseVersionChange(originalVersions, hotfixVersions))
                    .with(projectReleaseVersionChange(hotfixVersions))
                    .with(artifactReleaseVersionChange(originalVersions, hotfixVersions, ctx.isUpdateDependencies()))
                    .with(scmDefaultTagChange(hotfixVersions));
            try
            {
                getLogger().info("updating pom for " + project.getName() + "...");

                for (String desc : changes.getChangeDescriptionsOrSummaries())
                {
                    getLogger().debug("  " + desc);
                }

                projectRewriter.applyChanges(project, changes);
            }
            catch (ProjectRewriteException e)
            {
                throw new JGitFlowReleaseException("Error updating poms with hotfix versions", e);
            }
        }
    }

    protected void updatePomsWithDevelopmentVersion(ReleaseContext ctx, List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        Map<String, String> originalVersions = projectHelper.getOriginalVersions(reactorProjects);
        Map<String, String> developmentVersions = projectHelper.getDevelopmentVersions(reactorProjects, ctx);

        getLogger().info("updating poms for all projects...");
        getLogger().info("turn on debug logging with -X to see exact changes");
        for (MavenProject project : reactorProjects)
        {
            ProjectChangeset changes = new ProjectChangeset()
                    .with(parentReleaseVersionChange(originalVersions, developmentVersions))
                    .with(projectReleaseVersionChange(developmentVersions))
                    .with(artifactReleaseVersionChange(originalVersions, developmentVersions, ctx.isUpdateDependencies()))
                    .with(scmDefaultHeadTagChange());
            try
            {
                getLogger().info("updating pom for " + project.getName() + "...");

                for (String desc : changes.getChangeDescriptionsOrSummaries())
                {
                    getLogger().debug("  " + desc);
                }

                projectRewriter.applyChanges(project, changes);
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

    protected void commitAllChanges(Git git, String message) throws JGitFlowReleaseException
    {
        try
        {
            git.add().addFilepattern(".").call();
            git.commit().setMessage(message).call();
        }
        catch (GitAPIException e)
        {
            throw new JGitFlowReleaseException("error committing pom changes: " + e.getMessage(), e);
        }

    }

    private void ensureOrigin(List<MavenProject> reactorProjects, JGitFlow flow) throws JGitFlowReleaseException
    {
        getLogger().info("ensuring origin exists...");
        boolean foundGitScm = false;
        for (MavenProject project : reactorProjects)
        {
            Scm scm = project.getScm();
            if(null != scm)
            {
                File pomFile = project.getFile();

                if(null == pomFile || !pomFile.exists() || !pomFile.canRead())
                {
                    String pomPath = (null == pomFile) ? "null" : pomFile.getAbsolutePath();

                    throw new JGitFlowReleaseException("pom file must be readable! " + pomPath);
                }

                try
                {
                    String content = ReleaseUtil.readXmlFile(pomFile, ls);
                    SAXBuilder builder = new SAXBuilder();
                    Document document = builder.build(new StringReader( content ));
                    Element root = document.getRootElement();

                    Element scmElement = root.getChild("scm", root.getNamespace());

                    if(null != scmElement)
                    {
                        String scmUrl = (null != scm.getDeveloperConnection()) ? scm.getDeveloperConnection() : scm.getConnection();

                        String delimiter = ScmUrlUtils.getDelimiter(scmUrl);

                        String cleanScmUrl = scmUrl.substring(4);
                        int gitDelimiterIndex = cleanScmUrl.indexOf(delimiter);

                        cleanScmUrl = cleanScmUrl.substring(gitDelimiterIndex + 1, cleanScmUrl.length());
                        
                        URI uri = new URI(cleanScmUrl);
                        
                        String scheme = uri.getScheme();
                        if("ssh".equals(scheme))
                        {
                            cleanScmUrl = uri.getAuthority() + ":" + uri.getPath().substring(1);
                        }
                        
                        if(!Strings.isNullOrEmpty(scmUrl) && "git".equals(ScmUrlUtils.getProvider(scmUrl)))
                        {
                            foundGitScm = true;
                            StoredConfig config = flow.git().getRepository().getConfig();
                            String originUrl = config.getString(ConfigConstants.CONFIG_REMOTE_SECTION,Constants.DEFAULT_REMOTE_NAME,"url");
                            if(Strings.isNullOrEmpty(originUrl) || !cleanScmUrl.equals(originUrl))
                            {
                                getLogger().info("adding origin from scm...");
                                config.setString(ConfigConstants.CONFIG_REMOTE_SECTION,Constants.DEFAULT_REMOTE_NAME,"url",cleanScmUrl);
                                config.setString(ConfigConstants.CONFIG_REMOTE_SECTION,Constants.DEFAULT_REMOTE_NAME,"fetch","+refs/heads/*:refs/remotes/origin/*");
                                config.setString(ConfigConstants.CONFIG_BRANCH_SECTION,flow.getMasterBranchName(),"remote",Constants.DEFAULT_REMOTE_NAME);
                                config.setString(ConfigConstants.CONFIG_BRANCH_SECTION,flow.getMasterBranchName(),"merge",Constants.R_HEADS + flow.getMasterBranchName());
                                config.setString(ConfigConstants.CONFIG_BRANCH_SECTION,flow.getDevelopBranchName(),"remote",Constants.DEFAULT_REMOTE_NAME);
                                config.setString(ConfigConstants.CONFIG_BRANCH_SECTION,flow.getDevelopBranchName(),"merge",Constants.R_HEADS + flow.getDevelopBranchName());
                                config.save();

                                try
                                {
                                    config.load();
                                    flow.git().fetch().setRemote(Constants.DEFAULT_REMOTE_NAME).call();
                                }
                                catch (Exception e)
                                {
                                    throw new JGitFlowReleaseException("error configuring remote git repo", e);
                                }

                                getLogger().info("pulling changes from new origin...");
                                Ref originMaster = GitHelper.getRemoteBranch(flow.git(),flow.getMasterBranchName());
                                Ref localMaster = GitHelper.getLocalBranch(flow.git(),flow.getMasterBranchName());
                                RefUpdate update = flow.git().getRepository().updateRef(localMaster.getName());
                                update.setNewObjectId(originMaster.getObjectId());
                                update.forceUpdate();

                                Ref originDevelop = GitHelper.getRemoteBranch(flow.git(),flow.getDevelopBranchName());
                                Ref localDevelop = GitHelper.getLocalBranch(flow.git(),flow.getDevelopBranchName());
                                RefUpdate updateDevelop = flow.git().getRepository().updateRef(localDevelop.getName());
                                updateDevelop.setNewObjectId(originDevelop.getObjectId());
                                updateDevelop.forceUpdate();
                                
                                commitAllChanges(flow.git(),"committing changes from new origin");
                            }
                        }
                    }
                }
                catch (IOException e)
                {
                    throw new JGitFlowReleaseException("error configuring remote git repo", e);
                }
                catch (JDOMException e)
                {
                    throw new JGitFlowReleaseException("error configuring remote git repo", e);
                }
                catch (JGitFlowIOException e)
                {
                    throw new JGitFlowReleaseException("error configuring remote git repo", e);
                }
                catch (URISyntaxException e)
                {
                    throw new JGitFlowReleaseException("error configuring remote git repo", e);
                }
            }
        }

        if(!foundGitScm)
        {
            throw new JGitFlowReleaseException("No GIT Scm url found in reactor projects!");
        }
    }
}
