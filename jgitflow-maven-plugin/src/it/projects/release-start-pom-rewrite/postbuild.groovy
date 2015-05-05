import com.atlassian.jgitflow.core.JGitFlow
import com.atlassian.maven.plugins.jgitflow.it.FinishScriptHelper
import static org.junit.Assert.assertTrue

try
{
    helper = new FinishScriptHelper(basedir, localRepositoryPath, context)
    flow = JGitFlow.getOrInit(basedir)
    flow.git().checkout().setName("release/1.1").call()

    helper.comparePomFiles("subproject1/expected-release-pom.xml", "subproject1/pom.xml")
}
catch (Exception e)
{
    System.err.println(e.getMessage())
    return false;
}