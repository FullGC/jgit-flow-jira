package com.atlassian.maven.plugins.jgitflow.extension;

import com.atlassian.jgitflow.core.extension.ReleaseFinishExtension;

import org.codehaus.plexus.component.annotations.Component;

@Component(role = ReleaseFinishPluginExtension.class)
public class ReleaseFinishPluginExtension extends ProductionBranchMergingPluginExtension implements ReleaseFinishExtension
{

}
