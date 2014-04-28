package ut.com.atlassian.jgitflow.core.extension;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.JGitFlowInitCommand;
import com.atlassian.jgitflow.core.exception.JGitFlowExtensionException;
import com.atlassian.jgitflow.core.extension.ExtensionFailStrategy;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.junit.Test;

import ut.com.atlassian.jgitflow.core.BaseGitFlowTest;
import ut.com.atlassian.jgitflow.core.testutils.*;

import static org.junit.Assert.*;

public class FeatureFinishExtensionTest extends BaseGitFlowTest
{

    public static final String MY_FEATURE = "myFeature";

    @Test
    public void finishFeatureExtension() throws Exception
    {
        Git git = null;
        Git remoteGit = null;
        remoteGit = RepoUtil.createRepositoryWithMasterAndDevelop(newDir());

        git = Git.cloneRepository().setDirectory(newDir()).setURI("file://" + remoteGit.getRepository().getWorkTree().getPath()).call();

        JGitFlowInitCommand initCommand = new JGitFlowInitCommand();
        JGitFlow flow = initCommand.setDirectory(git.getRepository().getWorkTree()).call();
        git.push().setRemote("origin").add("develop").call();

        FeatureFinishExtensionForTests extension = new FeatureFinishExtensionForTests();

        ExtensionProviderForTests provider = new ExtensionProviderForTests();
        provider.setFeatureFinishExtension(extension);

        flow.featureStart(MY_FEATURE).setPush(true).call();

        //just in case
        assertEquals(flow.getFeatureBranchPrefix() + MY_FEATURE, git.getRepository().getBranch());

        
        flow.featureFinish(MY_FEATURE).setFetchDevelop(true).setPush(true).setExtensionProvider(provider).call();

        //we should be on develop branch
        assertEquals(flow.getDevelopBranchName(), git.getRepository().getBranch());

        //feature branch should be gone
        Ref ref2check = git.getRepository().getRef(flow.getFeatureBranchPrefix() + MY_FEATURE);
        assertNull(ref2check);

        assertTrue("before was not called", extension.wasCalled(BaseExtensionForTests.BEFORE));
        assertTrue("beforeFetch was not called", extension.wasCalled(BaseExtensionForTests.BEFORE_FETCH));
        assertTrue("afterFetch was not called", extension.wasCalled(BaseExtensionForTests.AFTER_FETCH));
        assertTrue("beforeDevelopCheckout was not called", extension.wasCalled(BaseExtensionForTests.BEFORE_DEVELOP_CHECKOUT));
        assertTrue("afterDevelopCheckout was not called", extension.wasCalled(BaseExtensionForTests.AFTER_DEVELOP_CHECKOUT));
        assertTrue("beforeDevelopMerge was not called", extension.wasCalled(BaseExtensionForTests.BEFORE_DEVELOP_MERGE));
        assertTrue("afterDevelopMerge was not called", extension.wasCalled(BaseExtensionForTests.AFTER_DEVELOP_MERGE));
        assertTrue("afterPush was not called", extension.wasCalled(BaseExtensionForTests.AFTER_PUSH));
        assertTrue("after was not called", extension.wasCalled(BaseExtensionForTests.AFTER));

    }

