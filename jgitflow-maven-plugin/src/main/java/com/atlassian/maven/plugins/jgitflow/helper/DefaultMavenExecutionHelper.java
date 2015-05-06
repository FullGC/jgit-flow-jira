package com.atlassian.maven.plugins.jgitflow.helper;

import com.atlassian.jgitflow.core.JGitFlow;
import com.atlassian.jgitflow.core.exception.JGitFlowException;
import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.ReactorReloadException;
import com.atlassian.maven.plugins.jgitflow.provider.ContextProvider;
import com.atlassian.maven.plugins.jgitflow.provider.JGitFlowProvider;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ReactorManager;
import org.apache.maven.model.Profile;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.shared.release.ReleaseResult;
import org.apache.maven.shared.release.env.DefaultReleaseEnvironment;
import org.apache.maven.shared.release.env.ReleaseEnvironment;
import org.apache.maven.shared.release.exec.MavenExecutor;
import org.apache.maven.shared.release.exec.MavenExecutorException;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Stack;

import static com.google.common.collect.Lists.newArrayList;

/**
 * @since version
 */
@Component(role = MavenExecutionHelper.class)
public class DefaultMavenExecutionHelper implements MavenExecutionHelper
{
    @Requirement
    protected Map<String, MavenExecutor> mavenExecutors;

    @Requirement
    protected MavenProjectBuilder projectBuilder;

    @Requirement
    protected JGitFlowProvider jGitFlowProvider;

    @Requirement
    private ContextProvider contextProvider;

    @Override
    public void execute(MavenProject project, MavenSession session) throws MavenExecutorException
    {
        ReleaseContext ctx = contextProvider.getContext();

        String goal = ctx.getGoals();

        if (ctx.isNoDeploy())
        {
            goal = "clean install";
        }

        execute(project, session, goal);
    }

    @Override
    public void execute(MavenProject project, MavenSession session, String goals) throws MavenExecutorException
    {
        ReleaseContext ctx = contextProvider.getContext();

        List<String> argList = new ArrayList<String>();


        if (ctx.isUseReleaseProfile())
        {
            argList.add("-DperformRelease=true");
        }

        String args = ctx.getArgs();
        if (Strings.isNullOrEmpty(args))
        {

            // use default user properties + default profiles

            Properties userProps = session.getUserProperties();

            for (String key : userProps.stringPropertyNames())
            {
                argList.add("-D" + key + "=\"" + userProps.getProperty(key) + "\"");
            }

            for (String profileId : getActiveProfileIds(project, session))
            {
                argList.add("-P" + profileId);
            }

        } else
        {

            // use user specific release arguments
            argList.add(args.trim());

        }

        String additionalArgs = Joiner.on(" ").join(argList);

        ReleaseResult result = new ReleaseResult();
        ReleaseEnvironment env = new DefaultReleaseEnvironment();
        env.setSettings(session.getSettings());
        MavenExecutor mavenExecutor = mavenExecutors.get(env.getMavenExecutorId());

        mavenExecutor.executeGoals(ctx.getBaseDir(), goals, env, ctx.isInteractive(), additionalArgs, result);

    }

