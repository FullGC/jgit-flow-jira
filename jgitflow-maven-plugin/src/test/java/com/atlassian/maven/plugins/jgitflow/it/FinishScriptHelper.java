package com.atlassian.maven.plugins.jgitflow.it;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Map;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.shared.release.util.ReleaseUtil;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.StoredConfig;

import static org.junit.Assert.assertEquals;

/**
 * @since version
 */
public class FinishScriptHelper
{

    /**
     * The absolute path to the base directory of the test project.
     */
    protected File baseDirectory;

    /**
     * The absolute path to the local repository used for the Maven invocation on the test project.
     */
    protected File localRepositoryPath;

    /**
     * The storage of key-value pairs used to pass data from the pre-build hook script to the post-build hook script.
     */
    protected Map context;

    public FinishScriptHelper(File baseDirectory, File localRepositoryPath, Map context)
    {
        this.baseDirectory = baseDirectory;
        this.localRepositoryPath = localRepositoryPath;
        this.context = context;
    }

    public Gits createAndCloneRepo(String masterVersion, String developVersion, String branchVersion, String branchPrefix) throws GitAPIException, IOException
    {
        File remotesBaseDir = new File(baseDirectory.getParentFile().getParentFile(), "remotes");
        remotesBaseDir.mkdirs();

        File remoteProjectDir = new File(remotesBaseDir, baseDirectory.getName());
        remoteProjectDir.mkdirs();

        FileUtils.copyDirectory(baseDirectory, remoteProjectDir);

        //update any @project.version@ vars with our plugin version
        URL url = Resources.getResource("VERSION");
        String flowVersion = Resources.toString(url, Charsets.UTF_8);

        Collection<File> masterXmls = FileUtils.listFiles(remoteProjectDir, new WildcardFileFilter("*.xml"), FileFilterUtils.directoryFileFilter());

        for (File masterXml : masterXmls)
        {
            String xmlString = FileUtils.readFileToString(masterXml);
            String updatedMasterXml = StringUtils.replace(xmlString, "<version>@project.version@</version>", "<version>" + flowVersion + "</version>");
            FileUtils.writeStringToFile(masterXml, updatedMasterXml);
        }

        Git remoteGit = Git.init().setDirectory(remoteProjectDir).call();
        remoteGit.add().addFilepattern(".").call();
        remoteGit.commit().setMessage("initial commit").call();
        remoteGit.branchCreate().setName("develop").call();
        remoteGit.commit().setMessage("added develop branch").call();

        //update Develop Version
        remoteGit.checkout().setName("develop").call();
        Collection<File> developPoms = FileUtils.listFiles(remoteProjectDir, FileFilterUtils.nameFileFilter("pom.xml"), FileFilterUtils.directoryFileFilter());

        for (File developPom : developPoms)
        {
            System.out.println("updating pom for develop: " + developPom.getAbsolutePath());
            String pomString = FileUtils.readFileToString(developPom);
            String updatedDevelopPom = StringUtils.replace(pomString, "<version>" + masterVersion + "</version>", "<version>" + developVersion + "</version>");
            FileUtils.writeStringToFile(developPom, updatedDevelopPom);
        }

        remoteGit.add().addFilepattern(".").call();
        remoteGit.commit().setMessage("updating develop versions").call();

        String scrubbedBranchVersion = branchVersion;

        if ("feature/".equals(branchPrefix))
        {
            scrubbedBranchVersion = StringUtils.replace(scrubbedBranchVersion, "-", "_");
            scrubbedBranchVersion = StringUtils.substringBeforeLast(developVersion, "-SNAPSHOT") + "-" + scrubbedBranchVersion + "-SNAPSHOT";
        }

        remoteGit.branchCreate().setName(branchPrefix + branchVersion).call();
        remoteGit.commit().setMessage("added branch " + branchPrefix + branchVersion).call();
        remoteGit.checkout().setName(branchPrefix + branchVersion).call();

        //update branch Version
        Collection<File> branchPoms = FileUtils.listFiles(remoteProjectDir, FileFilterUtils.nameFileFilter("pom.xml"), FileFilterUtils.directoryFileFilter());

        //if the prefix is feature, we need special handling of the version/branch


        for (File branchPom : branchPoms)
        {
            System.out.println("updating pom for branch: " + branchPom.getAbsolutePath());
            String pomString = FileUtils.readFileToString(branchPom);
            String updatedReleasePom = StringUtils.replace(pomString, "<version>" + developVersion + "</version>", "<version>" + scrubbedBranchVersion + "</version>");
            FileUtils.writeStringToFile(branchPom, updatedReleasePom);
        }

        remoteGit.add().addFilepattern(".").call();
        remoteGit.commit().setMessage("updating branch versions").call();

        remoteGit.checkout().setName("master").call();
        
        FileUtils.deleteQuietly(baseDirectory);
        baseDirectory.mkdirs();

        Git localGit = Git.cloneRepository().setCloneAllBranches(true).setDirectory(baseDirectory).setURI("file://" + remoteGit.getRepository().getWorkTree().getPath()).call();

        Gits gits = new Gits(remoteGit, localGit);

        localGit.checkout().setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK).setCreateBranch(true).setStartPoint("origin/develop").setName("develop").call();

