import com.atlassian.maven.plugins.jgitflow.it.FinishScriptHelper
import org.apache.commons.io.FileUtils

    helper = new FinishScriptHelper(basedir, localRepositoryPath, context)
    gits = helper.createAndCloneRepo("1.0","1.1-SNAPSHOT","1.0.1","hotfix/")

    localGit = gits.local;
    localGit.checkout().setName("hotfix/1.0.1")

    File junkFile = new File(localGit.getRepository().getWorkTree(), "junk.txt");
    FileUtils.writeStringToFile(junkFile, "I am junk");
    localGit.add().addFilepattern(junkFile.getName()).call();
    localGit.commit().setMessage("adding junk file").call();

    return true;
