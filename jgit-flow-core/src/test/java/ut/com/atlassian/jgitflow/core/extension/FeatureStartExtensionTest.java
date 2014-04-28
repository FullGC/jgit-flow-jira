package ut.com.atlassian.jgitflow.core.extension;

import java.io.File;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.JGitFlowInitCommand;
import com.atlassian.jgitflow.core.exception.JGitFlowExtensionException;
import com.atlassian.jgitflow.core.extension.ExtensionFailStrategy;
import com.atlassian.jgitflow.core.extension.ExtensionProvider;
import com.atlassian.jgitflow.core.extension.FeatureStartExtension;
import com.atlassian.jgitflow.core.extension.impl.EmptyExtensionProvider;
import com.atlassian.jgitflow.core.util.GitHelper;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.junit.Test;

import ut.com.atlassian.jgitflow.core.BaseGitFlowTest;
import ut.com.atlassian.jgitflow.core.testutils.BaseExtensionForTests;
import ut.com.atlassian.jgitflow.core.testutils.ExtensionProviderForTests;
import ut.com.atlassian.jgitflow.core.testutils.FeatureStartExtensionForTests;
import ut.com.atlassian.jgitflow.core.testutils.RepoUtil;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FeatureStartExtensionTest extends BaseGitFlowTest
{
    @Test
    public void startFeatureExtension() throws Exception
    {
        Git git = null;
        Git remoteGit = null;
        remoteGit = RepoUtil.createRepositoryWithMasterAndDevelop(newDir());

        git = Git.cloneRepository().setDirectory(newDir()).setURI("file://" + remoteGit.getRepository().getWorkTree().getPath()).call();

        JGitFlowInitCommand initCommand = new JGitFlowInitCommand();
        JGitFlow flow = initCommand.setDirectory(git.getRepository().getWorkTree()).call();
        git.push().setRemote("origin").add("develop").call();

        FeatureStartExtensionForTests extension = new FeatureStartExtensionForTests();

        ExtensionProviderForTests provider = new ExtensionProviderForTests();
        provider.setFeatureStartExtension(extension);

        flow.featureStart("myFeature").setFetchDevelop(true).setPush(true).setExtensionProvider(provider).call();

        assertTrue("before was not called", extension.wasCalled(BaseExtensionForTests.BEFORE));
        assertTrue("beforeFetch was not called", extension.wasCalled(BaseExtensionForTests.BEFORE_FETCH));
        assertTrue("afterFetch was not called", extension.wasCalled(BaseExtensionForTests.AFTER_FETCH));
        assertTrue("beforeCreateBranch was not called", extension.wasCalled(BaseExtensionForTests.BEFORE_CREATE_BRANCH));
        assertTrue("afterCreateBranch was not called", extension.wasCalled(BaseExtensionForTests.AFTER_CREATE_BRANCH));
        assertTrue("afterPush was not called", extension.wasCalled(BaseExtensionForTests.AFTER_PUSH));
        assertTrue("after was not called", extension.wasCalled(BaseExtensionForTests.AFTER));
    }

    @Test
    public void startFeatureExtensionWithThrownException() throws Exception
    {
        Git git = null;
        Git remoteGit = null;
        remoteGit = RepoUtil.createRepositoryWithMasterAndDevelop(newDir());

        git = Git.cloneRepository().setDirectory(newDir()).setURI("file://" + remoteGit.getRepository().getWorkTree().getPath()).call();

        JGitFlowInitCommand initCommand = new JGitFlowInitCommand();
        JGitFlow flow = initCommand.setDirectory(git.getRepository().getWorkTree()).call();
        git.push().setRemote("origin").add("develop").call();

        FeatureStartExtensionForTests extension = new FeatureStartExtensionForTests();
        extension.withException(BaseExtensionForTests.AFTER_CREATE_BRANCH, ExtensionFailStrategy.ERROR);

        ExtensionProviderForTests provider = new ExtensionProviderForTests();
        provider.setFeatureStartExtension(extension);

        try
        {
            flow.featureStart("myFeature").setFetchDevelop(true).setPush(true).setExtensionProvider(provider).call();

            fail("Exception should have been thrown!!");
        }
        catch (JGitFlowExtensionException e)
        {
            assertTrue("before was not called", extension.wasCalled(BaseExtensionForTests.BEFORE));
            assertTrue("beforeFetch was not called", extension.wasCalled(BaseExtensionForTests.BEFORE_FETCH));
            assertTrue("afterFetch was not called", extension.wasCalled(BaseExtensionForTests.AFTER_FETCH));
            assertTrue("beforeCreateBranch was not called", extension.wasCalled(BaseExtensionForTests.BEFORE_CREATE_BRANCH));
        }

    }

    @Test
    public void startFeatureExtensionWithWarnException() throws Exception
    {
        Git git = null;
        Git remoteGit = null;
        remoteGit = RepoUtil.createRepositoryWithMasterAndDevelop(newDir());

        git = Git.cloneRepository().setDirectory(newDir()).setURI("file://" + remoteGit.getRepository().getWorkTree().getPath()).call();

        JGitFlowInitCommand initCommand = new JGitFlowInitCommand();
        JGitFlow flow = initCommand.setDirectory(git.getRepository().getWorkTree()).call();
        git.push().setRemote("origin").add("develop").call();

        FeatureStartExtensionForTests extension = new FeatureStartExtensionForTests();
        extension.withException(BaseExtensionForTests.AFTER_CREATE_BRANCH, ExtensionFailStrategy.WARN);

        ExtensionProviderForTests provider = new ExtensionProviderForTests();
        provider.setFeatureStartExtension(extension);

        flow.featureStart("myFeature").setFetchDevelop(true).setPush(true).setExtensionProvider(provider).call();

        assertTrue("before was not called", extension.wasCalled(BaseExtensionForTests.BEFORE));
        assertTrue("beforeFetch was not called", extension.wasCalled(BaseExtensionForTests.BEFORE_FETCH));
        assertTrue("afterFetch was not called", extension.wasCalled(BaseExtensionForTests.AFTER_FETCH));
        assertTrue("beforeCreateBranch was not called", extension.wasCalled(BaseExtensionForTests.BEFORE_CREATE_BRANCH));
        assertTrue("afterCreateBranch was not called", extension.wasCalled(BaseExtensionForTests.AFTER_CREATE_BRANCH));
        assertTrue("afterPush was not called", extension.wasCalled(BaseExtensionForTests.AFTER_PUSH));
        assertTrue("after was not called", extension.wasCalled(BaseExtensionForTests.AFTER));

    }
    
}
