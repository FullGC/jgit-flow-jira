package com.atlassian.maven.plugins.jgitflow.helper;

import java.io.File;
import java.io.IOException;
import java.util.*;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.JGitFlowReporter;
import com.atlassian.jgitflow.core.exception.JGitFlowGitAPIException;
import com.atlassian.jgitflow.core.util.GitHelper;
import com.atlassian.maven.plugins.jgitflow.MavenJGitFlowConfiguration;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.VersionState;
import com.atlassian.maven.plugins.jgitflow.exception.JGitFlowReleaseException;
import com.atlassian.maven.plugins.jgitflow.util.ConsoleCredentialsProvider;
import com.atlassian.maven.plugins.jgitflow.util.SshCredentialsProvider;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

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
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

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

    //private PrettyPrompter prompter;
    private ArtifactFactory artifactFactory;
    private final Map<String, Map<String, String>> originalVersions;


    public DefaultProjectHelper()
    {
        this.originalVersions = new HashMap<String, Map<String, String>>();

    }

    @Override
    public void fixCygwinIfNeeded(JGitFlow flow) throws JGitFlowReleaseException
    {
        if (isCygwin)
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
    public Map<String, String> getOriginalVersions(ProjectCacheKey cacheKey, List<MavenProject> reactorProjects)
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
    public void ensureOrigin(String defaultRemote, boolean alwaysUpdateOrigin, JGitFlow flow) throws JGitFlowReleaseException
    {
        getLogger().info("ensuring origin exists...");
        String newOriginUrl = defaultRemote;

        try
        {
            StoredConfig config = flow.git().getRepository().getConfig();
            String originUrl = config.getString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME, "url");
            if ((Strings.isNullOrEmpty(originUrl) || alwaysUpdateOrigin) && !Strings.isNullOrEmpty(defaultRemote))
            {
                if (defaultRemote.startsWith("file://"))
                {
                    File originFile = new File(defaultRemote.substring(7));
                    newOriginUrl = "file://" + originFile.getCanonicalPath();
                }

                getLogger().info("adding origin from default...");
                config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME, "url", newOriginUrl);
                config.setString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME, "fetch", "+refs/heads/*:refs/remotes/origin/*");
            }

            if (!Strings.isNullOrEmpty(originUrl) || !Strings.isNullOrEmpty(newOriginUrl))
            {
                if (Strings.isNullOrEmpty(config.getString(ConfigConstants.CONFIG_BRANCH_SECTION, flow.getMasterBranchName(), "remote")))
                {
                    config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, flow.getMasterBranchName(), "remote", Constants.DEFAULT_REMOTE_NAME);
                }

                if (Strings.isNullOrEmpty(config.getString(ConfigConstants.CONFIG_BRANCH_SECTION, flow.getMasterBranchName(), "merge")))
                {
                    config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, flow.getMasterBranchName(), "merge", Constants.R_HEADS + flow.getMasterBranchName());
                }

                if (Strings.isNullOrEmpty(config.getString(ConfigConstants.CONFIG_BRANCH_SECTION, flow.getDevelopBranchName(), "remote")))
                {
                    config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, flow.getDevelopBranchName(), "remote", Constants.DEFAULT_REMOTE_NAME);
                }

                if (Strings.isNullOrEmpty(config.getString(ConfigConstants.CONFIG_BRANCH_SECTION, flow.getDevelopBranchName(), "merge")))
                {
                    config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, flow.getDevelopBranchName(), "merge", Constants.R_HEADS + flow.getDevelopBranchName());
                }

                if (Strings.isNullOrEmpty(config.getString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME, "fetch")))
                {
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

            File canonicalRepoDir;

            {
                // MJF-111. Canonicalize repoDir, getAbsolutePath is not enough
                File repoDir = repository.getDirectory().getParentFile();

                try
                {
                    canonicalRepoDir = repoDir.getCanonicalFile();
                }
                catch (IOException e)
                {
                    throw
                            new JGitFlowReleaseException(
                                    "Cannot get canonical name for repository directory <" +
                                            repoDir + ">",
                                    e
                            );
                }
            }

            if (!status.isClean())
            {
                AddCommand add = git.add();

                MavenProject rootProject = ReleaseUtil.getRootProject(reactorProjects);
//                File rootBaseDir = rootProject.getBasedir();
                for (MavenProject project : reactorProjects)
                {
                    String pomPath = relativePath(canonicalRepoDir, project.getFile());

                    if (getLogger().isDebugEnabled())
                    {
                        getLogger().debug("adding file pattern for poms commit: " + pomPath);
                    }

                    if (isWindows)
                    {
                        pomPath = StringUtils.replace(pomPath, "\\", "/");
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

    private String relativePath(File canonicalBasedir, File file) throws JGitFlowReleaseException
    {
        final String basePath = canonicalBasedir.getPath();

        String pomPath;

        try
        {
            // MJF-111. Canonicalize pomPath, getAbsolutePath is not enough
            pomPath = file.getCanonicalPath();
        }
        catch (IOException e)
        {
            throw
                    new JGitFlowReleaseException(
                            "Cannot get canonical name for pom file <" + file + ">",
                            e
                    );
        }

        final int basePathLen = basePath.length();

        //Need to ingore case because it was comparing C:/repo/pom.xml to c:/repo
        if (pomPath.regionMatches(true, 0, basePath, 0, basePathLen))
        {
            pomPath = pomPath.substring(basePathLen);

            if (pomPath.startsWith(File.separator))
            {
                pomPath = pomPath.substring(1);
            }
        }

        return pomPath;
    }

    @Override
    public void checkPomForVersionState(VersionState state, List<MavenProject> reactorProjects) throws JGitFlowReleaseException
    {
        getLogger().info("Checking for " + state.name() + " version in projects...");
        boolean hasSnapshotProject = false;
        for (MavenProject project : reactorProjects)
        {
            if (ArtifactUtils.isSnapshot(project.getVersion()))
            {
                hasSnapshotProject = true;
                break;
            }
        }

        if (!isStateValid(state, hasSnapshotProject))
        {
            String message = (VersionState.SNAPSHOT.equals(state)) ? "Unable to find SNAPSHOT version in reactor projects!" : "Some reactor projects contain SNAPSHOT versions!";
            throw new JGitFlowReleaseException(message);
        }
    }

    private boolean isStateValid(VersionState state, boolean hasSnapshot)
    {
        if ((VersionState.SNAPSHOT.equals(state) && hasSnapshot)
                || (VersionState.RELEASE.equals(state) && !hasSnapshot))
        {
            return true;
        }

        return false;
    }

    @Override
    public List<String> checkForNonReactorSnapshots(ProjectCacheKey cacheKey, List<MavenProject> reactorProjects) throws JGitFlowReleaseException
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
    public boolean setupUserPasswordCredentialsProvider(ReleaseContext ctx, JGitFlowReporter reporter)
    {
        if (!Strings.isNullOrEmpty(ctx.getPassword()) && !Strings.isNullOrEmpty(ctx.getUsername()))
        {
            reporter.debugText(getClass().getSimpleName(), "using provided username and password");
            CredentialsProvider.setDefault(new UsernamePasswordCredentialsProvider(ctx.getUsername(), ctx.getPassword()));
        }
        else if (null != System.console())
        {
            reporter.debugText(getClass().getSimpleName(), "installing ssh console credentials provider");
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
            reporter.debugText(getClass().getSimpleName(), "installing ssh-agent credentials provider");
            SshSessionFactory.setInstance(new SshCredentialsProvider());
            return true;
        }

        return false;
    }

}
