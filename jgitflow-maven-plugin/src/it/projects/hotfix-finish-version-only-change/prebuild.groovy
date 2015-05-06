import com.atlassian.maven.plugins.jgitflow.it.FinishScriptHelper
import org.eclipse.jgit.api.CreateBranchCommand
import org.eclipse.jgit.api.Git

try
{
    helper = new FinishScriptHelper(basedir, localRepositoryPath, context)
    FinishScriptHelper.Gits gits = helper.createAndCloneRepo("1.0","1.1-SNAPSHOT","1.0.1","hotfix/")

    Git localGit = gits.local;
    localGit.checkout().setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK).setCreateBranch(true).setStartPoint("origin/hotfix/1.0.1").setName("hotfix/1.0.1").call()
    
    return true
}
catch (Exception e)
{
    System.err.println(e.getMessage())
    return false;
}