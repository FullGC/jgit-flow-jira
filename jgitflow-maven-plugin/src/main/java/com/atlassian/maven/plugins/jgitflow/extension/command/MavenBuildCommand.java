package com.atlassian.maven.plugins.jgitflow.extension.command;

import com.atlassian.jgitflow.core.GitFlowConfiguration;
import com.atlassian.jgitflow.core.command.JGitFlowCommand;
import com.atlassian.jgitflow.core.exception.JGitFlowExtensionException;
import com.atlassian.jgitflow.core.extension.ExtensionCommand;
import com.atlassian.jgitflow.core.extension.ExtensionFailStrategy;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.helper.BranchHelper;
import com.atlassian.maven.plugins.jgitflow.helper.MavenExecutionHelper;
import com.atlassian.maven.plugins.jgitflow.helper.SessionAndProjects;
import com.atlassian.maven.plugins.jgitflow.provider.ContextProvider;

import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.release.util.ReleaseUtil;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.jgit.api.Git;

@Component(role = MavenBuildCommand.class)
public class MavenBuildCommand implements ExtensionCommand
{
    @Requirement
    private ContextProvider contextProvider;

    @Requirement
    private BranchHelper branchHelper;

    @Requirement
    MavenExecutionHelper mavenExecutionHelper;

    @Override
    public void execute(GitFlowConfiguration configuration, Git git, JGitFlowCommand gitFlowCommand) throws JGitFlowExtensionException
    {
        try
        {
            ReleaseContext ctx = contextProvider.getContext();
            SessionAndProjects sap = branchHelper.getSessionAndProjectsForCurrentBranch();

            MavenProject rootProject = ReleaseUtil.getRootProject(sap.getProjects());

            if (!ctx.isNoBuild())
            {
                mavenExecutionHelper.execute(rootProject, sap.getSession());
            }
        }
        catch (Exception e)
        {
            throw new JGitFlowExtensionException("Error building project from " + this.getClass().getSimpleName(), e);
        }
    }

    @Override
    public ExtensionFailStrategy failStrategy()
    {
        return ExtensionFailStrategy.ERROR;
    }
}
