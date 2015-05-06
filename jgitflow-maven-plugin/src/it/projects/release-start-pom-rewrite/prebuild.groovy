import com.atlassian.maven.plugins.jgitflow.it.FinishScriptHelper
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.api.Git

try
{
    helper = new FinishScriptHelper(basedir, localRepositoryPath, context)
    FinishScriptHelper.Gits gits = helper.createAndCloneRepo("1.0","1.1-SNAPSHOT","my-feature","feature/")

    Git localGit = gits.local;
    localGit.checkout().setName("develop").call();
    
    return true
}
catch (Exception e)
{
    System.err.println(e.getMessage())
    return false;
}