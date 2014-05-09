package com.atlassian.maven.plugins.jgitflow.manager;

import java.util.List;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.exception.JGitFlowException;
import com.atlassian.maven.plugins.jgitflow.BranchType;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.VersionType;
import com.atlassian.maven.plugins.jgitflow.exception.MavenJGitFlowException;
import com.atlassian.maven.plugins.jgitflow.extension.ReleaseStartPluginExtension;
import com.atlassian.maven.plugins.jgitflow.helper.JGitFlowSetupHelper;
import com.atlassian.maven.plugins.jgitflow.helper.MavenExecutionHelper;
import com.atlassian.maven.plugins.jgitflow.helper.PomUpdater;
import com.atlassian.maven.plugins.jgitflow.helper.ProjectHelper;
import com.atlassian.maven.plugins.jgitflow.manager.tasks.CheckoutAndGetProjects;
import com.atlassian.maven.plugins.jgitflow.manager.tasks.SetupOriginAndFetchIfNeeded;
import com.atlassian.maven.plugins.jgitflow.manager.tasks.VerifyInitialVersionState;
import com.atlassian.maven.plugins.jgitflow.provider.BranchLabelProvider;
import com.atlassian.maven.plugins.jgitflow.provider.JGitFlowProvider;
import com.atlassian.maven.plugins.jgitflow.provider.ProjectCacheKey;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Requirement;

public abstract class AbstractProductionBranchManager extends AbstractFlowReleaseManager
{
    private final BranchType branchType;

    public static final String ls = System.getProperty("line.separator");

    @Requirement
    protected JGitFlowSetupHelper setupHelper;

    @Requirement
    protected MavenExecutionHelper mavenExecutionHelper;

    @Requirement
    protected ProjectHelper projectHelper;

    @Requirement
    protected BranchLabelProvider labelProvider;

    @Requirement
    protected PomUpdater pomUpdater;

    @Requirement
    protected JGitFlowProvider jGitFlowProvider;

    @Requirement
    protected SetupOriginAndFetchIfNeeded setupOriginAndFetchIfNeeded;

    @Requirement
    protected CheckoutAndGetProjects checkoutAndGetProjects;

    @Requirement
    protected VerifyInitialVersionState verifyInitialVersionState;

    @Requirement
    protected ReleaseStartPluginExtension extension;

    public AbstractProductionBranchManager(BranchType branchType)
    {
        this.branchType = branchType;
    }

    public String preflight(ReleaseContext ctx, List<MavenProject> reactorProjects, MavenSession session) throws JGitFlowException, MavenJGitFlowException
    {
        JGitFlow flow = null;

        setupProviders(ctx, session, reactorProjects);

        String branchName = null;
        VersionType versionType = null;
        ProjectCacheKey cacheKey = null;

        flow = jGitFlowProvider.gitFlow();

        setupHelper.runCommonSetup();

        setupOriginAndFetchIfNeeded.run();

        List<MavenProject> developProjects = checkoutAndGetProjects.run(flow.getDevelopBranchName(), reactorProjects);

        verifyInitialVersionState.run(BranchType.RELEASE, developProjects);

        return labelProvider.getVersionLabel(VersionType.RELEASE, ProjectCacheKey.RELEASE_START_LABEL, developProjects);

    }
}
