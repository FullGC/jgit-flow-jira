package com.atlassian.maven.plugins.jgitflow.extension.command.external;

import com.atlassian.jgitflow.core.BranchType;
import com.atlassian.jgitflow.core.JGitFlowInfo;
import com.atlassian.maven.jgitflow.api.MavenJGitFlowExtension;
import com.atlassian.maven.jgitflow.api.MavenReleaseStartExtension;
import com.atlassian.maven.jgitflow.api.exception.MavenJGitFlowExtensionException;

import org.codehaus.plexus.component.annotations.Component;

@Component(role = StartReleaseExternalExecutor.class)
public class StartReleaseExternalExecutor extends CachedVersionExternalExecutor
{
    @Override
    public void execute(MavenJGitFlowExtension extension, String newVersion, String oldVersion, JGitFlowInfo flow) throws MavenJGitFlowExtensionException
    {
        if (null == extension || !MavenReleaseStartExtension.class.isAssignableFrom(extension.getClass()))
        {
            return;
        }

        MavenReleaseStartExtension startExtension = (MavenReleaseStartExtension) extension;
        try
        {
            BranchType type = branchHelper.getCurrentBranchType();

            switch (type)
            {
                case DEVELOP:
                    startExtension.onDevelopBranchVersionChange(newVersion, oldVersion, flow);
                    break;
            }
        }
        catch (Exception e)
        {
            throw new MavenJGitFlowExtensionException("Error running external extension", e);
        }
    }
}
