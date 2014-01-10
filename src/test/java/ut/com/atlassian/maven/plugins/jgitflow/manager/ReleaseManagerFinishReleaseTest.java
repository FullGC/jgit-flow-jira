package ut.com.atlassian.maven.plugins.jgitflow.manager;

import java.io.File;
import java.util.List;
import java.util.Properties;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.util.GitHelper;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.manager.FlowReleaseManager;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.util.FileUtils;
import org.eclipse.jgit.api.Git;
import org.junit.Test;

import ut.com.atlassian.maven.plugins.jgitflow.testutils.RepoUtil;

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
        ctx.setNoBuild(true).setScmCommentPrefix(commentPrefix);

        JGitFlow flow = JGitFlow.getOrInit(projectRoot);
        flow.git().checkout().setName(flow.getDevelopBranchName()).call();

        assertOnDevelop(flow);

        initialCommitAll(flow);
        FlowReleaseManager relman = getReleaseManager();

        relman.start(ctx, projects, session);

        assertOnRelease(flow, ctx.getDefaultReleaseVersion());

        //reload the projects
        projects = createReactorProjectsNoClean("release-projects", projectName);

        relman.finish(ctx, projects, session);
        
        assertTrue(GitHelper.getLatestCommit(flow.git(),flow.git().getRepository().getBranch()).getFullMessage().startsWith(commentPrefix));
        
        
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

}
