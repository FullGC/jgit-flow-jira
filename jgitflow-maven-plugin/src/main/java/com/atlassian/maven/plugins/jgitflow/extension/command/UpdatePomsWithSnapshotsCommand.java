package com.atlassian.maven.plugins.jgitflow.extension.command;

import java.util.List;

import com.atlassian.jgitflow.core.GitFlowConfiguration;
import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.JGitFlowReporter;
import com.atlassian.jgitflow.core.exception.JGitFlowExtensionException;
import com.atlassian.jgitflow.core.extension.ExtensionCommand;
import com.atlassian.jgitflow.core.extension.ExtensionFailStrategy;
import com.atlassian.maven.plugins.jgitflow.BranchType;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.VersionType;
import com.atlassian.maven.plugins.jgitflow.helper.MavenExecutionHelper;
import com.atlassian.maven.plugins.jgitflow.helper.PomUpdater;
import com.atlassian.maven.plugins.jgitflow.helper.ProjectHelper;
import com.atlassian.maven.plugins.jgitflow.provider.*;
import com.atlassian.maven.plugins.jgitflow.util.NamingUtil;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.release.util.ReleaseUtil;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.jgit.api.Git;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class UpdatePomsWithSnapshotsCommand implements ExtensionCommand
{
    @Requirement
    private ContextProvider contextProvider;

    @Requirement
    private JGitFlowProvider jGitFlowProvider;

    @Requirement
    private MavenExecutionHelper mavenExecutionHelper;
    
    @Requirement
    private PomUpdater pomUpdater;
    
    @Requirement
    private ProjectHelper projectHelper;
    
    @Requirement
    private ReactorProjectsProvider projectsProvider;

    @Requirement
    private MavenSessionProvider sessionProvider;
    
    private final BranchType branchType;

    public UpdatePomsWithSnapshotsCommand(BranchType branchType)
    {
        this.branchType = branchType;
    }

    @Override
    public void execute(GitFlowConfiguration configuration, Git git, JGitFlowReporter reporter) throws JGitFlowExtensionException
    {
        ProjectCacheKey cacheKey = null;
        VersionType versionType = null;
        String versionSuffix = "";
        String branchName = "";
        
        try
        {
            ReleaseContext ctx = contextProvider.getContext();
            JGitFlow flow = jGitFlowProvider.gitFlow();
            
            switch (branchType)
            {
                case RELEASE:
                    cacheKey = ProjectCacheKey.RELEASE_START_LABEL;
                    versionType = VersionType.RELEASE;
                    versionSuffix = ctx.getReleaseBranchVersionSuffix();
                    break;

                case HOTFIX:
                    cacheKey = ProjectCacheKey.HOTFIX_LABEL;
                    versionType = VersionType.HOTFIX;
                    versionSuffix = "";
                    break;
            }

            checkNotNull(cacheKey);
            checkNotNull(versionType);

            MavenSession session = sessionProvider.getSession();
            
            branchName = git.getRepository().getBranch();
            String branchPrefix = flow.getPrefixForBranch(branchName);

            String unprefixedBranchName = NamingUtil.unprefixedBranchName(branchPrefix, branchName);

            //reload the reactor projects for release
            MavenSession branchSession = mavenExecutionHelper.getSessionForBranch(branchName, ReleaseUtil.getRootProject(projectsProvider.getReactorProjects()), session);
            List<MavenProject> branchProjects = branchSession.getSortedProjects();

            pomUpdater.addSnapshotToPomVersions(cacheKey, versionType, unprefixedBranchName, versionSuffix, branchProjects);

            projectHelper.commitAllPoms(flow.git(), branchProjects, ctx.getScmCommentPrefix() + "updating poms for " + unprefixedBranchName + " release" + ctx.getScmCommentSuffix());
        }
        catch (Exception e)
        {
            throw new JGitFlowExtensionException("Error updating poms with snapshot versions for branch '" + branchName + "'");
        }
    }

    @Override
    public ExtensionFailStrategy failStrategy()
    {
        return ExtensionFailStrategy.ERROR;
    }
}
