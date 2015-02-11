package com.atlassian.maven.plugins.jgitflow.provider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.atlassian.jgitflow.core.BranchType;
import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.JGitFlowConstants;
import com.atlassian.jgitflow.core.JGitFlowReporter;
import com.atlassian.jgitflow.core.exception.JGitFlowException;
import com.atlassian.jgitflow.core.exception.JGitFlowGitAPIException;
import com.atlassian.jgitflow.core.util.GitHelper;
import com.atlassian.maven.plugins.jgitflow.PrettyPrompter;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.VersionType;
import com.atlassian.maven.plugins.jgitflow.exception.MavenJGitFlowException;

import com.google.common.base.Strings;

import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.release.util.ReleaseUtil;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.components.interactivity.PrompterException;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;

@Component(role = BranchLabelProvider.class)
public class DefaultBranchLabelProvider extends AbstractLogEnabled implements BranchLabelProvider
{
    @Requirement
    private VersionProvider versionProvider;

    @Requirement
    private PrettyPrompter prompter;

    @Requirement
    private ContextProvider contextProvider;

    @Requirement
    private JGitFlowProvider jGitFlowProvider;


    @Override
    public String getNextVersionLabel(VersionType versionType, ProjectCacheKey cacheKey, List<MavenProject> reactorProjects) throws MavenJGitFlowException
    {
        Map<String, String> versions = versionProvider.getNextVersionsForType(versionType, cacheKey, reactorProjects);
        MavenProject rootProject = ReleaseUtil.getRootProject(reactorProjects);
        String rootProjectId = ArtifactUtils.versionlessKey(rootProject.getGroupId(), rootProject.getArtifactId());
        return versions.get(rootProjectId);
    }

    @Override
    public String getFeatureStartName() throws MavenJGitFlowException
    {
        try
        {
            ReleaseContext ctx = contextProvider.getContext();
            JGitFlow flow = jGitFlowProvider.gitFlow();

            String featureName = StringUtils.defaultString(ctx.getDefaultFeatureName());

            if (ctx.isInteractive())
            {
                featureName = promptForFeatureName(flow.getFeatureBranchPrefix(), featureName);
            }
            else
            {
                if (StringUtils.isBlank(featureName))
                {
                    throw new MavenJGitFlowException("Missing featureName mojo option.");
                }
            }

            return featureName;
        }
        catch (JGitFlowException e)
        {
            throw new MavenJGitFlowException("Error getting feature start name", e);
        }

    }

    @Override
    public String getFeatureFinishName() throws MavenJGitFlowException
    {
        JGitFlow flow;

        try
        {
            flow = jGitFlowProvider.gitFlow();
        }
        catch (JGitFlowException e)
        {
            throw new MavenJGitFlowException(e);
        }

        ReleaseContext ctx = contextProvider.getContext();

        String featureName = StringUtils.defaultString(ctx.getDefaultFeatureName());

        if (StringUtils.isBlank(featureName))
        {
            String currentBranch = null;

            try
            {
                currentBranch = flow.git().getRepository().getBranch();
                getLogger().debug("Current Branch is: " + currentBranch);
            }
            catch (IOException e)
            {
                throw new MavenJGitFlowException(e);
            }

            getLogger().debug("Feature Prefix is: " + flow.getFeatureBranchPrefix());
            getLogger().debug("Branch starts with feature prefix?: " + currentBranch.startsWith(flow.getFeatureBranchPrefix()));
            if (currentBranch.startsWith(flow.getFeatureBranchPrefix()))
            {
                featureName = currentBranch.replaceFirst(flow.getFeatureBranchPrefix(), "");
            }
        }

        if (ctx.isInteractive())
        {
            List<String> possibleValues = new ArrayList<String>();
            if (null == featureName)
            {
                featureName = "";
            }

            try
            {
                String rheadPrefix = Constants.R_HEADS + flow.getFeatureBranchPrefix();
                String rOriginPrefix = JGitFlowConstants.R_REMOTE_ORIGIN + flow.getFeatureBranchPrefix();

                List<Ref> branches = GitHelper.listBranchesWithPrefix(flow.git(), flow.getFeatureBranchPrefix());

                for (Ref branch : branches)
                {
                    String simpleName = "";

                    if (branch.getName().contains(rheadPrefix))
                    {
                        simpleName = branch.getName().substring(branch.getName().indexOf(rheadPrefix) + rheadPrefix.length());
                    }

                    if (branch.getName().contains(rOriginPrefix))
                    {
                        simpleName = branch.getName().substring(branch.getName().indexOf(rOriginPrefix) + rOriginPrefix.length());
                    }

                    if (!Strings.isNullOrEmpty(simpleName) && !possibleValues.contains(simpleName))
                    {
                        possibleValues.add(simpleName);
                    }
                }

                featureName = promptForExistingFeatureName(flow.getFeatureBranchPrefix(), featureName, possibleValues);
            }
            catch (JGitFlowGitAPIException e)
            {
                throw new MavenJGitFlowException("Unable to determine feature names", e);
            }
        }
        else
        {
            if (StringUtils.isBlank(featureName))
            {
                throw new MavenJGitFlowException("Missing featureName mojo option.");
            }
        }

        return featureName;
    }

