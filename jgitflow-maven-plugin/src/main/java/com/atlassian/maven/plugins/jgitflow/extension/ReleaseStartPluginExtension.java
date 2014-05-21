package com.atlassian.maven.plugins.jgitflow.extension;

import com.atlassian.jgitflow.core.extension.ReleaseStartExtension;

import org.codehaus.plexus.component.annotations.Component;

@Component(role = ReleaseStartPluginExtension.class)
public class ReleaseStartPluginExtension extends ProductionBranchCreatingPluginExtension implements ReleaseStartExtension
{
    
}
