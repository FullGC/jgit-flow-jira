import com.atlassian.jgitflow.core.JGitFlow
import com.atlassian.jgitflow.core.util.GitHelper
import com.atlassian.maven.plugins.jgitflow.it.FinishScriptHelper

import static org.junit.Assert.assertTrue
import static org.junit.Assert.assertFalse

    helper = new FinishScriptHelper(basedir, localRepositoryPath, context)
    flow = JGitFlow.getOrInit(basedir)
    flow.git().checkout().setName("master").call()

    helper.comparePomFiles("expected-master-pom.xml", "pom.xml")

    flow.git().checkout().setName("develop").call()

    File junkFile = new File(basedir, "junk.txt")
    assertTrue(junkFile.exists())

    //make sure hotfix delete was pushed
    branch = "hotfix/1.0.1";
    assertFalse("remote hotfix should not exist!", GitHelper.remoteBranchExists(flow.git(), branch, flow.getReporter()));

    helper.comparePomFiles("expected-develop-pom.xml", "pom.xml")

    return true;
