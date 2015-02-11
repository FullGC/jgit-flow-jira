package com.atlassian.maven.plugins.jgitflow.extension.command;

import java.util.List;

import com.atlassian.jgitflow.core.GitFlowConfiguration;
import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.command.JGitFlowCommand;
import com.atlassian.jgitflow.core.exception.JGitFlowExtensionException;
import com.atlassian.jgitflow.core.extension.ExtensionCommand;
import com.atlassian.jgitflow.core.extension.ExtensionFailStrategy;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.VersionType;
import com.atlassian.maven.plugins.jgitflow.helper.BranchHelper;
import com.atlassian.maven.plugins.jgitflow.helper.PomUpdater;
import com.atlassian.maven.plugins.jgitflow.helper.ProjectHelper;
import com.atlassian.maven.plugins.jgitflow.helper.SessionAndProjects;
import com.atlassian.maven.plugins.jgitflow.manager.tasks.CheckoutAndGetProjects;
import com.atlassian.maven.plugins.jgitflow.provider.BranchLabelProvider;
import com.atlassian.maven.plugins.jgitflow.provider.ContextProvider;
import com.atlassian.maven.plugins.jgitflow.provider.JGitFlowProvider;
import com.atlassian.maven.plugins.jgitflow.provider.ProjectCacheKey;

import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.jgit.api.Git;

@Component(role = UpdateDevelopWithNextDevVersionCommand.class)
public class UpdateDevelopWithNextDevVersionCommand implements ExtensionCommand
{
    @Requirement
    private BranchHelper branchHelper;

    @Requirement
    private ProjectHelper projectHelper;

    @Requirement
    private JGitFlowProvider jGitFlowProvider;

    @Requirement
    private CheckoutAndGetProjects checkoutAndGetProjects;

    @Requirement
    private BranchLabelProvider labelProvider;

    @Requirement
    private PomUpdater pomUpdater;

    @Requirement
    private ContextProvider contextProvider;

    @Override
    public void execute(GitFlowConfiguration configuration, Git git, JGitFlowCommand gitFlowCommand) throws JGitFlowExtensionException
    {
        try
        {
            JGitFlow flow = jGitFlowProvider.gitFlow();
            ReleaseContext ctx = contextProvider.getContext();

            String originalBranchName = branchHelper.getCurrentBranchName();

            //check out develop and reload the reactor
            SessionAndProjects sessionAndProjects = checkoutAndGetProjects.run(flow.getDevelopBranchName());
            List<MavenProject> developProjects = sessionAndProjects.getProjects();

            String developLabel = labelProvider.getNextVersionLabel(VersionType.DEVELOPMENT, ProjectCacheKey.DEVELOP_BRANCH, developProjects);

            pomUpdater.updatePomsWithNextDevelopmentVersion(ProjectCacheKey.DEVELOP_BRANCH, developProjects);

            projectHelper.commitAllPoms(flow.git(), developProjects, ctx.getScmCommentPrefix() + "updating poms for " + developLabel + " development" + ctx.getScmCommentSuffix());

            flow.git().checkout().setName(originalBranchName).call();

        }
        catch (Exception e)
        {
            throw new JGitFlowExtensionException("Error updating develop poms to next development version", e);
        }
    }

    @Override
    public ExtensionFailStrategy failStrategy()
    {
        return ExtensionFailStrategy.ERROR;
    }
}