    @Override
    public MavenSession reloadReactor(MavenProject rootProject, MavenSession oldSession) throws ReactorReloadException
    {
        List<MavenProject> reactorProjects = new ArrayList<MavenProject>();

        List<File> pomFiles = newArrayList(rootProject.getFile());
        try
        {
            MavenProject project = null;
            //try maven3 first
            try
            {
                if (rootProject.getFile().exists() && rootProject.getFile().canRead())
                {
                    Method getRequestMethod = oldSession.getClass().getMethod("getRequest");
                    Object mavenExecutionRequest = getRequestMethod.invoke(oldSession);
                    Method getProjectBuildingRequest = mavenExecutionRequest.getClass().getMethod("getProjectBuildingRequest");
                    Object pbr = getProjectBuildingRequest.invoke(mavenExecutionRequest);

                    Object pb = oldSession.getContainer().lookup("org.apache.maven.project.ProjectBuilder");

                    Class requestClass = Class.forName("org.apache.maven.project.ProjectBuildingRequest");

                    Method buildMethod = pb.getClass().getMethod("build", List.class, boolean.class, requestClass);
                    List results = (List) buildMethod.invoke(pb, pomFiles, true, pbr);

                    for (Object result : results)
                    {
                        Method getProjectMethod = result.getClass().getMethod("getProject");
                        getProjectMethod.setAccessible(true);
                        project = (MavenProject) getProjectMethod.invoke(result);
                        project.setActiveProfiles(rootProject.getActiveProfiles());
                        reactorProjects.add(project);
                    }
                }

            }
            catch (Exception e)
            {
                // MJF-112: Check that the exception is a result of not having Maven 3
                // installed, or a genuine project configuration error
                try
                {
                    oldSession.getClass().getMethod("getProjectBuilderConfiguration");
                }
                catch (NoSuchMethodException e1)
                {
                    // There is no Maven 2 environment, just report the error
                    throw e;
                }

                Stack<File> projectFiles = new Stack<File>();
                projectFiles.push(rootProject.getFile());

                while (!projectFiles.isEmpty())
                {
                    File file = (File) projectFiles.pop();

                    if (!file.exists() || !file.canRead())
                    {
                        continue;
                    }

                    // Fallback to Maven 2 API
                    project = projectBuilder.build(file, oldSession.getProjectBuilderConfiguration());
                    project.setActiveProfiles(rootProject.getActiveProfiles());
                    List<String> moduleNames = project.getModules();

                    for (String moduleName : moduleNames)
                    {
                        //if moduleName is a file treat as explicitly defined pom.xml
                        File baseFile = new File(file.getParentFile(), moduleName);
                        if (baseFile.isFile())
                        {
                            projectFiles.push(baseFile);
                        }
                        else
                        {
                            projectFiles.push(new File(baseFile, File.separator + "pom.xml"));
                        }

                    }

                    reactorProjects.add(project);
                }
            }


            ReactorManager reactorManager = new ReactorManager(reactorProjects);
            MavenSession newSession = new MavenSession(
                    oldSession.getContainer()
                    , oldSession.getSettings()
                    , oldSession.getLocalRepository()
                    , oldSession.getEventDispatcher()
                    , reactorManager
                    , oldSession.getGoals()
                    , oldSession.getExecutionRootDirectory()
                    , oldSession.getExecutionProperties()
                    , oldSession.getUserProperties()
                    , oldSession.getStartTime()
            );

            //in case of maven 3
            try
            {
                Method setProjectsMethod = newSession.getClass().getMethod("setProjects", List.class);
                setProjectsMethod.invoke(newSession, reactorManager.getSortedProjects());
            }
            catch (Exception ignore)
            {
                //ignore
            }

            return newSession;
        }
        catch (Exception e)
        {
            throw new ReactorReloadException("Error reloading Maven reactor projects", e);
        }

    }

    @Override
    public MavenSession getSessionForBranch(String branchName, MavenProject rootProject, MavenSession oldSession) throws JGitFlowException, IOException, GitAPIException, ReactorReloadException
    {
        JGitFlow flow = jGitFlowProvider.gitFlow();
        String originalBranch = flow.git().getRepository().getBranch();

        flow.git().checkout().setName(branchName).call();

        //reload the reactor projects
        MavenSession newSession = reloadReactor(rootProject, oldSession);

        flow.git().checkout().setName(originalBranch).call();

        return newSession;
    }

    private List<String> getActiveProfileIds(MavenProject project, MavenSession session)
    {
        List<String> profiles = new ArrayList<String>();
        try
        {
            // Try to use M3-methods
            Method getRequestMethod = session.getClass().getMethod("getRequest");
            Object mavenExecutionRequest = getRequestMethod.invoke(session);
            Method getActiveProfilesMethod = mavenExecutionRequest.getClass().getMethod("getActiveProfiles");
            profiles = (List<String>) getActiveProfilesMethod.invoke(mavenExecutionRequest);
        }
        catch (Exception e)
        {
            //do nothing
        }

        if (project.getActiveProfiles() != null && !project.getActiveProfiles().isEmpty())
        {
            for (Object profile : project.getActiveProfiles())
            {
                profiles.add(((Profile) profile).getId());
            }
        }
        return profiles;
    }

}
