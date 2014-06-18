package ut.com.atlassian.maven.plugins.jgitflow.manager;

import java.io.File;
import java.util.List;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.JGitFlowInitCommand;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.helper.JGitFlowSetupHelper;
import com.atlassian.maven.plugins.jgitflow.helper.ProjectHelper;

import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.eclipse.jgit.api.Git;
import org.junit.Test;

import ut.com.atlassian.maven.plugins.jgitflow.testutils.RepoUtil;

import static org.junit.Assert.assertEquals;

public class FeatureManagerDeployTest extends AbstractFlowManagerTest
{
    @Test
    public void deployWithoutMasterFetch() throws Exception
    {
        String featureBranch = "feature/my-feature";
        
        ProjectHelper projectHelper = (ProjectHelper) lookup(ProjectHelper.class.getName());
        JGitFlowSetupHelper setupHelper = (JGitFlowSetupHelper) lookup(JGitFlowSetupHelper.class.getName());

        Git git = null;
        Git remoteGit = null;

        List<MavenProject> remoteProjects = createReactorProjects("remote-git-project", null);

        File remoteDir = remoteProjects.get(0).getBasedir();

        //make sure we're clean
        FileUtils.cleanDirectory(remoteDir);

        remoteGit = RepoUtil.createRepositoryWithMasterAndDevelop(remoteDir);
        remoteGit.checkout().setName("develop").call();
        copyTestProject("remote-git-project",null);
        remoteGit.add().addFilepattern(".").call();
        remoteGit.commit().setMessage("pom commit").call();
        
        remoteGit.branchDelete().setBranchNames("master").setForce(true).call();
        
        //create the remote featur branch
        remoteGit.branchCreate().setName(featureBranch).call();
        
        File localProject = new File(testFileBase, "projects/local/local-git-project");
        git = Git.cloneRepository().setDirectory(localProject).setURI("file://" + remoteGit.getRepository().getWorkTree().getPath()).setBranch(featureBranch).setCloneAllBranches(false).call();

        //make sure we're on the feature
        assertEquals(featureBranch,git.getRepository().getBranch());
        
        List<MavenProject> projects = createReactorProjects("remote-git-project", "local/local-git-project", null, false);
        File projectRoot = projects.get(0).getBasedir();

        JGitFlowInitCommand initCommand = new JGitFlowInitCommand();
        JGitFlow flow = initCommand.setDirectory(git.getRepository().getWorkTree()).call();

        //make sure we're STILL on the feature
        assertEquals(featureBranch,git.getRepository().getBranch());
        
        ReleaseContext ctx = new ReleaseContext(projectRoot);

    }
}
