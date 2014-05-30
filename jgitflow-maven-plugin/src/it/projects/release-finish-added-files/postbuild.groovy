import com.atlassian.jgitflow.core.JGitFlow
import com.atlassian.maven.plugins.jgitflow.it.FinishScriptHelper
import org.apache.commons.io.FileUtils

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue

try
{
    helper = new FinishScriptHelper(basedir, localRepositoryPath, context)
    flow = JGitFlow.getOrInit(basedir)
    flow.git().checkout().setName("master").call()

    helper.comparePomFiles("expected-master-pom.xml", "pom.xml")
    
    extFile = new File(flow.git().getRepository().getWorkTree(),"ext-result.txt");
    assertTrue("extension file missing!",extFile.exists());
    
    extResult = FileUtils.readFileToString(extFile);
    
    String[] versions = extResult.split(":");
    
    assertEquals("old version mismatch", "1.0", versions[0]);
    assertEquals("new version mismatch", "1.1", versions[1]);

    flow.git().checkout().setName("develop").call()

    File junkFile = new File(basedir,"junk.txt")
    assertTrue(junkFile.exists())
    
    helper.comparePomFiles("expected-develop-pom.xml", "pom.xml")
}
catch (Exception e)
{
    System.err.println(e.getMessage())
    return false;
}