package com.atlassian.maven.plugins.jgitflow.extension;

import com.atlassian.jgitflow.core.extension.HotfixFinishExtension;
import com.atlassian.maven.jgitflow.api.MavenJGitFlowExtension;
import com.atlassian.maven.plugins.jgitflow.extension.command.UpdateCurrentBranchWithHotfixVersionsCommand;
import com.atlassian.maven.plugins.jgitflow.extension.command.UpdateDevelopWithPreviousVersionsCommand;
import com.atlassian.maven.plugins.jgitflow.extension.command.UpdateReleaseWithPreviousVersionsCommand;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;

@Component(role = HotfixFinishPluginExtension.class)
public class HotfixFinishPluginExtension extends ProductionBranchMergingPluginExtension implements HotfixFinishExtension
{
    @Requirement
    private UpdateCurrentBranchWithHotfixVersionsCommand updateCurrentBranchWithHotfixVersionsCommand;

    @Requirement
    private UpdateDevelopWithPreviousVersionsCommand updateDevelopWithPreviousVersionsCommand;

    @Requirement
    private UpdateReleaseWithPreviousVersionsCommand updateReleaseWithPreviousVersionsCommand;

    @Override
    public void init(MavenJGitFlowExtension externalExtension)
    {
        super.init(externalExtension);

        //we need to avoid merge conflicts from master to develop with hotfix versions

        //update develop to hotfix versions
        addBeforeDevelopMergeCommands(updateCurrentBranchWithHotfixVersionsCommand);

        //update develop to previous development versions
        addAfterDevelopMergeCommands(updateDevelopWithPreviousVersionsCommand);

        //update release to hotfix versions
        addBeforeReleaseMergeCommands(updateCurrentBranchWithHotfixVersionsCommand);

        //update release to previous development versions
        addAfterReleaseMergeCommands(updateReleaseWithPreviousVersionsCommand);
    }
}