    @Override
    public String getCurrentProductionVersionLabel(BranchType branchType) throws MavenJGitFlowException
    {
        String branchLabel = "";
        try
        {
            String branchPrefix = "";
            JGitFlow flow = jGitFlowProvider.gitFlow();
            switch (branchType)
            {
                case RELEASE:
                    branchPrefix = flow.getReleaseBranchPrefix();
                    break;

                case HOTFIX:
                    branchPrefix = flow.getHotfixBranchPrefix();
                    break;

                default:
                    throw new MavenJGitFlowException("Unsupported branch type '" + branchType.name() + "' while trying to get the current production branch label");
            }

            List<Ref> productionBranches = GitHelper.listBranchesWithPrefix(flow.git(), branchPrefix);

            if (productionBranches.isEmpty())
            {
                throw new MavenJGitFlowException("Could not find current production branch of type " + branchType.name());
            }

            String rheadPrefix = Constants.R_HEADS + branchPrefix;
            String rOriginPrefix = JGitFlowConstants.R_REMOTE_ORIGIN + branchPrefix;
            Ref productionBranch = productionBranches.get(0);

            if (productionBranch.getName().contains(rheadPrefix))
            {
                return productionBranch.getName().substring(productionBranch.getName().indexOf(rheadPrefix) + rheadPrefix.length());
            }

            if (productionBranch.getName().contains(rOriginPrefix))
            {
                return productionBranch.getName().substring(productionBranch.getName().indexOf(rOriginPrefix) + rOriginPrefix.length());
            }

            throw new JGitFlowException(productionBranch.getName() + " does not match " + rheadPrefix + " or " + rOriginPrefix);
        }
        catch (JGitFlowException e)
        {
            throw new MavenJGitFlowException("Error looking up current production branch label:" + e.getMessage(), e);
        }
    }

    private String promptForFeatureName(String prefix, String defaultFeatureName) throws MavenJGitFlowException
    {
        String message = "What is the feature branch name? " + prefix;
        String name = "";

        try
        {
            name = prompter.promptNotBlank(message, defaultFeatureName);
        }
        catch (PrompterException e)
        {
            throw new MavenJGitFlowException("Error reading feature name from command line " + e.getMessage(), e);
        }

        return name;
    }

    private String promptForExistingFeatureName(String prefix, String defaultFeatureName, List<String> featureBranches) throws MavenJGitFlowException
    {
        String message = "What is the feature branch name? " + prefix;

        String name = "";
        try
        {
            name = prompter.promptNumberedList(message, featureBranches, defaultFeatureName);
        }
        catch (PrompterException e)
        {
            throw new MavenJGitFlowException("Error reading feature name from command line " + e.getMessage(), e);
        }

        return name;
    }
}
