package com.atlassian.maven.plugins.jgitflow.manager.tasks;

import com.atlassian.maven.plugins.jgitflow.ReleaseContext;
import com.atlassian.maven.plugins.jgitflow.exception.MavenJGitFlowException;
import com.atlassian.maven.plugins.jgitflow.helper.JGitFlowSetupHelper;
import com.atlassian.maven.plugins.jgitflow.provider.ContextProvider;
import com.atlassian.maven.plugins.jgitflow.provider.JGitFlowProvider;

import com.google.common.base.Strings;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;

@Component(role = SetupOriginAndFetchIfNeeded.class)
public class SetupOriginAndFetchIfNeeded
{
    @Requirement
    private ContextProvider contextProvider;

    @Requirement
    private JGitFlowSetupHelper setupHelper;

    @Requirement
    private JGitFlowProvider jGitFlowProvider;
    
    public void run() throws MavenJGitFlowException
    {
        ReleaseContext ctx = contextProvider.getContext();

        if (ctx.isRemoteAllowed())
        {
            try
            {
                setupHelper.ensureOrigin();

                String originUrl = jGitFlowProvider.gitFlow().git().getRepository().getConfig().getString(ConfigConstants.CONFIG_REMOTE_SECTION, Constants.DEFAULT_REMOTE_NAME, "url");
                
                if(!Strings.isNullOrEmpty(originUrl))
                {
                    jGitFlowProvider.gitFlow().git().fetch().setRemote(Constants.DEFAULT_REMOTE_NAME).call();
                }
            }
            catch (Exception e)
            {
                throw new MavenJGitFlowException("Error setting origin in configuration", e);
            }
        }
    }
}
