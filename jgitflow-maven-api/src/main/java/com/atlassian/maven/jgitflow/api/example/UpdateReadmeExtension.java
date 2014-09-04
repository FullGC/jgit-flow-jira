package com.atlassian.maven.jgitflow.api.example;

import java.io.File;

import com.atlassian.jgitflow.core.JGitFlowInfo;
import com.atlassian.maven.jgitflow.api.exception.MavenJGitFlowExtensionException;
import com.atlassian.maven.jgitflow.api.impl.NoopMavenReleaseFinishExtension;
import com.atlassian.maven.jgitflow.api.util.JGitFlowCommitHelper;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import org.eclipse.jgit.api.Git;

/**
 * An example class meant to be used on release-finish to update a README.md file with the new release version
 */
public class UpdateReadmeExtension extends NoopMavenReleaseFinishExtension
{

    public static final String README_MD = "README.md";

    /**
     * Updates the README.md file on release-finish with the new release version.
     * Since the change happens on master first and is then back-merged into develop, we only need to change the file when master is updated.
     *
     * @param newVersion
     * @param oldVersion
     * @param flow
     * @throws MavenJGitFlowExtensionException
     */
    @Override
    public void onMasterBranchVersionChange(String newVersion, String oldVersion, JGitFlowInfo flow) throws MavenJGitFlowExtensionException
    {
        try
        {
            Git git = flow.git();

            //get the README.md file
            File readmeFile = new File(flow.getProjectRoot(), README_MD);

            //do the replacement
            //NOTE: This is not performant or scalable. It's only here for example purposes.
            String readmeContent = Files.toString(readmeFile, Charsets.UTF_8);
            String newContent = readmeContent.replace(oldVersion, newVersion);

            Files.write(newContent, readmeFile, Charsets.UTF_8);

            //now commit the change
            JGitFlowCommitHelper.commitAllChanges(flow, "updating version in README.md");

        }
        catch (Exception e)
        {
            throw new MavenJGitFlowExtensionException("Error updating " + README_MD + " file!", e);
        }
    }
}
