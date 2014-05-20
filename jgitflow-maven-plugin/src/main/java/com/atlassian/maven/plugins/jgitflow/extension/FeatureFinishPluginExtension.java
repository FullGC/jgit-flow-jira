package com.atlassian.maven.plugins.jgitflow.extension;

import com.atlassian.jgitflow.core.extension.FeatureFinishExtension;
import com.atlassian.jgitflow.core.extension.impl.EmptyDevelopMergingExtension;
import com.atlassian.jgitflow.core.extension.impl.EmptyFeatureFinishExtension;

import org.codehaus.plexus.component.annotations.Component;

@Component(role = FeatureFinishPluginExtension.class)
public class FeatureFinishPluginExtension extends EmptyFeatureFinishExtension implements FeatureFinishExtension, InitializingExtension
{

    @Override
    public void init()
    {
        
    }
}
