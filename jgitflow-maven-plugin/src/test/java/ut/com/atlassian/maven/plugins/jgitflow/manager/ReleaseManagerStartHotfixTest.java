package ut.com.atlassian.maven.plugins.jgitflow.manager;

import java.io.File;
import java.util.List;
import java.util.Properties;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.JGitFlowInitCommand;
import com.atlassian.jgitflow.core.exception.DirtyWorkingTreeException;
import com.atlassian.jgitflow.core.util.GitHelper;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.MavenJGitFlowException;
import com.atlassian.maven.plugins.jgitflow.exception.UnresolvedSnapshotsException;
import com.atlassian.maven.plugins.jgitflow.helper.JGitFlowSetupHelper;
import com.atlassian.maven.plugins.jgitflow.helper.ProjectHelper;
import com.atlassian.maven.plugins.jgitflow.manager.FlowReleaseManager;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.util.FileUtils;
import org.eclipse.jgit.api.Git;
import org.junit.Ignore;
import org.junit.Test;

import ut.com.atlassian.maven.plugins.jgitflow.testutils.RepoUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ReleaseManagerStartHotfixTest extends AbstractFlowManagerTest
{
    @Test(expected = MavenJGitFlowException.class)
    public void uncommittedChangesFails() throws Exception
    {
        String projectSubdir = "basic-pom";
        List<MavenProject> projects = createReactorProjects("rewrite-for-hotfix", projectSubdir);
        File projectRoot = projects.get(0).getBasedir();

        FlowReleaseManager relman = getHotfixManager();

        ReleaseContext ctx = new ReleaseContext(projectRoot);
        ctx.setInteractive(false).setNoTag(true);

        try
        {
            MavenSession session = new MavenSession(getContainer(), new Settings(), localRepository, null, null, null, projectRoot.getAbsolutePath(), new Properties(), new Properties(), null);

            relman.start(ctx, projects, session);
        }
        catch (MavenJGitFlowException e)
        {
            assertEquals(DirtyWorkingTreeException.class, e.getCause().getClass());
            throw e;
        }
    }

    @Test(expected = MavenJGitFlowException.class)
    public void existingSameHotfixIsThrown() throws Exception
    {
        String projectSubdir = "basic-pom";
        List<MavenProject> projects = createReactorProjects("rewrite-for-hotfix", projectSubdir);
        File projectRoot = projects.get(0).getBasedir();

        JGitFlow flow = JGitFlow.getOrInit(projectRoot);
        flow.git().checkout().setName(flow.getMasterBranchName()).call();

        assertOnMaster(flow);

        initialCommitAll(flow);

        flow.git().checkout().setCreateBranch(true).setName(flow.getHotfixBranchPrefix() + "1.0.1").call();

        //go back to develop
        flow.git().checkout().setName(flow.getMasterBranchName()).call();

        assertOnMaster(flow);

        assertTrue(GitHelper.localBranchExists(flow.git(), flow.getHotfixBranchPrefix() + "1.0.1"));

        FlowReleaseManager relman = getHotfixManager();

        ReleaseContext ctx = new ReleaseContext(projectRoot);
        ctx.setInteractive(false).setNoTag(true);

        MavenSession session = new MavenSession(getContainer(), new Settings(), localRepository, null, null, null, projectRoot.getAbsolutePath(), new Properties(), new Properties(), null);

        relman.start(ctx, projects, session);

        assertOnHotfix(flow);

        compareSnapPomFiles(projects);
    }

    @Test(expected = MavenJGitFlowException.class)
    public void existingDifferentReleaseThrows() throws Exception
    {
        String projectSubdir = "basic-pom";
        List<MavenProject> projects = createReactorProjects("rewrite-for-hotfix", projectSubdir);
        File projectRoot = projects.get(0).getBasedir();

        JGitFlow flow = JGitFlow.getOrInit(projectRoot);
        flow.git().checkout().setName(flow.getMasterBranchName()).call();

        assertOnMaster(flow);

        initialCommitAll(flow);

        flow.git().checkout().setCreateBranch(true).setName(flow.getHotfixBranchPrefix() + "0.2").call();

        //go back to develop
        flow.git().checkout().setName(flow.getMasterBranchName()).call();

        assertOnMaster(flow);

        assertTrue(GitHelper.localBranchExists(flow.git(), flow.getHotfixBranchPrefix() + "0.2"));

        FlowReleaseManager relman = getHotfixManager();

        ReleaseContext ctx = new ReleaseContext(projectRoot);
        ctx.setInteractive(false).setNoTag(true);

        MavenSession session = new MavenSession(getContainer(), new Settings(), localRepository, null, null, null, projectRoot.getAbsolutePath(), new Properties(), new Properties(), null);

        relman.start(ctx, projects, session);

        assertOnHotfix(flow);

        compareSnapPomFiles(projects);
    }

    @Test
    public void releaseBasicPom() throws Exception
    {
        basicHotfixRewriteTest("basic-pom");
    }

    @Test
    public void releaseWithNamespace() throws Exception
    {
        basicHotfixRewriteTest("basic-pom-namespace");
    }

    @Test
    public void releaseWithTagBase() throws Exception
    {
        basicHotfixRewriteTest("basic-pom-with-tag-base");
    }

    @Test(expected = MavenJGitFlowException.class)
    public void releaseWithInternalDifferingSnapshotDeps() throws Exception
    {
        try
        {
            basicHotfixRewriteTest("internal-differing-snapshot-dependencies");
        }
        catch (MavenJGitFlowException e)
        {
            assertEquals(UnresolvedSnapshotsException.class, e.getCause().getClass());
            throw e;
        }
    }

    @Test
    public void releaseWithInternalDifferingSnapshotDepsAllow() throws Exception
    {
        String projectName = "internal-differing-snapshot-dependencies";
        List<MavenProject> projects = createReactorProjects("rewrite-for-hotfix", projectName);
        File projectRoot = projects.get(0).getBasedir();

        ReleaseContext ctx = new ReleaseContext(projectRoot);

        ctx.setInteractive(false).setNoTag(true).setAllowSnapshots(true);

        basicHotfixRewriteTest(projectName, ctx);
    }

    @Test(expected = MavenJGitFlowException.class)
    public void releaseWithInternalDifferingSnapshotExtension() throws Exception
    {
        try
        {
            basicHotfixRewriteTest("internal-differing-snapshot-extension");
        }
        catch (MavenJGitFlowException e)
        {
            assertEquals(UnresolvedSnapshotsException.class, e.getCause().getClass());
            throw e;
        }
    }

    @Test
    public void releaseWithInternalDifferingSnapshotExtensionAllow() throws Exception
    {
        String projectName = "internal-differing-snapshot-extension";
        List<MavenProject> projects = createReactorProjects("rewrite-for-hotfix", projectName);
        File projectRoot = projects.get(0).getBasedir();

        ReleaseContext ctx = new ReleaseContext(projectRoot);

        ctx.setInteractive(false).setNoTag(true).setAllowSnapshots(true);

        basicHotfixRewriteTest(projectName, ctx);
    }

    @Test(expected = MavenJGitFlowException.class)
    public void releaseWithInternalDifferingSnapshotPlugins() throws Exception
    {
        try
        {
            basicHotfixRewriteTest("internal-differing-snapshot-plugins");
        }
        catch (MavenJGitFlowException e)
        {
            assertEquals(UnresolvedSnapshotsException.class, e.getCause().getClass());
            throw e;
        }
    }

    @Test
    public void releaseWithInternalDifferingSnapshotPluginsAllow() throws Exception
    {
        String projectName = "internal-differing-snapshot-plugins";
        List<MavenProject> projects = createReactorProjects("rewrite-for-hotfix", projectName);
        File projectRoot = projects.get(0).getBasedir();

        ReleaseContext ctx = new ReleaseContext(projectRoot);

        ctx.setInteractive(false).setNoTag(true).setAllowSnapshots(true);

        basicHotfixRewriteTest(projectName, ctx);
    }

    @Test(expected = MavenJGitFlowException.class)
    public void releaseWithInternalDifferingSnapshotReportPlugins() throws Exception
    {
        try
        {
            basicHotfixRewriteTest("internal-differing-snapshot-report-plugins");
        }
        catch (MavenJGitFlowException e)
        {
            assertEquals(UnresolvedSnapshotsException.class, e.getCause().getClass());
            throw e;
        }
    }

    @Test
    public void releaseWithInternalDifferingSnapshotReportPluginsAllow() throws Exception
    {
        String projectName = "internal-differing-snapshot-report-plugins";
        List<MavenProject> projects = createReactorProjects("rewrite-for-hotfix", projectName);
        File projectRoot = projects.get(0).getBasedir();

        ReleaseContext ctx = new ReleaseContext(projectRoot);

        ctx.setInteractive(false).setNoTag(true).setAllowSnapshots(true);

        basicHotfixRewriteTest(projectName, ctx);
    }

    @Test
    public void releaseWithInternalManagedSnapshotDeps() throws Exception
    {
        basicHotfixRewriteTest("internal-managed-snapshot-dependency");
    }

    @Test
    public void releaseWithInternalManagedSnapshotPlugin() throws Exception
    {
        basicHotfixRewriteTest("internal-managed-snapshot-plugin");
    }

    @Test
    public void releaseWithInternalSnapshotDeps() throws Exception
    {
        basicHotfixRewriteTest("internal-snapshot-dependencies");
    }

    @Test
    public void releaseWithInternalSnapshotExtension() throws Exception
    {
        basicHotfixRewriteTest("internal-snapshot-extension");
    }

    @Test
    public void releaseWithInternalSnapshotPluginDeps() throws Exception
    {
        basicHotfixRewriteTest("internal-snapshot-plugin-deps");
    }

    @Test
    public void releaseWithInternalSnapshotPlugins() throws Exception
    {
        basicHotfixRewriteTest("internal-snapshot-plugins");
    }

    @Test
    public void releaseWithInternalSnapshotProfile() throws Exception
    {
        basicHotfixRewriteTest("internal-snapshot-profile");
    }

    @Test
    public void releaseWithInternalSnapshotReportPlugins() throws Exception
    {
        basicHotfixRewriteTest("internal-snapshot-report-plugins");
    }

    @Test
    public void releaseWithInterpolatedVersions() throws Exception
    {
        basicHotfixRewriteTest("interpolated-versions");
    }

    @Test
    public void releaseWithDeepMultimodule() throws Exception
    {
        basicHotfixRewriteTest("multimodule-with-deep-subprojects");
    }

    @Test
    public void releaseWithMultimoduleAlternatePom() throws Exception
    {
        basicHotfixRewriteTest("multimodule-with-alternate-pom");
    }

    @Test
    public void releaseWithInheritedVersion() throws Exception
    {
        basicHotfixRewriteTest("pom-with-inherited-version");
    }

    @Test
    public void releaseWithParent() throws Exception
    {
        basicHotfixRewriteTest("pom-with-parent");
    }

    @Test
    public void releaseWithParentAndProperties() throws Exception
    {
        basicHotfixRewriteTest("pom-with-parent-and-properties");
    }

    @Test
    public void releaseWithFlatParent() throws Exception
    {
        List<MavenProject> projects = createReactorProjects("rewrite-for-hotfix/pom-with-parent-flat", "root-project");
        File projectRoot = projects.get(0).getBasedir();

        JGitFlow flow = JGitFlow.getOrInit(projectRoot);
        flow.git().checkout().setName(flow.getMasterBranchName()).call();

        assertOnMaster(flow);

        initialCommitAll(flow);
        FlowReleaseManager relman = getHotfixManager();

        ReleaseContext ctx = new ReleaseContext(projectRoot);
        ctx.setInteractive(false).setNoTag(true);

        MavenSession session = new MavenSession(getContainer(), new Settings(), localRepository, null, null, null, projectRoot.getAbsolutePath(), new Properties(), new Properties(), null);

        relman.start(ctx, projects, session);

        assertOnHotfix(flow);

        compareSnapPomFiles(projects);
    }

    @Test
    public void startReleaseWithMasterOnly() throws Exception
    {
        ProjectHelper projectHelper = (ProjectHelper) lookup(ProjectHelper.class.getName());
        JGitFlowSetupHelper setupHelper = (JGitFlowSetupHelper) lookup(JGitFlowSetupHelper.class.getName());

        Git git = null;
        Git remoteGit = null;

        List<MavenProject> remoteProjects = createReactorProjects("remote-git-project", null);

        File remoteDir = remoteProjects.get(0).getBasedir();

        //make sure we're clean
        File remoteGitDir = new File(remoteDir, ".git");
        if (remoteGitDir.exists())
        {
            FileUtils.cleanDirectory(remoteGitDir);
        }

        remoteGit = RepoUtil.createRepositoryWithMaster(remoteDir);
        projectHelper.commitAllChanges(remoteGit, "remote commit");

        File localProject = new File(testFileBase, "projects/local/local-git-project");
        git = Git.cloneRepository().setDirectory(localProject).setURI("file://" + remoteGit.getRepository().getWorkTree().getPath()).call();

        List<MavenProject> projects = createReactorProjects("remote-git-project", "local/local-git-project", null, false);
        File projectRoot = projects.get(0).getBasedir();

        JGitFlowInitCommand initCommand = new JGitFlowInitCommand();
        JGitFlow flow = initCommand.setDirectory(git.getRepository().getWorkTree()).call();

        ReleaseContext ctx = new ReleaseContext(projectRoot);
        ctx.setInteractive(false).setNoTag(true);

        flow.hotfixStart("1.0.1").call();

        assertOnHotfix(flow);


    }

}