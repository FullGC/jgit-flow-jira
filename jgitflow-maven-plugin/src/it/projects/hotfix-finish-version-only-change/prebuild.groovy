import com.atlassian.maven.plugins.jgitflow.it.FinishScriptHelper
import org.eclipse.jgit.api.Git

try
{
    helper = new FinishScriptHelper(basedir, localRepositoryPath, context)
    FinishScriptHelper.Gits gits = helper.createAndCloneRepo("1.0","1.1-SNAPSHOT","1.0.1","hotfix/")

    Git localGit = gits.local;
    localGit.checkout().setName("hotfix/1.0.1")
    
    return true
}
catch (Exception e)
{
    System.err.println(e.getMessage())
    return false;
}