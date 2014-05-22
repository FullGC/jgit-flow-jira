package com.atlassian.maven.plugins.jgitflow.helper;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.atlassian.maven.plugins.jgitflow.VersionState;
import com.atlassian.maven.plugins.jgitflow.exception.MavenJGitFlowException;
import com.atlassian.maven.plugins.jgitflow.provider.ProjectCacheKey;
import com.atlassian.maven.plugins.jgitflow.provider.VersionProvider;

import com.google.common.base.Strings;

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
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;

import static com.google.common.collect.Lists.newArrayList;

/**
 * @since version
 */
@Component(role = ProjectHelper.class)
public class DefaultProjectHelper extends AbstractLogEnabled implements ProjectHelper
{
    private static String OS = System.getProperty("os.name").toLowerCase();
    private static boolean isWindows = (OS.indexOf("win") >= 0);

    @Requirement
    private ArtifactFactory artifactFactory;

    @Requirement
    private VersionProvider versionProvider;

    @Requirement
    private CurrentBranchHelper currentBranchHelper;

    @Override
    public void commitAllChanges(Git git, String message) throws MavenJGitFlowException
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
            throw new MavenJGitFlowException("error committing changes: " + e.getMessage(), e);
        }

    }

    @Override
    public void commitAllPoms(Git git, List<MavenProject> reactorProjects, String message) throws MavenJGitFlowException
    {
        String fullBranchName = currentBranchHelper.getBranchName();
        
        try
        {
            Status status = git.status().call();
            Repository repository = git.getRepository();

            if (getLogger().isDebugEnabled())
            {
                getLogger().debug("(" + fullBranchName + ") committing all poms on branch '" + repository.getBranch() + "'");
            }
            
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
                            new MavenJGitFlowException(
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
                        getLogger().debug("(" + fullBranchName + ") adding file pattern for poms commit: " + pomPath);
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
            throw new MavenJGitFlowException("error committing pom changes: " + e.getMessage(), e);
        }
        catch (IOException e)
        {
            throw new MavenJGitFlowException("error committing pom changes: " + e.getMessage(), e);
        }
    }

    private String relativePath(File canonicalBasedir, File file) throws MavenJGitFlowException
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
                    new MavenJGitFlowException(
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
    public void checkPomForVersionState(VersionState state, List<MavenProject> reactorProjects) throws MavenJGitFlowException
    {
        String fullBranchName = currentBranchHelper.getBranchName();
        
        getLogger().info("(" + fullBranchName + ") Checking for " + state.name() + " version in projects...");
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
            throw new MavenJGitFlowException(message);
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
    public List<String> checkForNonReactorSnapshots(ProjectCacheKey cacheKey, List<MavenProject> reactorProjects) throws MavenJGitFlowException
    {
        List<String> snapshots = newArrayList();

        String fullBranchName = currentBranchHelper.getBranchName();
        
        getLogger().info("(" + fullBranchName + ") Checking dependencies and plugins for snapshots ...");
        Map<String, String> originalReactorVersions = versionProvider.getOriginalVersions(cacheKey, reactorProjects);

        for (MavenProject project : reactorProjects)
        {
            snapshots.addAll(checkProjectForNonReactorSnapshots(project, originalReactorVersions));
        }

        return snapshots;
    }

    private List<String> checkProjectForNonReactorSnapshots(MavenProject project, Map<String, String> originalReactorVersions) throws MavenJGitFlowException
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
            throw new MavenJGitFlowException("Failed to create dependency artifacts", e);
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

}
