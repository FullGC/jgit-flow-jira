package ut.com.atlassian.maven.plugins.jgitflow;

import com.atlassian.jgitflow.core.JGitFlowInfo;
import com.atlassian.maven.jgitflow.api.exception.MavenJGitFlowExtensionException;
import com.atlassian.maven.jgitflow.api.impl.NoopMavenReleaseFinishExtension;

public class TestFinishExtension extends NoopMavenReleaseFinishExtension
{
    private String oldVersion;
    private String newVersion;

    @Override
    public void onMasterBranchVersionChange(String newVersion, String oldVersion, JGitFlowInfo flow) throws MavenJGitFlowExtensionException
    {
        this.oldVersion = oldVersion;
        this.newVersion = newVersion;
    }

    public String getOldVersion()
    {
        return oldVersion;
    }

    public String getNewVersion()
    {
        return newVersion;
    }
}
