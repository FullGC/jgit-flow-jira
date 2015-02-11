package com.atlassian.maven.plugins.jgitflow.extension.command;

import java.util.List;

import com.atlassian.jgitflow.core.BranchType;
import com.atlassian.jgitflow.core.GitFlowConfiguration;
import com.atlassian.jgitflow.core.command.JGitFlowCommand;
import com.atlassian.jgitflow.core.exception.JGitFlowExtensionException;
import com.atlassian.jgitflow.core.extension.ExtensionCommand;
import com.atlassian.jgitflow.core.extension.ExtensionFailStrategy;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.VersionState;
import com.atlassian.maven.plugins.jgitflow.exception.UnresolvedSnapshotsException;
import com.atlassian.maven.plugins.jgitflow.helper.BranchHelper;
import com.atlassian.maven.plugins.jgitflow.helper.ProjectHelper;
import com.atlassian.maven.plugins.jgitflow.provider.ContextProvider;
import com.atlassian.maven.plugins.jgitflow.provider.ProjectCacheKey;

import com.google.common.base.Joiner;

import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.jgit.api.Git;

@Component(role = VerifyReleaseVersionStateAndDepsCommand.class)
public class VerifyReleaseVersionStateAndDepsCommand implements ExtensionCommand
{
    private static final String ls = System.getProperty("line.separator");

    @Requirement
    private ContextProvider contextProvider;

    @Requirement
    private ProjectHelper projectHelper;

    @Requirement
    private BranchHelper branchHelper;


    @Override
    public void execute(GitFlowConfiguration configuration, Git git, JGitFlowCommand gitFlowCommand) throws JGitFlowExtensionException
    {
        try
        {
            BranchType branchType = branchHelper.getCurrentBranchType();
            ProjectCacheKey cacheKey = null;

            switch (branchType)
            {
                case RELEASE:
                    cacheKey = ProjectCacheKey.RELEASE_BRANCH;
                    break;

                case HOTFIX:
                    cacheKey = ProjectCacheKey.HOTFIX_BRANCH;
                    break;

                case DEVELOP:
                    cacheKey = ProjectCacheKey.DEVELOP_BRANCH;
                    break;

                case MASTER:
                    cacheKey = ProjectCacheKey.MASTER_BRANCH;
                    break;

                case FEATURE:
                    cacheKey = ProjectCacheKey.FEATURE_BRANCH;
                    break;

                default:
                    throw new JGitFlowExtensionException("Unsupported branch type '" + branchType.name() + "' while running " + this.getClass().getSimpleName() + " command");
            }

            ReleaseContext ctx = contextProvider.getContext();
            List<MavenProject> branchProjects = branchHelper.getProjectsForCurrentBranch();

            projectHelper.checkPomForVersionState(VersionState.RELEASE, branchProjects);

            if (!ctx.isAllowSnapshots())
            {
                List<String> snapshots = projectHelper.checkForNonReactorSnapshots(cacheKey, branchProjects);
                if (!snapshots.isEmpty())
                {
                    String details = Joiner.on(ls).join(snapshots);
                    throw new UnresolvedSnapshotsException("Cannot finish a " + branchType.name().toLowerCase() + " due to snapshot dependencies:" + ls + details);
                }
            }
        }
        catch (Exception e)
        {
            throw new JGitFlowExtensionException("Error verifying version state in poms: " + e.getMessage(), e);
        }
    }

    @Override
    public ExtensionFailStrategy failStrategy()
    {
        return ExtensionFailStrategy.ERROR;
    }
}
