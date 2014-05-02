package com.atlassian.maven.plugins.jgitflow.extension;

import java.util.List;

import com.atlassian.jgitflow.core.GitFlowConfiguration;
import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.JGitFlowReporter;
import com.atlassian.maven.plugins.jgitflow.VersionState;
import com.atlassian.jgitflow.core.exception.JGitFlowExtensionException;
import com.atlassian.jgitflow.core.extension.ExtensionCommand;
import com.atlassian.jgitflow.core.extension.ExtensionFailStrategy;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.UnresolvedSnapshotsException;
import com.atlassian.maven.plugins.jgitflow.helper.ProjectHelper;

import com.google.common.base.Joiner;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.eclipse.jgit.api.Git;

public class ProductionPreBranchCommand implements ExtensionCommand
{
    private final JGitFlow flow;
    private final ReleaseContext ctx;
    private final List<MavenProject> reactorProjects;
    private final MavenSession session;
    private final ProjectHelper projectHelper;
    private final VersionState expectedVersionState;

    public ProductionPreBranchCommand(JGitFlow flow, ReleaseContext ctx, List<MavenProject> reactorProjects, MavenSession session, ProjectHelper projectHelper, VersionState expectedVersionState)
    {
        this.flow = flow;
        this.ctx = ctx;
        this.reactorProjects = reactorProjects;
        this.session = session;
        this.projectHelper = projectHelper;
        this.expectedVersionState = expectedVersionState;
    }

    @Override
    public void execute(GitFlowConfiguration configuration, Git git, JGitFlowReporter reporter) throws JGitFlowExtensionException
    {
        String currentBranch = git.getRepository().getBranch();
        //reload the reactor projects for develop
        MavenSession developSession = getSessionForBranch(flow, currentBranch, reactorProjects, session);
        List<MavenProject> branchProjects = developSession.getSortedProjects();

        projectHelper.checkPomForVersionState(expectedVersionState,branchProjects);

        if(!ctx.isAllowSnapshots())
        {
            List<String> snapshots = projectHelper.checkForNonReactorSnapshots(currentBranch, branchProjects);
            if(!snapshots.isEmpty())
            {
                String details = Joiner.on(ls).join(snapshots);
                throw new UnresolvedSnapshotsException("Cannot start a release due to snapshot dependencies:" + ls + details);
            }
        }

        if(ctx.isPushReleases() || !ctx.isNoTag())
        {
            projectHelper.ensureOrigin(ctx.getDefaultOriginUrl(), ctx.isAlwaysUpdateOrigin(), flow);
        }
    }

    @Override
    public ExtensionFailStrategy failStrategy()
    {
        return ExtensionFailStrategy.ERROR;
    }
}
