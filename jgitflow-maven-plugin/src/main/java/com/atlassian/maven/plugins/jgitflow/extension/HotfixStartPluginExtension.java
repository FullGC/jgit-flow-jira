package com.atlassian.maven.plugins.jgitflow.extension;

import com.atlassian.jgitflow.core.extension.HotfixStartExtension;

import org.codehaus.plexus.component.annotations.Component;

@Component(role = HotfixStartPluginExtension.class)
public class HotfixStartPluginExtension extends ProductionBranchCreatingPluginExtension implements HotfixStartExtension
{

}
