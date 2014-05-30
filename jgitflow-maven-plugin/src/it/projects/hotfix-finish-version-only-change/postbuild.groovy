import com.atlassian.jgitflow.core.JGitFlow
import com.atlassian.maven.plugins.jgitflow.it.FinishScriptHelper

try
{
    helper = new FinishScriptHelper(basedir, localRepositoryPath, context)
    flow = JGitFlow.getOrInit(basedir)
    flow.git().checkout().setName("master").call()

    helper.comparePomFiles("expected-master-pom.xml", "pom.xml")

    flow.git().checkout().setName("develop").call()

    helper.comparePomFiles("expected-develop-pom.xml", "pom.xml")
}
catch (Exception e)
{
    System.err.println(e.getMessage())
    return false;
}