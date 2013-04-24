package ut.com.atlassian.maven.plugins.jgitflow.manager;

import java.io.File;
import java.util.List;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.JGitFlowInitCommand;
import com.atlassian.jgitflow.core.exception.DirtyWorkingTreeException;
import com.atlassian.jgitflow.core.util.GitHelper;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.JGitFlowReleaseException;
import com.atlassian.maven.plugins.jgitflow.helper.DefaultProjectHelper;
import com.atlassian.maven.plugins.jgitflow.helper.ProjectHelper;
import com.atlassian.maven.plugins.jgitflow.manager.FlowReleaseManager;

import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.codehaus.plexus.util.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.junit.Test;

import ut.com.atlassian.maven.plugins.jgitflow.testutils.RepoUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @since version
 */
public class ReleaseManagerStartReleaseTest extends AbstractFlowManagerTest
{
    @Test(expected = JGitFlowReleaseException.class)
    public void uncommittedChangesFails() throws Exception
    {
        String projectSubdir = "basic-pom";
        List<MavenProject> projects = createReactorProjects("rewrite-for-release",projectSubdir);
        File projectRoot = projects.get(0).getBasedir();
        
        FlowReleaseManager relman = getReleaseManager();
        
        ReleaseContext ctx = new ReleaseContext(projectRoot);
        ctx.setDefaultReleaseVersion("1.0");
        ctx.setInteractive(false).setNoTag(true).setPush(false);

        try
        {
            relman.start(ctx,projects);
        }
        catch (JGitFlowReleaseException e)
        {
            assertEquals(DirtyWorkingTreeException.class,e.getCause().getClass());
            throw e;
        }
    }

    @Test
    public void existingSameReleaseIsCheckedOut() throws Exception
    {
        String projectSubdir = "basic-pom";
        List<MavenProject> projects = createReactorProjects("rewrite-for-release",projectSubdir);
        File projectRoot = projects.get(0).getBasedir();

        JGitFlow flow = JGitFlow.getOrInit(projectRoot);

        assertOnDevelop(flow);

        initialCommitAll(flow);
        
        flow.git().checkout().setCreateBranch(true).setName(flow.getReleaseBranchPrefix() + "1.0").call();
        
        //go back to develop
        flow.git().checkout().setName(flow.getDevelopBranchName()).call();
        
        assertOnDevelop(flow);
        
        assertTrue(GitHelper.localBranchExists(flow.git(),flow.getReleaseBranchPrefix() + "1.0"));
        
        FlowReleaseManager relman = getReleaseManager();

        ReleaseContext ctx = new ReleaseContext(projectRoot);
        ctx.setInteractive(false).setNoTag(true).setPush(false);
        
        relman.start(ctx,projects);

        assertOnRelease(flow, ctx.getDefaultReleaseVersion());

        comparePomFiles(projects);
    }

    @Test(expected = JGitFlowReleaseException.class)
    public void existingDifferentReleaseThrows() throws Exception
    {
        String projectSubdir = "basic-pom";
        List<MavenProject> projects = createReactorProjects("rewrite-for-release",projectSubdir);
        File projectRoot = projects.get(0).getBasedir();

        JGitFlow flow = JGitFlow.getOrInit(projectRoot);

        assertOnDevelop(flow);

        initialCommitAll(flow);

        flow.git().checkout().setCreateBranch(true).setName(flow.getReleaseBranchPrefix() + "0.2").call();

        //go back to develop
        flow.git().checkout().setName(flow.getDevelopBranchName()).call();

        assertOnDevelop(flow);

        assertTrue(GitHelper.localBranchExists(flow.git(),flow.getReleaseBranchPrefix() + "0.2"));

        FlowReleaseManager relman = getReleaseManager();

        ReleaseContext ctx = new ReleaseContext(projectRoot);
        ctx.setInteractive(false).setNoTag(true).setPush(false);

        relman.start(ctx,projects);

        assertOnRelease(flow, ctx.getDefaultReleaseVersion());

        comparePomFiles(projects);
    }

    @Test
    public void releaseBasicPom() throws Exception
    {
        basicReleaseRewriteTest("basic-pom","1.0");
    }

    @Test
    public void releaseWithInheritedScm() throws Exception
    {
        basicReleaseRewriteTest("basic-pom-inherited-scm");
    }

    @Test
    public void releaseWithNamespace() throws Exception
    {
        basicReleaseRewriteTest("basic-pom-namespace");
    }

    @Test
    public void releaseWithTagBase() throws Exception
    {
        basicReleaseRewriteTest("basic-pom-with-tag-base");
    }

    @Test
    public void releaseWithInternalDifferingSnapshotDeps() throws Exception
    {
        basicReleaseRewriteTest("internal-differing-snapshot-dependencies");
    }

    @Test
    public void releaseWithInternalDifferingSnapshotExtension() throws Exception
    {
        basicReleaseRewriteTest("internal-differing-snapshot-extension");
    }

    @Test
    public void releaseWithInternalDifferingSnapshotPlugins() throws Exception
    {
        basicReleaseRewriteTest("internal-differing-snapshot-plugins");
    }

