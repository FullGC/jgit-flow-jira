import com.atlassian.maven.plugins.jgitflow.it.ReleaseFinishScriptHelper
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.api.Git

try
{
    helper = new ReleaseFinishScriptHelper(basedir, localRepositoryPath, context)
    ReleaseFinishScriptHelper.Gits gits = helper.createAndCloneReleaseRepo("1.0","1.1-SNAPSHOT","1.1")

    Git localGit = gits.local;
    Git remoteGit = gits.remote;
    
    localGit.checkout().setName("release/1.1")

    File junkFile = new File(localGit.getRepository().getWorkTree(), "junk.txt");
    FileUtils.writeStringToFile(junkFile, "I am junk");
    localGit.add().addFilepattern(junkFile.getName()).call();
    localGit.commit().setMessage("adding junk file").call();
    
    helper.clearOrigin(localGit)
    
    return true
}
catch (Exception e)
{
    System.err.println(e.getMessage())
    return false;
}