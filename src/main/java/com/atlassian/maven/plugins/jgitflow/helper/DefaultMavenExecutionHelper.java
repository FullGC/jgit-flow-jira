package com.atlassian.maven.plugins.jgitflow.helper;

import java.lang.reflect.Method;
import java.util.*;

import com.atlassian.maven.plugins.jgitflow.ReleaseContext;

import com.google.common.base.Joiner;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Profile;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.release.ReleaseResult;
import org.apache.maven.shared.release.env.DefaultReleaseEnvironment;
import org.apache.maven.shared.release.env.ReleaseEnvironment;
import org.apache.maven.shared.release.exec.MavenExecutor;
import org.apache.maven.shared.release.exec.MavenExecutorException;

/**
 * @since version
 */
public class DefaultMavenExecutionHelper implements MavenExecutionHelper
{
    @Component
    protected Map<String, MavenExecutor> mavenExecutors;
    
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
        
        String additionalArgs = Joiner.on(" ").join(argList);
        
        ReleaseResult result = new ReleaseResult();
        ReleaseEnvironment env = new DefaultReleaseEnvironment();
        MavenExecutor mavenExecutor = mavenExecutors.get(env.getMavenExecutorId());
        
        String goal = "deploy";
        
        if(ctx.isNoDeploy())
        {
            goal = "install";
        }
        
        mavenExecutor.executeGoals(ctx.getBaseDir(),goal,env,ctx.isInteractive(),additionalArgs,result);

    }

    private List<String> getActiveProfileIds(MavenProject project, MavenSession session)
    {
        List<String> profiles;
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
            if ( project.getActiveProfiles() == null || project.getActiveProfiles().isEmpty() )
            {
                profiles = Collections.emptyList();
            }
            else
            {
                profiles = new ArrayList<String>( project.getActiveProfiles().size() );
                for ( Object profile : project.getActiveProfiles() )
                {
                    profiles.add( ( (Profile) profile ).getId() );
                }
            }
        }
        return profiles;
    }
}