    @Test
    public void releaseWithInternalDifferingSnapshotReportPlugins() throws Exception
    {
        basicReleaseRewriteTest("internal-differing-snapshot-report-plugins");
    }

    @Test
    public void releaseWithInternalManagedSnapshotDeps() throws Exception
    {
        basicReleaseRewriteTest("internal-managed-snapshot-dependency");
    }

    @Test
    public void releaseWithInternalManagedSnapshotPlugin() throws Exception
    {
        basicReleaseRewriteTest("internal-managed-snapshot-plugin");
    }

    @Test
    public void releaseWithInternalSnapshotDeps() throws Exception
    {
        basicReleaseRewriteTest("internal-snapshot-dependencies");
    }

    @Test
    public void releaseWithInternalSnapshotExtension() throws Exception
    {
        basicReleaseRewriteTest("internal-snapshot-extension");
    }

    @Test
    public void releaseWithInternalSnapshotPluginDeps() throws Exception
    {
        basicReleaseRewriteTest("internal-snapshot-plugin-deps");
    }

    @Test
    public void releaseWithInternalSnapshotPlugins() throws Exception
    {
        basicReleaseRewriteTest("internal-snapshot-plugins");
    }

    @Test
    public void releaseWithInternalSnapshotProfile() throws Exception
    {
        basicReleaseRewriteTest("internal-snapshot-profile");
    }

    @Test
    public void releaseWithInternalSnapshotReportPlugins() throws Exception
    {
        basicReleaseRewriteTest("internal-snapshot-report-plugins");
    }

    @Test
    public void releaseWithInterpolatedVersions() throws Exception
    {
        basicReleaseRewriteTest("interpolated-versions");
    }

    @Test
    public void releaseWithDifferentModuleVersions() throws Exception
    {
        basicReleaseRewriteTest("modules-with-different-versions");
    }

    @Test
    public void releaseWithDeepMultimodule() throws Exception
    {
        basicReleaseRewriteTest("multimodule-with-deep-subprojects");
    }

    @Test
    public void releaseWithInheritedVersion() throws Exception
    {
        basicReleaseRewriteTest("pom-with-inherited-version");
    }

    @Test
    public void releaseWithOverriddenScm() throws Exception
    {
        basicReleaseRewriteTest("pom-with-overridden-scm");
    }

    @Test
    public void releaseWithParent() throws Exception
    {
        basicReleaseRewriteTest("pom-with-parent");
    }

    @Test
    public void releaseWithParentAndProperties() throws Exception
    {
        basicReleaseRewriteTest("pom-with-parent-and-properties");
    }

    @Test
    public void releaseWithFlatParent() throws Exception
    {
        List<MavenProject> projects = createReactorProjects("rewrite-for-release/pom-with-parent-flat","root-project");
        File projectRoot = projects.get(0).getBasedir();

        JGitFlow flow = JGitFlow.getOrInit(projectRoot);

        assertOnDevelop(flow);

        initialCommitAll(flow);
        FlowReleaseManager relman = getReleaseManager();

        ReleaseContext ctx = new ReleaseContext(projectRoot);
        ctx.setInteractive(false).setNoTag(true).setPush(false);

        relman.start(ctx,projects);

        assertOnRelease(flow, "1.0");

        comparePomFiles(projects);
    }

    @Test
    public void releaseWithPropertyDependencyCoord() throws Exception
    {
        basicReleaseRewriteTest("pom-with-property-dependency-coordinate");
    }

    @Test
    public void releaseWithReleasedParent() throws Exception
    {
        basicReleaseRewriteTest("pom-with-released-parent");
    }

    @Test
    public void startReleaseWithMasterOnly() throws Exception
    {
        ProjectHelper projectHelper = (ProjectHelper) lookup(ProjectHelper.class.getName());

        Git git = null;
        Git remoteGit = null;

        List<MavenProject> remoteProjects = createReactorProjects("remote-git-project",null);

        File remoteDir = remoteProjects.get(0).getBasedir();

        //make sure we're clean
        File remoteGitDir = new File(remoteDir,".git");
        if(remoteGitDir.exists())
        {
            FileUtils.cleanDirectory(remoteGitDir);
        }

        remoteGit = RepoUtil.createRepositoryWithMaster(remoteDir);
        projectHelper.commitAllChanges(remoteGit,"remote commit");

        File localProject = new File( testFileBase, "projects/local/local-git-project" );
        git = Git.cloneRepository().setDirectory(localProject).setURI("file://" + remoteGit.getRepository().getWorkTree().getPath()).call();
        
        List<MavenProject> projects = createReactorProjects("remote-git-project","local/local-git-project",null,false);
        File projectRoot = projects.get(0).getBasedir();

        JGitFlowInitCommand initCommand = new JGitFlowInitCommand();
        JGitFlow flow = initCommand.setDirectory(git.getRepository().getWorkTree()).call();

        projectHelper.ensureOrigin(projects,flow);

        flow.releaseStart("1.0").setFetch(true).call();

        assertEquals(flow.getReleaseBranchPrefix() + "1.0", git.getRepository().getBranch());


    }

}
