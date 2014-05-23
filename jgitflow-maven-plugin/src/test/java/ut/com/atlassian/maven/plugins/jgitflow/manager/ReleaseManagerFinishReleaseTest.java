package ut.com.atlassian.maven.plugins.jgitflow.manager;

import java.io.File;
import java.util.List;
import java.util.Properties;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.JGitFlowInfo;
import com.atlassian.jgitflow.core.exception.AlreadyInitializedException;
import com.atlassian.jgitflow.core.exception.JGitFlowGitAPIException;
import com.atlassian.jgitflow.core.exception.JGitFlowIOException;
import com.atlassian.jgitflow.core.exception.SameBranchException;
import com.atlassian.jgitflow.core.util.GitHelper;
import com.atlassian.maven.jgitflow.api.exception.MavenJGitFlowExtensionException;
import com.atlassian.maven.jgitflow.api.impl.NoopMavenReleaseFinishExtension;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.manager.FlowReleaseManager;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.util.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.junit.Test;

import ut.com.atlassian.maven.plugins.jgitflow.testutils.RepoUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @since version
 */
public class ReleaseManagerFinishReleaseTest extends AbstractFlowManagerTest
{
    @Test
    public void releaseBasicPomWithoutOrigin() throws Exception
    {
        String commentPrefix = "woot!";
        String commentSuffix = "+review CR-XYZ @reviewer1 @reviewer2";

        String projectName = "basic-pom";
        Git git = null;
        Git remoteGit = null;

        String projectSubdir = "basic-pom";
        List<MavenProject> remoteProjects = createReactorProjects("remote-git-project", null);

        File remoteDir = remoteProjects.get(0).getBasedir();

        //make sure we're clean
        File remoteGitDir = new File(remoteDir, ".git");
        if (remoteGitDir.exists())
        {
            FileUtils.cleanDirectory(remoteGitDir);
        }

        remoteGit = RepoUtil.createRepositoryWithMasterAndDevelop(remoteDir);

        List<MavenProject> projects = createReactorProjects("release-projects", projectName);
        File projectRoot = projects.get(0).getBasedir();

        MavenSession session = new MavenSession(getContainer(), new Settings(), localRepository, null, null, null, projectRoot.getAbsolutePath(), new Properties(), new Properties(), null);

        ReleaseContext ctx = new ReleaseContext(projectRoot);
        ctx.setInteractive(false);
        ctx.setNoBuild(true).setScmCommentPrefix(commentPrefix).setScmCommentSuffix(commentSuffix);

        JGitFlow flow = JGitFlow.getOrInit(projectRoot);
        setupProjectsForMasterAndDevelop(projectRoot,projectName);
        
        FlowReleaseManager relman = getReleaseManager();

        relman.start(ctx, projects, session);

        assertOnRelease(flow, ctx.getDefaultReleaseVersion());

        //reload the projects
        projects = createReactorProjectsNoClean("release-projects", projectName);

        relman.finish(ctx, projects, session);

        String fullMessage = GitHelper.getLatestCommit(flow.git(), flow.git().getRepository().getBranch()).getFullMessage();

        assertTrue(fullMessage.startsWith(commentPrefix));
        assertTrue(fullMessage.endsWith(commentSuffix));
    }

    @Test
    public void releaseFinishWithComplexVersionAndSuffix() throws Exception
    {
        String commentPrefix = "woot!";

        String projectName = "complex-version-and-suffix";

        List<MavenProject> projects = createReactorProjects("rewrite-for-release", projectName);
        File projectRoot = projects.get(0).getBasedir();

        ReleaseContext ctx = new ReleaseContext(projectRoot);
        ctx.setInteractive(false).setNoTag(true).setAllowSnapshots(true).setReleaseBranchVersionSuffix("RC");

        basicReleaseRewriteTest(projectName, ctx);
    }

    @Test
    public void releaseFinishWithExternalExtension() throws Exception
    {
        String projectName = "master-and-develop";
        Git git = null;

        List<MavenProject> projects = createReactorProjects("release-projects", projectName);
        File projectRoot = projects.get(0).getBasedir();

        MavenSession session = new MavenSession(getContainer(), new Settings(), localRepository, null, null, null, projectRoot.getAbsolutePath(), new Properties(), new Properties(), null);

        FinishExtension extension = new FinishExtension();

        ReleaseContext ctx = new ReleaseContext(projectRoot);
        ctx.setInteractive(false)
           .setNoTag(true)
           .setAllowSnapshots(true)
           .setNoBuild(true)
           .setReleaseFinishExtension(extension);

        setupProjectsForMasterAndDevelop(projectRoot,projectName);

        FlowReleaseManager relman = getReleaseManager();
        relman.start(ctx, projects, session);
        relman.finish(ctx, projects, session);

        assertEquals("old version incorrect", "1.0", extension.getOldVersion());
        assertEquals("new version incorrect", "1.1", extension.getNewVersion());

    }

    private void setupProjectsForMasterAndDevelop(File projectRoot, String projectName) throws Exception
    {
        JGitFlow flow = JGitFlow.getOrInit(projectRoot);
        flow.git().checkout().setName(flow.getMasterBranchName()).call();
        copyTestProject("release-projects", projectName);

        assertOnMaster(flow);
        initialCommitAll(flow);

        Ref masterRef = flow.git().getRepository().getRef("HEAD");
        flow.git().checkout().setName(flow.getDevelopBranchName()).call();
        assertOnDevelop(flow);

        flow.git().merge().include("master", masterRef.getObjectId()).call();

        updatePomVersion(new File(projectRoot, "pom.xml"), "1.0", "1.1-SNAPSHOT");
        commitAll(flow, "bumping develop");
    }
    
    private class FinishExtension extends NoopMavenReleaseFinishExtension
    {
        private String oldVersion;
        private String newVersion;

        @Override
        public void onMasterBranchVersionChange(String newVersion, String oldVersion, JGitFlowInfo flow) throws MavenJGitFlowExtensionException
        {
            this.oldVersion = oldVersion;
            this.newVersion = newVersion;
        }

        public String getOldVersion()
        {
            return oldVersion;
        }

        public String getNewVersion()
        {
            return newVersion;
        }
    }
}
