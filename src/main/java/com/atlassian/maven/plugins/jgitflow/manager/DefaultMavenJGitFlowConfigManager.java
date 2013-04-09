package com.atlassian.maven.plugins.jgitflow.manager;

import java.io.File;
import java.io.IOException;

import com.atlassian.maven.plugins.jgitflow.MavenJGitFlowConfiguration;

import com.google.common.base.Strings;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.codehaus.plexus.util.FileUtils;
import org.eclipse.jgit.api.Git;

/**
 * @since version
 */
public class DefaultMavenJGitFlowConfigManager implements MavenJGitFlowConfigManager
{
    private MavenJGitFlowConfiguration config;
    
    @Override
    public MavenJGitFlowConfiguration getConfiguration(Git git) throws IOException
    {
        if(null == config)
        {
            File configFile = new File(git.getRepository().getDirectory(),CONFIG_FILENAME);
            this.config = loadConfiguration(configFile);
        }
        
        return config;
    }

    @Override
    public void saveConfiguration(MavenJGitFlowConfiguration newConfig, Git git) throws IOException
    {
        File configFile = new File(git.getRepository().getDirectory(),CONFIG_FILENAME);
        if(!configFile.exists())
        {
            Files.write("{}".getBytes(),configFile);
        }
        
        Gson gson = createGson();
        String json = gson.toJson(newConfig);
        Files.write(json.getBytes(),configFile);
        
        this.config = newConfig;
    }


    private Gson createGson()
    {
        GsonBuilder gsonBuilder = new GsonBuilder();
        Gson gson = gsonBuilder.create();

        return gson;
    }

    private MavenJGitFlowConfiguration loadConfiguration(File configFile)
    {
        MavenJGitFlowConfiguration loadedConfig = null;
        try
        {
            if (!configFile.exists())
            {
                Files.write("{}".getBytes(),configFile);
            }

            String configJson = FileUtils.fileRead(configFile);

            if (Strings.isNullOrEmpty(configJson))
            {
                Files.write("{}".getBytes(), configFile);
                configJson = "{}";
            }

            Gson gson = createGson();

            loadedConfig = gson.fromJson(configJson, MavenJGitFlowConfiguration.class);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            loadedConfig = null;
        }

        return loadedConfig;
    }

}
