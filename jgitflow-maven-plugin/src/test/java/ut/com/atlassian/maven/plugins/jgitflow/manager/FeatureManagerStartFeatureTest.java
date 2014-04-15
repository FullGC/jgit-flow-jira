package ut.com.atlassian.maven.plugins.jgitflow.manager;

import java.io.File;
import java.util.List;
import java.util.Properties;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.exception.LocalBranchExistsException;
import com.atlassian.jgitflow.core.util.GitHelper;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.JGitFlowReleaseException;
import com.atlassian.maven.plugins.jgitflow.manager.FlowReleaseManager;

import org.apache.commons.io.FileUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @since version
 */
public class FeatureManagerStartFeatureTest extends AbstractFlowManagerTest
{
    public static final String FEATURE_NAME = "my-feature";
    public static final String UNDERSCORED_FEATURE_NAME = "my_feature";

    @Test(expected = JGitFlowReleaseException.class)
    public void existingSameFeatureThrowsException() throws Exception
    {
        String projectSubdir = "basic-pom";
        List<MavenProject> projects = createReactorProjects("rewrite-for-release", projectSubdir);
        File projectRoot = projects.get(0).getBasedir();

        JGitFlow flow = JGitFlow.getOrInit(projectRoot);
        flow.git().checkout().setName(flow.getDevelopBranchName()).call();

        assertOnDevelop(flow);

        initialCommitAll(flow);

        flow.git().checkout().setCreateBranch(true).setName(flow.getFeatureBranchPrefix() + FEATURE_NAME).call();

        //go back to develop
        flow.git().checkout().setName(flow.getDevelopBranchName()).call();

        assertOnDevelop(flow);

        assertTrue(GitHelper.localBranchExists(flow.git(), flow.getFeatureBranchPrefix() + FEATURE_NAME));

        FlowReleaseManager relman = getFeatureManager();

        ReleaseContext ctx = new ReleaseContext(projectRoot);
        ctx.setInteractive(false).setDefaultFeatureName(FEATURE_NAME);

        try
        {
            MavenSession session = new MavenSession(getContainer(), new Settings(), localRepository, null, null, null, projectRoot.getAbsolutePath(), new Properties(), new Properties(), null);

            relman.start(ctx, projects, session);
        }
        catch (JGitFlowReleaseException e)
        {
            assertEquals(LocalBranchExistsException.class, e.getCause().getClass());
            throw e;
        }

    }

    @Test
    public void useFeatureVersions() throws Exception
    {

        String projectSubdir = "basic-pom";
        List<MavenProject> projects = createReactorProjects("rewrite-for-release", projectSubdir);
        File projectRoot = projects.get(0).getBasedir();

        JGitFlow flow = JGitFlow.getOrInit(projectRoot);

        flow.git().checkout().setName(flow.getDevelopBranchName()).call();

        assertOnDevelop(flow);

        initialCommitAll(flow);

        FlowReleaseManager relman = getFeatureManager();

        ReleaseContext ctx = new ReleaseContext(projectRoot);
        ctx.setInteractive(false).setDefaultFeatureName(FEATURE_NAME).setEnableFeatureVersions(true);

        MavenSession session = new MavenSession(getContainer(), new Settings(), localRepository, null, null, null, projectRoot.getAbsolutePath(), new Properties(), new Properties(), null);

        relman.start(ctx, projects, session);

        //reload the projects
        projects = createReactorProjectsNoClean("rewrite-for-release", projectSubdir);

        String pom = FileUtils.readFileToString(projects.get(0).getFile());
        assertTrue(pom.contains("1.0-" + UNDERSCORED_FEATURE_NAME + "-SNAPSHOT"));
    }

    /*
    @Test(expected = JGitFlowReleaseException.class)
    public void existingFeaturesPrompt() throws Exception
    {
        String projectSubdir = "basic-pom";
        List<MavenProject> projects = createReactorProjects("rewrite-for-release", projectSubdir);
        File projectRoot = projects.get(0).getBasedir();

        JGitFlow flow = JGitFlow.getOrInit(projectRoot);

        assertOnDevelop(flow);

        initialCommitAll(flow);

        flow.git().checkout().setCreateBranch(true).setName(flow.getFeatureBranchPrefix() + FEATURE_NAME + "1").call();
        flow.git().checkout().setCreateBranch(true).setName(flow.getFeatureBranchPrefix() + FEATURE_NAME + "2").call();

        //go back to develop
        flow.git().checkout().setName(flow.getDevelopBranchName()).call();

        assertOnDevelop(flow);

        FlowReleaseManager relman = getFeatureManager();

        ReleaseContext ctx = new ReleaseContext(projectRoot);
        ctx.setInteractive(true);

        TestInputHandler userInput = (TestInputHandler) lookup(InputHandler.class.getName(),"test");
        TestOutputHandler consoleOutput = (TestOutputHandler) lookup(OutputHandler.class.getName(),"test");

        userInput.setResponse("2");
        relman.start(ctx, projects);

        System.out.println("console: " + consoleOutput.getValue());

        

        comparePomFiles(projects);
    }
    */


}

