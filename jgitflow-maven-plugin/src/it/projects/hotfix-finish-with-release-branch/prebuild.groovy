import com.atlassian.maven.plugins.jgitflow.it.FinishScriptHelper
import org.apache.commons.io.FileUtils
import org.eclipse.jgit.api.CreateBranchCommand

helper = new FinishScriptHelper(basedir, localRepositoryPath, context)
    gits = helper.createAndCloneRepo("1.0","1.1-SNAPSHOT","1.0.1","hotfix/")

    helper.cloneDevelopRepo("1.1-SNAPSHOT","1.1","release/")

    localGit = gits.local;
    localGit.checkout().setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK).setCreateBranch(true).setStartPoint("origin/hotfix/1.0.1").setName("hotfix/1.0.1").call();

    File junkFile = new File(localGit.getRepository().getWorkTree(), "junk.txt");
    FileUtils.writeStringToFile(junkFile, "I am junk");
    localGit.add().addFilepattern(junkFile.getName()).call();
    localGit.commit().setMessage("adding junk file").call();

    return true;
