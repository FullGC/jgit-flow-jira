package com.atlassian.maven.plugins.jgitflow.extension.command;

import java.util.List;

import com.atlassian.jgitflow.core.GitFlowConfiguration;
import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.JGitFlowReporter;
import com.atlassian.jgitflow.core.command.JGitFlowCommand;
import com.atlassian.jgitflow.core.exception.JGitFlowExtensionException;
import com.atlassian.jgitflow.core.extension.ExtensionCommand;
import com.atlassian.jgitflow.core.extension.ExtensionFailStrategy;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.helper.BranchHelper;
import com.atlassian.maven.plugins.jgitflow.helper.PomUpdater;
import com.atlassian.maven.plugins.jgitflow.helper.ProjectHelper;
import com.atlassian.maven.plugins.jgitflow.manager.tasks.CheckoutAndGetProjects;
import com.atlassian.maven.plugins.jgitflow.provider.ContextProvider;
import com.atlassian.maven.plugins.jgitflow.provider.JGitFlowProvider;
import com.atlassian.maven.plugins.jgitflow.provider.VersionCacheProvider;

import com.google.common.base.Strings;

import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.jgit.api.Git;

@Component(role = UpdateReleaseWithPreviousVersionsCommand.class)
public class UpdateReleaseWithPreviousVersionsCommand implements ExtensionCommand
{
    @Requirement
    private JGitFlowProvider jGitFlowProvider;

    @Requirement
    private VersionCacheProvider versionCacheProvider;

    @Requirement
    private PomUpdater pomUpdater;

    @Requirement
    private BranchHelper branchHelper;

    @Requirement
    private ProjectHelper projectHelper;

    @Requirement
    private CheckoutAndGetProjects checkoutAndGetProjects;

    @Requirement
    private ContextProvider contextProvider;


    @Override
    public void execute(GitFlowConfiguration configuration, Git git, JGitFlowCommand gitFlowCommand, JGitFlowReporter reporter) throws JGitFlowExtensionException
    {
        try
        {
            String releaseBranchName = branchHelper.getCurrentReleaseBranchNameOrBlank();
            
            if(Strings.isNullOrEmpty(releaseBranchName))
            {
                return;
            }
            
            ReleaseContext ctx = contextProvider.getContext();

            JGitFlow flow = jGitFlowProvider.gitFlow();

            String originalBranchName = branchHelper.getCurrentBranchName();

            List<MavenProject> releaseProjects = checkoutAndGetProjects.run(releaseBranchName).getProjects();

            pomUpdater.copyPomVersionsFromMap(versionCacheProvider.getCachedVersions(),releaseProjects);
            projectHelper.commitAllPoms(git,releaseProjects,ctx.getScmCommentPrefix() + "Updating release poms back to pre merge state" + ctx.getScmCommentSuffix());

            flow.git().checkout().setName(originalBranchName).call();
        }
        catch (Exception e)
        {
            throw new JGitFlowExtensionException("Error updating release poms to previously cached versions", e);
        }
    }

    @Override
    public ExtensionFailStrategy failStrategy()
    {
        return null;
    }
}
