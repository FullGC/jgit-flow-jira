package com.atlassian.maven.plugins.jgitflow;

import java.io.File;

import com.atlassian.jgitflow.core.JGitFlowInfo;
import com.atlassian.maven.jgitflow.api.exception.MavenJGitFlowExtensionException;
import com.atlassian.maven.jgitflow.api.impl.NoopMavenReleaseFinishExtension;
import com.atlassian.maven.jgitflow.api.util.JGitFlowCommitHelper;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;

public class ITFinishExtension extends NoopMavenReleaseFinishExtension
{
    public static final String EXTENSION_RESULT = "ext-result.txt";
    @Override
    public void onMasterBranchVersionChange(String newVersion, String oldVersion, JGitFlowInfo flow) throws MavenJGitFlowExtensionException
    {
        try
        {
            Git git = flow.git();

            //get the README.md file
            File extFile = new File(flow.getProjectRoot(), EXTENSION_RESULT);
            
            if(!extFile.exists())
            {
                FileUtils.touch(extFile);
            }
            
            FileUtils.writeStringToFile(extFile,oldVersion + ":" + newVersion);

            //now commit the change
            JGitFlowCommitHelper.commitAllChanges(flow, "updating version in extension file");

        }
        catch (Exception e)
        {
            throw new MavenJGitFlowExtensionException("Error updating " + EXTENSION_RESULT + " file!", e);
        }
    }

}