    @Test
    public void finishFeatureExtensionWithThrownException() throws Exception
    {
        Git git = null;
        Git remoteGit = null;
        remoteGit = RepoUtil.createRepositoryWithMasterAndDevelop(newDir());

        git = Git.cloneRepository().setDirectory(newDir()).setURI("file://" + remoteGit.getRepository().getWorkTree().getPath()).call();

        JGitFlowInitCommand initCommand = new JGitFlowInitCommand();
        JGitFlow flow = initCommand.setDirectory(git.getRepository().getWorkTree()).call();
        git.push().setRemote("origin").add("develop").call();

        FeatureFinishExtensionForTests extension = new FeatureFinishExtensionForTests();
        extension.withException(BaseExtensionForTests.BEFORE_DEVELOP_CHECKOUT, ExtensionFailStrategy.ERROR);

        ExtensionProviderForTests provider = new ExtensionProviderForTests();
        provider.setFeatureFinishExtension(extension);

        flow.featureStart(MY_FEATURE).setPush(true).call();

        //just in case
        assertEquals(flow.getFeatureBranchPrefix() + MY_FEATURE, git.getRepository().getBranch());


        try
        {
            flow.featureFinish(MY_FEATURE).setFetchDevelop(true).setPush(true).setExtensionProvider(provider).call();

            fail("Exception should have been thrown!!");
        }
        catch (JGitFlowExtensionException e)
        {
            assertTrue("before was not called", extension.wasCalled(BaseExtensionForTests.BEFORE));
            assertTrue("beforeFetch was not called", extension.wasCalled(BaseExtensionForTests.BEFORE_FETCH));
            assertTrue("afterFetch was not called", extension.wasCalled(BaseExtensionForTests.AFTER_FETCH));
            assertFalse("afterDevelopCheckout was called", extension.wasCalled(BaseExtensionForTests.AFTER_DEVELOP_CHECKOUT));
        }
    }

    @Test
    public void finishFeatureWithWarnException() throws Exception
    {
        Git git = null;
        Git remoteGit = null;
        remoteGit = RepoUtil.createRepositoryWithMasterAndDevelop(newDir());

        git = Git.cloneRepository().setDirectory(newDir()).setURI("file://" + remoteGit.getRepository().getWorkTree().getPath()).call();

        JGitFlowInitCommand initCommand = new JGitFlowInitCommand();
        JGitFlow flow = initCommand.setDirectory(git.getRepository().getWorkTree()).call();
        git.push().setRemote("origin").add("develop").call();

        FeatureFinishExtensionForTests extension = new FeatureFinishExtensionForTests();
        extension.withException(BaseExtensionForTests.BEFORE_DEVELOP_CHECKOUT, ExtensionFailStrategy.WARN);

        ExtensionProviderForTests provider = new ExtensionProviderForTests();
        provider.setFeatureFinishExtension(extension);

        flow.featureStart(MY_FEATURE).setPush(true).call();

        //just in case
        assertEquals(flow.getFeatureBranchPrefix() + MY_FEATURE, git.getRepository().getBranch());


        flow.featureFinish(MY_FEATURE).setFetchDevelop(true).setPush(true).setExtensionProvider(provider).call();

        //we should be on develop branch
        assertEquals(flow.getDevelopBranchName(), git.getRepository().getBranch());

        //feature branch should be gone
        Ref ref2check = git.getRepository().getRef(flow.getFeatureBranchPrefix() + MY_FEATURE);
        assertNull(ref2check);

        assertTrue("before was not called", extension.wasCalled(BaseExtensionForTests.BEFORE));
        assertTrue("beforeFetch was not called", extension.wasCalled(BaseExtensionForTests.BEFORE_FETCH));
        assertTrue("afterFetch was not called", extension.wasCalled(BaseExtensionForTests.AFTER_FETCH));
        assertTrue("beforeDevelopCheckout was not called", extension.wasCalled(BaseExtensionForTests.BEFORE_DEVELOP_CHECKOUT));
        assertTrue("afterDevelopCheckout was not called", extension.wasCalled(BaseExtensionForTests.AFTER_DEVELOP_CHECKOUT));
        assertTrue("beforeDevelopMerge was not called", extension.wasCalled(BaseExtensionForTests.BEFORE_DEVELOP_MERGE));
        assertTrue("afterDevelopMerge was not called", extension.wasCalled(BaseExtensionForTests.AFTER_DEVELOP_MERGE));
        assertTrue("afterPush was not called", extension.wasCalled(BaseExtensionForTests.AFTER_PUSH));
        assertTrue("after was not called", extension.wasCalled(BaseExtensionForTests.AFTER));

    }
}
