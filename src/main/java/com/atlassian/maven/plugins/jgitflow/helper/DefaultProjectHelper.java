package com.atlassian.maven.plugins.jgitflow.helper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.JGitFlowReporter;
import com.atlassian.jgitflow.core.exception.JGitFlowGitAPIException;
import com.atlassian.jgitflow.core.exception.JGitFlowIOException;
import com.atlassian.jgitflow.core.util.GitHelper;
import com.atlassian.maven.plugins.jgitflow.PrettyPrompter;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.JGitFlowReleaseException;

import com.atlassian.maven.plugins.jgitflow.util.ConsoleCredentialsProvider;
import com.atlassian.maven.plugins.jgitflow.util.SshCredentialsProvider;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

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
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;
import org.apache.maven.shared.release.util.ReleaseUtil;
import org.apache.maven.shared.release.version.HotfixVersionInfo;
import org.apache.maven.shared.release.versions.DefaultVersionInfo;
import org.apache.maven.shared.release.versions.VersionParseException;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.util.FS;

import static com.google.common.collect.Lists.newArrayList;

/**
 * @since version
 */
public class DefaultProjectHelper extends AbstractLogEnabled implements ProjectHelper
{
    private static final String ls = System.getProperty("line.separator");
    private static String OS = System.getProperty("os.name").toLowerCase();
    private static boolean isWindows = (OS.indexOf("win") >= 0);

    private static boolean isCygwin = (isWindows && !Strings.isNullOrEmpty(System.getenv("TERM")));
    
    private PrettyPrompter prompter;
    private ArtifactFactory artifactFactory;
    private final Map<String, Map<String, String>> originalVersions;
    private final Map<String, Map<String, String>> releaseVersions;
    private final Map<String, Map<String, String>> developmentVersions;
    private final Map<String, Map<String, String>> hotfixVersions;

    public DefaultProjectHelper()
    {
        this.originalVersions = new HashMap<String, Map<String, String>>();
        this.releaseVersions = new HashMap<String, Map<String, String>>();
        this.developmentVersions = new HashMap<String, Map<String, String>>();
        this.hotfixVersions = new HashMap<String, Map<String, String>>();
    }

