package ut.com.atlassian.maven.plugins.jgitflow.manager;

import java.io.File;
import java.util.List;
import java.util.Properties;

import com.atlassian.jgitflow.core.InitContext;
import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.manager.FlowReleaseManager;

import org.apache.commons.io.FileUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FeatureManagerFinishFeatureTest extends AbstractFlowManagerTest
{
    public static final String FEATURE_NAME = "my-feature";
    public static final String UNDERSCORED_FEATURE_NAME = "my_feature";

    @Test
    public void useFeatureVersions() throws Exception
    {
        startFeature();

        String projectSubdir = "basic-pom";
        List<MavenProject> projects = createReactorProjects("rewrite-for-release", projectSubdir, false);
        File projectRoot = projects.get(0).getBasedir();

        FlowReleaseManager relman = getFeatureManager();

        ReleaseContext ctx = new ReleaseContext(projectRoot);
        ctx.setInteractive(false).setEnableFeatureVersions(true).setNoBuild(true);

        MavenSession session = new MavenSession(getContainer(), new Settings(), localRepository, null, null, null, projectRoot.getAbsolutePath(), new Properties(), new Properties(), null);

        relman.finish(ctx, projects, session);

        //reload the projects
        projects = createReactorProjectsNoClean("rewrite-for-release", projectSubdir);

        String pom = FileUtils.readFileToString(projects.get(0).getFile());
        assertTrue(pom.contains("1.0-SNAPSHOT"));
    }

    @Test
    public void customDevelopName() throws Exception
    {
        String developName = "voipac-current";
        InitContext initContext = new InitContext();
        initContext.setDevelop(developName);

        JGitFlow flow = startFeature(initContext);

        String projectSubdir = "basic-pom";
        List<MavenProject> projects = createReactorProjects("rewrite-for-release", projectSubdir, false);
        File projectRoot = projects.get(0).getBasedir();

        FlowReleaseManager relman = getFeatureManager();

        ReleaseContext ctx = new ReleaseContext(projectRoot);
        ctx.setInteractive(false).setEnableFeatureVersions(true).setNoBuild(true).setFlowInitContext(initContext);

        MavenSession session = new MavenSession(getContainer(), new Settings(), localRepository, null, null, null, projectRoot.getAbsolutePath(), new Properties(), new Properties(), null);

        relman.finish(ctx, projects, session);

        assertEquals(developName, flow.git().getRepository().getBranch());
        //reload the projects
        projects = createReactorProjectsNoClean("rewrite-for-release", projectSubdir);

        String pom = FileUtils.readFileToString(projects.get(0).getFile());
        assertTrue(pom.contains("1.0-SNAPSHOT"));
    }

    private JGitFlow startFeature() throws Exception
    {
        return startFeature(new InitContext());
    }

    private JGitFlow startFeature(InitContext initContext) throws Exception
    {
        String projectSubdir = "basic-pom";
        List<MavenProject> projects = createReactorProjects("rewrite-for-release", projectSubdir);
        File projectRoot = projects.get(0).getBasedir();

        JGitFlow flow = JGitFlow.forceInit(projectRoot, initContext);

        flow.git().checkout().setName(flow.getDevelopBranchName()).call();

        initialCommitAll(flow);

        FlowReleaseManager relman = getFeatureManager();

        ReleaseContext ctx = new ReleaseContext(projectRoot);
        ctx.setInteractive(false).setDefaultFeatureName(FEATURE_NAME).setEnableFeatureVersions(true).setFlowInitContext(initContext);

        MavenSession session = new MavenSession(getContainer(), new Settings(), localRepository, null, null, null, projectRoot.getAbsolutePath(), new Properties(), new Properties(), null);

        relman.start(ctx, projects, session);

        String pom = FileUtils.readFileToString(projects.get(0).getFile());
        assertTrue(pom.contains("1.0-" + UNDERSCORED_FEATURE_NAME + "-SNAPSHOT"));

        return flow;
    }
}
