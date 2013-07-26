package com.atlassian.maven.plugins.jgitflow.helper;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;

import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.ReactorReloadException;

import com.google.common.base.Joiner;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ReactorManager;
import org.apache.maven.model.Profile;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.profiles.DefaultProfileManager;
import org.apache.maven.profiles.ProfileManager;
import org.apache.maven.project.*;
import org.apache.maven.shared.release.ReleaseResult;
import org.apache.maven.shared.release.env.DefaultReleaseEnvironment;
import org.apache.maven.shared.release.env.ReleaseEnvironment;
import org.apache.maven.shared.release.exec.MavenExecutor;
import org.apache.maven.shared.release.exec.MavenExecutorException;
import org.codehaus.plexus.util.dag.CycleDetectedException;

/**
 * @since version
 */
public class DefaultMavenExecutionHelper implements MavenExecutionHelper
{
    @Component
    protected Map<String, MavenExecutor> mavenExecutors;

    @Component
    protected MavenProjectBuilder projectBuilder;
    
    @Override
    public void execute(MavenProject project, ReleaseContext ctx, MavenSession session) throws MavenExecutorException
    {
        List<String> argList = new ArrayList<String>();
        
        Properties userProps = session.getUserProperties();
        
        for(String key : userProps.stringPropertyNames())
        {
            argList.add("-D" + key + "=" + userProps.getProperty(key));
        }
        
        if(ctx.isUseReleaseProfile())
        {
            argList.add("-DperformRelease=true");
        }
        
        for(String profileId : getActiveProfileIds(project,session))
        {
            argList.add("-P" + profileId);
        }

        argList.add("-X");
        String additionalArgs = Joiner.on(" ").join(argList);
        
        ReleaseResult result = new ReleaseResult();
        ReleaseEnvironment env = new DefaultReleaseEnvironment();
        env.setSettings(session.getSettings());
        MavenExecutor mavenExecutor = mavenExecutors.get(env.getMavenExecutorId());
        
        String goal = "deploy";
        
        if(ctx.isNoDeploy())
        {
            goal = "install";
        }
        
        mavenExecutor.executeGoals(ctx.getBaseDir(),goal,env,ctx.isInteractive(),additionalArgs,result);

    }

    @Override
    public MavenSession reloadReactor(MavenProject rootProject, MavenSession oldSession) throws ReactorReloadException
    {
        Stack<File> projectFiles = new Stack<File>();
        projectFiles.push(rootProject.getFile());

        List<MavenProject> reactorProjects = new ArrayList<MavenProject>();
        
        try
        {
            while(!projectFiles.isEmpty())
            {
                File file = (File) projectFiles.pop();
    
                //TODO: use getProjectBuilderConfiguration to create builder so we get profiles and junk
                MavenProject project = projectBuilder.build(file, oldSession.getLocalRepository(), new DefaultProfileManager(oldSession.getContainer(),oldSession.getExecutionProperties()));
                
                project.setActiveProfiles(rootProject.getActiveProfiles());
                List<String> moduleNames = project.getModules();
                
                for(String moduleName : moduleNames)
                {
                    projectFiles.push(new File(file.getParentFile(), moduleName + "/pom.xml"));
                }
    
                reactorProjects.add(project);
            }
    
            ReactorManager reactorManager = new ReactorManager(reactorProjects);
            MavenSession newSession = new MavenSession(
                    oldSession.getContainer()
                    ,oldSession.getSettings()
                    ,oldSession.getLocalRepository()
                    ,oldSession.getEventDispatcher()
                    ,reactorManager
                    ,oldSession.getGoals()
                    ,oldSession.getExecutionRootDirectory()
                    ,oldSession.getExecutionProperties()
                    ,oldSession.getUserProperties()
                    ,oldSession.getStartTime()
            );
            
            //in case of maven 3
            try
            {
                Method setProjectsMethod = newSession.getClass().getMethod("setProjects",List.class);
                setProjectsMethod.invoke(newSession,reactorManager.getSortedProjects());
            }
            catch (Exception ignore)
            {
                //ignore
            }
            
            return newSession;
        }
        catch (Exception e)
        {
            throw new ReactorReloadException("Error reloading Maven reacotr projects", e);
        }
        
    }

    private List<String> getActiveProfileIds(MavenProject project, MavenSession session)
    {
        List<String> profiles = new ArrayList<String>();
        try
        {
            // Try to use M3-methods
            Method getRequestMethod = session.getClass().getMethod( "getRequest" );
            Object mavenExecutionRequest = getRequestMethod.invoke( session );
            Method getActiveProfilesMethod = mavenExecutionRequest.getClass().getMethod( "getActiveProfiles" );
            profiles = (List<String>) getActiveProfilesMethod.invoke( mavenExecutionRequest );
        }
        catch ( Exception e )
        {
            //do nothing
        }

        if ( project.getActiveProfiles() != null && !project.getActiveProfiles().isEmpty() )
        {
            for ( Object profile : project.getActiveProfiles() )
            {
                profiles.add( ( (Profile) profile ).getId() );
            }
        }
        return profiles;
    }
}
