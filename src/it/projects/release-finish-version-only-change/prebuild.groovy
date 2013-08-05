import com.atlassian.maven.plugins.jgitflow.it.ReleaseFinishScriptHelper
import org.eclipse.jgit.api.Git

try
{
    helper = new ReleaseFinishScriptHelper(basedir, localRepositoryPath, context)
    ReleaseFinishScriptHelper.Gits gits = helper.createAndCloneReleaseRepo("1.0","1.1-SNAPSHOT","1.1")

    Git localGit = gits.local;
    localGit.checkout().setName("release/1.1")
    
    return true
}
catch (Exception e)
{
    System.err.println(e.getMessage())
    return false;
}