        return gits;
    }

    public Gits cloneDevelopRepo(String developVersion, String branchVersion, String branchPrefix) throws GitAPIException, IOException
    {
        File remotesBaseDir = new File(baseDirectory.getParentFile().getParentFile(), "remotes");

        File remoteProjectDir = new File(remotesBaseDir, baseDirectory.getName());

        //update any @project.version@ vars with our plugin version
        URL url = Resources.getResource("VERSION");
        String flowVersion = Resources.toString(url, Charsets.UTF_8);

        Git remoteGit = Git.open(remoteProjectDir);
        
        remoteGit.checkout().setName("develop").call();

        String scrubbedBranchVersion = branchVersion;

        if ("feature/".equals(branchPrefix))
        {
            scrubbedBranchVersion = StringUtils.replace(scrubbedBranchVersion, "-", "_");
            scrubbedBranchVersion = StringUtils.substringBeforeLast(developVersion, "-SNAPSHOT") + "-" + scrubbedBranchVersion + "-SNAPSHOT";
        }

        remoteGit.branchCreate().setName(branchPrefix + scrubbedBranchVersion).call();
        remoteGit.commit().setMessage("added branch " + branchPrefix + scrubbedBranchVersion).call();
        remoteGit.checkout().setName(branchPrefix + scrubbedBranchVersion).call();

        //update branch Version
        Collection<File> branchPoms = FileUtils.listFiles(remoteProjectDir, FileFilterUtils.nameFileFilter("pom.xml"), FileFilterUtils.directoryFileFilter());


        for (File branchPom : branchPoms)
        {
            System.out.println("updating pom for branch: " + branchPom.getAbsolutePath());
            String pomString = FileUtils.readFileToString(branchPom);
            String updatedReleasePom = StringUtils.replace(pomString, "<version>" + developVersion + "</version>", "<version>" + scrubbedBranchVersion + "</version>");
            FileUtils.writeStringToFile(branchPom, updatedReleasePom);
        }

        remoteGit.add().addFilepattern(".").call();
        remoteGit.commit().setMessage("updating branch versions").call();

        FileUtils.deleteQuietly(baseDirectory);
        baseDirectory.mkdirs();

        Git localGit = Git.cloneRepository().setCloneAllBranches(true).setDirectory(baseDirectory).setURI("file://" + remoteGit.getRepository().getWorkTree().getPath()).call();

        Gits gits = new Gits(remoteGit, localGit);

        localGit.checkout().setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK).setCreateBranch(true).setStartPoint("origin/develop").setName("develop").call();

        return gits;
    }

    public Git remoteGit() throws IOException
    {
        File remotesBaseDir = new File(baseDirectory.getParentFile().getParentFile(), "remotes");
        File remoteProjectDir = new File(remotesBaseDir, baseDirectory.getName());

        return Git.open(remoteProjectDir);

    }

    public void comparePomFiles(String expectedPath, String actualPath) throws IOException
    {
        String expectedPom = ReleaseUtil.readXmlFile(new File(baseDirectory, expectedPath));
        String actualPom = ReleaseUtil.readXmlFile(new File(baseDirectory, actualPath));

        System.out.println("EXPECTED");
        System.out.println("--------------------------------");
        System.out.println(expectedPom);
        System.out.println("--------------------------------");
        System.out.println("ACTUAL");
        System.out.println(actualPom);
        System.out.println("--------------------------------");
        assertEquals("Pom files don't match!!!: \nexpected:\n" + expectedPom + "actual:\n" + actualPom, expectedPom, actualPom);
    }

    public void clearOrigin(Git git) throws IOException
    {
        StoredConfig config = git.getRepository().getConfig();
        config.unsetSection("remote", "origin");
        config.save();
    }

    public class Gits
    {
        public final Git remote;
        public final Git local;

        public Gits(Git remote, Git local)
        {
            this.remote = remote;
            this.local = local;
        }
    }
}