    @Override
    public void fixCygwinIfNeeded(JGitFlow flow) throws JGitFlowReleaseException
    {
        if(isCygwin)
        {
            getLogger().info("detected cygwin:");
            try
            {
                getLogger().info("    - turning off filemode...");
                
                StoredConfig config = flow.git().getRepository().getConfig();
                config.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_FILEMODE, false);
                config.save();
                config.load();
            }
            catch (IOException e)
            {
                throw new JGitFlowReleaseException("error configuring filemode for cygwin", e);
            }
            catch (ConfigInvalidException e)
            {
                throw new JGitFlowReleaseException("error configuring filemode for cygwin", e);
            }

            getLogger().info("    - fixing maven prompter...");
            prompter.setCygwinTerminal();
        }
    }

    @Override
    public String getReleaseVersion(ReleaseContext ctx, MavenProject rootProject) throws JGitFlowReleaseException
    {
        Logger log = getLogger();
        String defaultVersion = rootProject.getVersion();

        if (log.isDebugEnabled())
        {
            log.debug("calculating release version for " + rootProject.getGroupId() + ":" + rootProject.getArtifactId());
            log.debug("defaultVersion is currently: " + defaultVersion);
        }

        if (StringUtils.isNotBlank(ctx.getDefaultReleaseVersion()))
        {
            defaultVersion = ctx.getDefaultReleaseVersion();

            if (log.isDebugEnabled())
            {
                log.debug("(ctx change) defaultVersion is currently: " + defaultVersion);
            }
        }

        String suggestedVersion = null;
        String releaseVersion = defaultVersion;

        if (log.isDebugEnabled())
        {
            log.debug("releaseVersion is currently: " + releaseVersion);
        }

        while (null == releaseVersion || ArtifactUtils.isSnapshot(releaseVersion))
        {
            if (log.isDebugEnabled())
            {
                log.debug("looping until we find a non-snapshot version...");
            }

            DefaultVersionInfo info = null;
            try
            {
                info = new DefaultVersionInfo(rootProject.getVersion());
            }
            catch (VersionParseException e)
            {
                if (ctx.isInteractive())
                {
                    try
                    {
                        info = new DefaultVersionInfo("1.0");
                    }
                    catch (VersionParseException e1)
                    {
                        throw new JGitFlowReleaseException("error parsing 1.0 version!!!", e1);
                    }
                }
                else
                {
                    throw new JGitFlowReleaseException("error parsing release version: " + e.getMessage(), e);
                }
            }

            suggestedVersion = info.getReleaseVersionString();

            if (log.isDebugEnabled())
            {
                log.debug("suggestedVersion: " + suggestedVersion);
            }

            if (ctx.isInteractive())
            {
                String message = MessageFormat.format("What is the release version for \"{0}\"? ({1})", rootProject.getName(), ArtifactUtils.versionlessKey(rootProject.getGroupId(), rootProject.getArtifactId()));
                try
                {
                    releaseVersion = prompter.promptNotBlank(message, suggestedVersion);
                }
                catch (PrompterException e)
                {
                    throw new JGitFlowReleaseException("Error reading version from command line " + e.getMessage(), e);
                }
            }
            else
            {
                releaseVersion = suggestedVersion;

                if (log.isDebugEnabled())
                {
                    log.debug("setting release version to suggested version: " + suggestedVersion);
                }
            }

        }

        return releaseVersion;
    }

    @Override
    public String getHotfixVersion(ReleaseContext ctx, MavenProject rootProject, String lastRelease) throws JGitFlowReleaseException
    {
        String projectVersion = rootProject.getVersion();
        String defaultVersion = projectVersion;


        HotfixVersionInfo hotfixInfo = null;
        if (StringUtils.isNotBlank(lastRelease) && !ArtifactUtils.isSnapshot(lastRelease))
        {
            try
            {
                DefaultVersionInfo projectInfo = new DefaultVersionInfo(projectVersion);
                DefaultVersionInfo lastReleaseInfo = new DefaultVersionInfo(lastRelease);

                String higherVersion = projectVersion;

                if (projectInfo.isSnapshot())
                {
                    higherVersion = lastRelease;
                }
                else if (projectInfo.compareTo(lastReleaseInfo) < 1)
                {
                    higherVersion = lastRelease;
                }

                hotfixInfo = new HotfixVersionInfo(higherVersion);
                defaultVersion = hotfixInfo.getHotfixVersionString();
            }
            catch (VersionParseException e)
            {
                //just ignore
            }
        }
        else
        {
            try
            {
                hotfixInfo = new HotfixVersionInfo(projectVersion);
                defaultVersion = hotfixInfo.getHotfixVersionString();
            }
            catch (VersionParseException e)
            {
                //ignore
            }
        }

        String suggestedVersion = defaultVersion;
        String hotfixVersion = null;

        while (null == suggestedVersion || ArtifactUtils.isSnapshot(suggestedVersion))
        {
            HotfixVersionInfo info = null;
            try
            {
                info = new HotfixVersionInfo(rootProject.getVersion());
            }
            catch (VersionParseException e)
            {
                if (ctx.isInteractive())
                {
                    try
                    {
                        info = new HotfixVersionInfo("2.0");
                    }
                    catch (VersionParseException e1)
                    {
                        throw new JGitFlowReleaseException("error parsing 2.0 version!!!", e1);
                    }
                }
                else
                {
                    throw new JGitFlowReleaseException("error parsing release version: " + e.getMessage(), e);
                }
            }

            suggestedVersion = info.getDecrementedHotfixVersionString();
        }

        while (null == hotfixVersion || ArtifactUtils.isSnapshot(hotfixVersion))
        {
            if (ctx.isInteractive())
            {
                String message = MessageFormat.format("What is the hotfix version for \"{0}\"? ({1})", rootProject.getName(), ArtifactUtils.versionlessKey(rootProject.getGroupId(), rootProject.getArtifactId()));
                try
                {
                    hotfixVersion = prompter.promptNotBlank(message, suggestedVersion);
                }
                catch (PrompterException e)
                {
                    throw new JGitFlowReleaseException("Error reading version from command line " + e.getMessage(), e);
                }
            }
            else
            {
                hotfixVersion = suggestedVersion;
            }

        }

        return hotfixVersion;
    }

    @Override
    public String getDevelopmentVersion(ReleaseContext ctx, MavenProject rootProject) throws JGitFlowReleaseException
    {
        String defaultVersion = rootProject.getVersion();

        if (StringUtils.isNotBlank(ctx.getDefaultDevelopmentVersion()))
        {
            defaultVersion = ctx.getDefaultDevelopmentVersion();
        }

        String suggestedVersion = null;
        String developmentVersion = defaultVersion;

        while (null == developmentVersion || !ArtifactUtils.isSnapshot(developmentVersion))
        {
            DefaultVersionInfo info = null;
            try
            {
                info = new DefaultVersionInfo(rootProject.getVersion());
            }
            catch (VersionParseException e)
            {
                if (ctx.isInteractive())
                {
                    try
                    {
                        info = new DefaultVersionInfo("1.0");
                    }
                    catch (VersionParseException e1)
                    {
                        throw new JGitFlowReleaseException("error parsing 1.0 version!!!", e1);
                    }
                }
                else
                {
                    throw new JGitFlowReleaseException("error parsing development version: " + e.getMessage(), e);
                }
            }

            suggestedVersion = info.getNextVersion().getSnapshotVersionString();

            if (ctx.isInteractive())
            {
                String message = MessageFormat.format("What is the development version for \"{0}\"? ({1})", rootProject.getName(), ArtifactUtils.versionlessKey(rootProject.getGroupId(), rootProject.getArtifactId()));
                try
                {
                    developmentVersion = prompter.promptNotBlank(message, suggestedVersion);
                }
                catch (PrompterException e)
                {
                    throw new JGitFlowReleaseException("Error reading version from command line " + e.getMessage(), e);
                }
            }
            else
            {
                developmentVersion = suggestedVersion;
            }

        }

        return developmentVersion;
    }

    @Override
    public Map<String, String> getOriginalVersions(String key, List<MavenProject> reactorProjects)
    {
        if (!originalVersions.containsKey(key))
        {
            Map<String, String> versions = new HashMap<String, String>();

            for (MavenProject project : reactorProjects)
            {
                versions.put(ArtifactUtils.versionlessKey(project.getGroupId(), project.getArtifactId()), project.getVersion());
            }

            originalVersions.put(key, versions);
        }

        return ImmutableMap.copyOf(originalVersions.get(key));
    }

    @Override
    public Map<String, String> getReleaseVersions(String key, List<MavenProject> reactorProjects, ReleaseContext ctx) throws JGitFlowReleaseException
    {
        //todo: add getOriginalVersions here to pre-pop
        Logger log = getLogger();

        if (log.isDebugEnabled())
        {
            log.debug("getting release versions for key: " + key);
            log.debug("AutoVersionSubmodules: " + ctx.isAutoVersionSubmodules());
        }

        if (!releaseVersions.containsKey(key))
        {
            Map<String, String> versions = new HashMap<String, String>();

            MavenProject rootProject = ReleaseUtil.getRootProject(reactorProjects);

            if (log.isDebugEnabled())
            {
                log.debug("root project is snapshot: " + ArtifactUtils.isSnapshot(rootProject.getVersion()));
            }

            if (ctx.isAutoVersionSubmodules() && ArtifactUtils.isSnapshot(rootProject.getVersion()))
            {
                String rootProjectId = ArtifactUtils.versionlessKey(rootProject.getGroupId(), rootProject.getArtifactId());

                String rootReleaseVersion = getReleaseVersion(ctx, rootProject);

                if (log.isDebugEnabled())
                {
                    log.debug("getting release version for root project: " + rootProjectId);
                    log.debug("root release version is: " + rootReleaseVersion);
                }

                versions.put(rootProjectId, rootReleaseVersion);

                for (MavenProject subProject : reactorProjects)
                {
                    String subProjectId = ArtifactUtils.versionlessKey(subProject.getGroupId(), subProject.getArtifactId());
                    versions.put(subProjectId, rootReleaseVersion);
                }
            }
            else
            {
                for (MavenProject project : reactorProjects)
                {
                    String projectId = ArtifactUtils.versionlessKey(project.getGroupId(), project.getArtifactId());
                    String releaseVersion = getReleaseVersion(ctx, project);
                    versions.put(projectId, releaseVersion);
                }
            }

            releaseVersions.put(key, versions);
        }

        return ImmutableMap.copyOf(releaseVersions.get(key));

    }

    @Override
    public Map<String, String> getHotfixVersions(String key, List<MavenProject> reactorProjects, ReleaseContext ctx, Map<String, String> lastReleaseVersions) throws JGitFlowReleaseException
    {
        //todo: add getOriginalVersions here to pre-pop
        if (!hotfixVersions.containsKey(key))
        {
            Map<String, String> versions = new HashMap<String, String>();

            MavenProject rootProject = ReleaseUtil.getRootProject(reactorProjects);

            if (ctx.isAutoVersionSubmodules())
            {
                String rootProjectId = ArtifactUtils.versionlessKey(rootProject.getGroupId(), rootProject.getArtifactId());

                String lastRootRelease = "";

                if (null != lastReleaseVersions)
                {
                    lastRootRelease = lastReleaseVersions.get(rootProjectId);
                }

                String rootHotfixVersion = getHotfixVersion(ctx, rootProject, lastRootRelease);

                versions.put(rootProjectId, rootHotfixVersion);

                for (MavenProject subProject : reactorProjects)
                {
                    String subProjectId = ArtifactUtils.versionlessKey(subProject.getGroupId(), subProject.getArtifactId());
                    versions.put(subProjectId, rootHotfixVersion);
                }
            }
            else
            {
                for (MavenProject project : reactorProjects)
                {
                    String projectId = ArtifactUtils.versionlessKey(project.getGroupId(), project.getArtifactId());
                    String lastRelease = "";

                    if (null != lastReleaseVersions)
                    {
                        lastRelease = lastReleaseVersions.get(projectId);
                    }

                    String hotfixVersion = getHotfixVersion(ctx, project, lastRelease);
                    versions.put(projectId, hotfixVersion);
                }
            }

            hotfixVersions.put(key, versions);
        }

        return ImmutableMap.copyOf(hotfixVersions.get(key));
    }

    @Override
    public Map<String, String> getDevelopmentVersions(String key, List<MavenProject> reactorProjects, ReleaseContext ctx) throws JGitFlowReleaseException
    {
        //todo: add getOriginalVersions here to pre-pop
        if (!developmentVersions.containsKey(key))
        {
            Map<String, String> versions = new HashMap<String, String>();

            MavenProject rootProject = ReleaseUtil.getRootProject(reactorProjects);
            if (ctx.isAutoVersionSubmodules())
            {
                String rootProjectId = ArtifactUtils.versionlessKey(rootProject.getGroupId(), rootProject.getArtifactId());
                String rootDevelopmentVersion = getDevelopmentVersion(ctx, rootProject);

                versions.put(rootProjectId, rootDevelopmentVersion);

                for (MavenProject subProject : reactorProjects)
                {
                    String subProjectId = ArtifactUtils.versionlessKey(subProject.getGroupId(), subProject.getArtifactId());
                    versions.put(subProjectId, rootDevelopmentVersion);
                }
            }
            else
            {
                for (MavenProject project : reactorProjects)
                {
                    String projectId = ArtifactUtils.versionlessKey(project.getGroupId(), project.getArtifactId());
                    String developmentVersion = getDevelopmentVersion(ctx, project);
                    versions.put(projectId, developmentVersion);
                }
            }

            developmentVersions.put(key, versions);
        }

        return ImmutableMap.copyOf(developmentVersions.get(key));
    }

    @Override
    public void ensureOrigin(String defaultRemote, JGitFlow flow) throws JGitFlowReleaseException
    {
        getLogger().info("ensuring origin exists...");
        String newOriginUrl = defaultRemote;
        
        try
        {
            StoredConfig config = flow.git().getRepository().getConfig();
            String originUrl = config.getString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME, "url");
            if (Strings.isNullOrEmpty(originUrl) && !Strings.isNullOrEmpty(defaultRemote))
            {
                if(defaultRemote.startsWith("file://"))
                {
                    File originFile = new File(defaultRemote.substring(7));
                    newOriginUrl = "file://" + originFile.getCanonicalPath();
                }
                
                getLogger().info("adding origin from default...");
                config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME, "url", newOriginUrl);
                config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME, "fetch", "+refs/heads/*:refs/remotes/origin/*");
            }
            
            if(!Strings.isNullOrEmpty(originUrl) || !Strings.isNullOrEmpty(newOriginUrl))
            {
                if(Strings.isNullOrEmpty(config.getString(ConfigConstants.CONFIG_BRANCH_SECTION,flow.getMasterBranchName(),"remote")))
                {
                    config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, flow.getMasterBranchName(), "remote", Constants.DEFAULT_REMOTE_NAME);
                }
    
                if(Strings.isNullOrEmpty(config.getString(ConfigConstants.CONFIG_BRANCH_SECTION, flow.getMasterBranchName(), "merge")))
                {
                    config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, flow.getMasterBranchName(), "merge", Constants.R_HEADS + flow.getMasterBranchName());
                }
    
                if(Strings.isNullOrEmpty(config.getString(ConfigConstants.CONFIG_BRANCH_SECTION, flow.getDevelopBranchName(), "remote")))
                {
                    config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, flow.getDevelopBranchName(), "remote", Constants.DEFAULT_REMOTE_NAME);
                }
    
                if(Strings.isNullOrEmpty(config.getString(ConfigConstants.CONFIG_BRANCH_SECTION, flow.getDevelopBranchName(), "merge")))
                {
                    config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, flow.getDevelopBranchName(), "merge", Constants.R_HEADS + flow.getDevelopBranchName());
                }

                if (Strings.isNullOrEmpty(config.getString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME, "fetch"))) {
                    config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME, "fetch", "+refs/heads/*:refs/remotes/origin/*");
                }
                config.save();

                try
                {
                    config.load();
                    flow.git().fetch().setRemote(Constants.DEFAULT_REMOTE_NAME).call();
                }
                catch (Exception e)
                {
                    throw new JGitFlowReleaseException("error configuring remote git repo with url: " + newOriginUrl, e);
                }
                
            }

        }
        catch (IOException e)
        {
            throw new JGitFlowReleaseException("error configuring remote git repo with url: " + defaultRemote, e);
        }
    }


    @Override
    public void commitAllChanges(Git git, String message) throws JGitFlowReleaseException
    {
        try
        {
            Status status = git.status().call();
            if (!status.isClean())
            {
                git.add().addFilepattern(".").call();
                git.commit().setMessage(message).call();
            }
        }
        catch (GitAPIException e)
        {
            throw new JGitFlowReleaseException("error committing changes: " + e.getMessage(), e);
        }

    }

    @Override
    public void commitAllPoms(Git git, List<MavenProject> reactorProjects, String message) throws JGitFlowReleaseException
    {
        try
        {
            Status status = git.status().call();
            Repository repository = git.getRepository();
            File repoDir = repository.getDirectory().getParentFile();
            if (!status.isClean())
            {
                AddCommand add = git.add();

                MavenProject rootProject = ReleaseUtil.getRootProject(reactorProjects);
//                File rootBaseDir = rootProject.getBasedir();
                for (MavenProject project : reactorProjects)
                {
                    String pomPath = relativePath(repoDir, project.getFile());

                    if (getLogger().isDebugEnabled())
                    {
                        getLogger().debug("adding file pattern for poms commit: " + pomPath);
                    }
                    
                    if(isWindows)
                    {
                        pomPath = StringUtils.replace(pomPath,"\\","/");    
                    }
                    
                    add.addFilepattern(pomPath);
                }
                add.call();
                git.commit().setMessage(message).call();
            }
        }
        catch (GitAPIException e)
        {
            throw new JGitFlowReleaseException("error committing pom changes: " + e.getMessage(), e);
        }
    }

    private String relativePath(File basedir, File file)
    {
        String pomPath = file.getAbsolutePath();
        String basePath = basedir.getAbsolutePath();

        //Need to ingore case because it was comparing C:/repo/pom.xml to c:/repo
        if (pomPath.regionMatches(true, 0, basePath, 0, basePath.length())) 
        {
            pomPath = file.getAbsolutePath().substring(basedir.getAbsolutePath().length());

            if (pomPath.startsWith(File.separator)) 
            {
                pomPath = pomPath.substring(1);
            }
        }

        return pomPath;
    }

    @Override
    public List<String> checkForNonReactorSnapshots(String key, List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        List<String> snapshots = newArrayList();

        getLogger().info("Checking dependencies and plugins for snapshots ...");
        Map<String, String> originalReactorVersions = getOriginalVersions(key, reactorProjects);

        for (MavenProject project : reactorProjects)
        {
            snapshots.addAll(checkProjectForNonReactorSnapshots(project, originalReactorVersions));
        }

        return snapshots;
    }

    private List<String> checkProjectForNonReactorSnapshots(MavenProject project, Map<String, String> originalReactorVersions) throws JGitFlowReleaseException
    {
        List<String> snapshots = newArrayList();

        Map<String, Artifact> artifactMap = ArtifactUtils.artifactMapByVersionlessId(project.getArtifacts());
        if (project.getParentArtifact() != null)
        {
            String parentSnap = checkArtifact(getArtifactFromMap(project.getParentArtifact(), artifactMap), originalReactorVersions, AT_PARENT);
            if (!Strings.isNullOrEmpty(parentSnap))
            {
                snapshots.add(parentSnap);
            }
        }

        //Dependencies
        try
        {
            Set<Artifact> dependencyArtifacts = project.createArtifacts(artifactFactory, null, null);
            snapshots.addAll(checkArtifacts(dependencyArtifacts, originalReactorVersions, AT_DEPENDENCY));
        }
        catch (InvalidDependencyVersionException e)
        {
            throw new JGitFlowReleaseException("Failed to create dependency artifacts", e);
        }

        //Dependency Management
        DependencyManagement dmgnt = project.getDependencyManagement();
        if (null != dmgnt)
        {
            List<Dependency> mgntDependencies = dmgnt.getDependencies();
            if (null != mgntDependencies)
            {
                for (Dependency dep : mgntDependencies)
                {
                    String depSnap = checkArtifact(dep.getGroupId(), dep.getArtifactId(), dep.getVersion(), originalReactorVersions, AT_DEPENDENCY_MGNT);
                    if (!Strings.isNullOrEmpty(depSnap))
                    {
                        snapshots.add(depSnap);
                    }
                }
            }
        }

        //Plugins
        Set<Artifact> pluginArtifacts = project.getPluginArtifacts();
        snapshots.addAll(checkArtifacts(pluginArtifacts, originalReactorVersions, AT_PLUGIN));

//Plugin Management
        PluginManagement pmgnt = project.getPluginManagement();
        if (null != pmgnt)
        {
            List<Plugin> mgntPlugins = pmgnt.getPlugins();

            for (Plugin plugin : mgntPlugins)
            {
                String pluginSnap = checkArtifact(plugin.getGroupId(), plugin.getArtifactId(), plugin.getVersion(), originalReactorVersions, AT_PLUGIN_MGNT);
                if (!Strings.isNullOrEmpty(pluginSnap))
                {
                    snapshots.add(pluginSnap);
                }
            }
        }

        //Reports
        Set<Artifact> reportArtifacts = project.getReportArtifacts();
        snapshots.addAll(checkArtifacts(reportArtifacts, originalReactorVersions, AT_REPORT));

//Extensions
        Set<Artifact> extensionArtifacts = project.getExtensionArtifacts();
        snapshots.addAll(checkArtifacts(extensionArtifacts, originalReactorVersions, AT_EXTENSIONS));

        return snapshots;
    }

    private List<String> checkArtifacts(Set<Artifact> artifacts, Map<String, String> originalReactorVersions, String type)
    {
        List<String> snapshots = newArrayList();

        for (Artifact artifact : artifacts)
        {
            String snap = checkArtifact(artifact, originalReactorVersions, type);

            if (!Strings.isNullOrEmpty(snap))
            {
                snapshots.add(snap);
            }
        }

        return snapshots;
    }

    private String checkArtifact(Artifact artifact, Map<String, String> originalReactorVersions, String type)
    {
        String versionlessArtifactKey = ArtifactUtils.versionlessKey(artifact.getGroupId(), artifact.getArtifactId());

        boolean isSnapshot = (artifact.isSnapshot() && !artifact.getBaseVersion().equals(originalReactorVersions.get(versionlessArtifactKey)));

        if (isSnapshot)
        {
            return type + " - " + versionlessArtifactKey;
        }

        return null;
    }

    private String checkArtifact(String groupId, String artifactId, String version, Map<String, String> originalReactorVersions, String type)
    {
        String versionlessArtifactKey = ArtifactUtils.versionlessKey(groupId, artifactId);

        boolean isSnapshot = (ArtifactUtils.isSnapshot(version) && !version.equals(originalReactorVersions.get(versionlessArtifactKey)));

        if (isSnapshot)
        {
            return type + " - " + versionlessArtifactKey;
        }

        return null;
    }

    private Artifact getArtifactFromMap(Artifact originalArtifact, Map<String, Artifact> artifactMap)
    {
        String versionlessId = ArtifactUtils.versionlessKey(originalArtifact);
        Artifact checkArtifact = artifactMap.get(versionlessId);

        if (checkArtifact == null)
        {
            checkArtifact = originalArtifact;
        }
        return checkArtifact;
    }

    @Override
    public String getFeatureStartName(ReleaseContext ctx, JGitFlow flow) throws JGitFlowReleaseException
    {
        String featureName = StringUtils.defaultString(ctx.getDefaultFeatureName());

        if (ctx.isInteractive())
        {
            featureName = promptForFeatureName(flow.getFeatureBranchPrefix(), featureName);
        }
        else
        {
            if (StringUtils.isBlank(featureName))
            {
                throw new JGitFlowReleaseException("Missing featureName mojo option.");
            }
        }

        return featureName;
    }

    @Override
    public String getFeatureFinishName(ReleaseContext ctx, JGitFlow flow) throws JGitFlowReleaseException
    {
        String featureName = StringUtils.defaultString(ctx.getDefaultFeatureName());

        if (StringUtils.isBlank(featureName))
        {
            String currentBranch = null;

            try
            {
                currentBranch = flow.git().getRepository().getBranch();
                getLogger().debug("Current Branch is: " + currentBranch);
            }
            catch (IOException e)
            {
                throw new JGitFlowReleaseException(e);
            }

            getLogger().debug("Feature Prefix is: " + flow.getFeatureBranchPrefix());
            getLogger().debug("Branch starts with feature prefix?: " + currentBranch.startsWith(flow.getFeatureBranchPrefix()));
            if (currentBranch.startsWith(flow.getFeatureBranchPrefix()))
            {
                featureName = currentBranch.replaceFirst(flow.getFeatureBranchPrefix(), "");
            }
        }

        if (ctx.isInteractive())
        {
            List<String> possibleValues = new ArrayList<String>();
            if (null == featureName)
            {
                featureName = "";
            }

            try
            {
                String rheadPrefix = Constants.R_HEADS + flow.getFeatureBranchPrefix();
                List<Ref> branches = GitHelper.listBranchesWithPrefix(flow.git(), flow.getFeatureBranchPrefix(),flow.getReporter());

                for (Ref branch : branches)
                {
                    String simpleName = branch.getName().substring(branch.getName().indexOf(rheadPrefix) + rheadPrefix.length());
                    possibleValues.add(simpleName);
                }

                featureName = promptForExistingFeatureName(flow.getFeatureBranchPrefix(), featureName, possibleValues);
            }
            catch (JGitFlowGitAPIException e)
            {
                throw new JGitFlowReleaseException("Unable to determine feature names", e);
            }
        }
        else
        {
            if (StringUtils.isBlank(featureName))
            {
                throw new JGitFlowReleaseException("Missing featureName mojo option.");
            }
        }

        return featureName;
    }

    private String promptForFeatureName(String prefix, String defaultFeatureName) throws JGitFlowReleaseException
    {
        String message = "What is the feature branch name? " + prefix;
        String name = "";

        try
        {
            name = prompter.promptNotBlank(message, defaultFeatureName);
        }
        catch (PrompterException e)
        {
            throw new JGitFlowReleaseException("Error reading feature name from command line " + e.getMessage(), e);
        }

        return name;
    }

    private String promptForExistingFeatureName(String prefix, String defaultFeatureName, List<String> featureBranches) throws JGitFlowReleaseException
    {
        String message = "What is the feature branch name? " + prefix;

        String name = "";
        try
        {
            name = prompter.promptNumberedList(message, featureBranches, defaultFeatureName);
        }
        catch (PrompterException e)
        {
            throw new JGitFlowReleaseException("Error reading feature name from command line " + e.getMessage(), e);
        }

        return name;
    }

    @Override
    public boolean setupUserPasswordCredentialsProvider(ReleaseContext ctx, JGitFlowReporter reporter)
    {
        if (null != System.console())
        {
            reporter.debugText(getClass().getSimpleName(),"installing ssh console credentials provider");
            CredentialsProvider.setDefault(new ConsoleCredentialsProvider(prompter));
            return true;
        }

        return false;
    }

    @Override
    public boolean setupSshCredentialsProvider(ReleaseContext ctx, JGitFlowReporter reporter)
    {
        if (ctx.isEnableSshAgent())
        {
            reporter.debugText(getClass().getSimpleName(),"installing ssh-agent credentials provider");
            SshSessionFactory.setInstance(new SshCredentialsProvider());
            return true;
        }

        return false;
    }

}
