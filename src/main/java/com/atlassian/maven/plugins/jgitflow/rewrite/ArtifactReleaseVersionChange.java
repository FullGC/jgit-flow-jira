package com.atlassian.maven.plugins.jgitflow.rewrite;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.atlassian.maven.plugins.jgitflow.exception.ProjectRewriteException;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.release.ReleaseExecutionException;
import org.apache.maven.shared.release.util.ReleaseUtil;
import org.jdom2.Element;
import org.jdom2.Namespace;

import static com.atlassian.maven.plugins.jgitflow.rewrite.ProjectChangeUtils.getElementListOrEmpty;
import static com.atlassian.maven.plugins.jgitflow.rewrite.ProjectChangeUtils.getNamespaceOrNull;

/**
 * @since version
 */
public class ArtifactReleaseVersionChange implements ProjectChange
{
    private static final String LF = System.getProperty("line.separator") + "  - ";
    private final Map<String, String> originalVersions;
    private final Map<String, String> releaseVersions;
    private final boolean updateDependencies;
    private StringBuilder workLog;

    private ArtifactReleaseVersionChange(Map<String, String> originalVersions, Map<String, String> releaseVersions, boolean updateDependencies)
    {
        this.originalVersions = originalVersions;
        this.releaseVersions = releaseVersions;
        this.updateDependencies = updateDependencies;
        this.workLog = new StringBuilder("[Update Artifact Versions]");
    }

    public static ArtifactReleaseVersionChange artifactReleaseVersionChange(Map<String, String> originalVersions, Map<String, String> releaseVersions, boolean updateDependencies)
    {
        return new ArtifactReleaseVersionChange(originalVersions,releaseVersions,updateDependencies);    
    }
    
    @Override
    public boolean applyChange(MavenProject project, Element root) throws ProjectRewriteException
    {
        boolean modified = false;

        Namespace ns = getNamespaceOrNull(root);
        Element properties = root.getChild("properties", ns);
        List<Element> artifactContainers = new ArrayList<Element>();
        artifactContainers.add(root);

        artifactContainers.addAll(getElementListOrEmpty(root, "profiles/profile",ns));
        
        for(Element artifactContainer : artifactContainers)
        {
            modified |= rewriteDependencies(artifactContainer, project, properties, ns);
            modified |= rewriteDependencyManagement(artifactContainer, project, properties, ns);
            modified |= rewriteBuildExtensions(artifactContainer, project, properties, ns);
            modified |= rewritePluginElements(artifactContainer, project, properties, ns);
            modified |= rewriteReportingPlugins(artifactContainer, project, properties, ns);
        }
        
        return modified;  //To change body of implemented methods use File | Settings | File Templates.
    }

    private boolean rewriteDependencies(Element artifactContainer, MavenProject project, Element properties, Namespace ns) throws ProjectRewriteException
    {
        List<Element> artifacts = getElementListOrEmpty(artifactContainer, "dependencies/dependency",ns);
        return rewriteArtifactVersions(artifacts, project, properties, ns);
    }
    
    private boolean rewriteDependencyManagement(Element artifactContainer, MavenProject project, Element properties, Namespace ns) throws ProjectRewriteException
    {
        List<Element> artifacts = getElementListOrEmpty(artifactContainer, "dependencyManagement/dependencies/dependency",ns);
        return rewriteArtifactVersions(artifacts, project, properties, ns);
    }

    private boolean rewriteBuildExtensions(Element artifactContainer, MavenProject project, Element properties, Namespace ns) throws ProjectRewriteException
    {
        List<Element> artifacts = getElementListOrEmpty(artifactContainer, "build/extensions/extension",ns);
        return rewriteArtifactVersions(artifacts, project, properties, ns);
    }

    private boolean rewritePluginElements(Element artifactContainer, MavenProject project, Element properties, Namespace ns) throws ProjectRewriteException
    {
        boolean modified = false;
        
        List<Element> pluginElements = new ArrayList<Element>();
        pluginElements.addAll(getElementListOrEmpty(artifactContainer, "build/plugins/plugin",ns));
        pluginElements.addAll(getElementListOrEmpty(artifactContainer, "build/pluginManagement/plugins/plugin",ns));

        modified |= rewriteArtifactVersions(pluginElements, project, properties, ns);
        
        for(Element pluginElement : pluginElements)
        {
            List<Element> artifacts = getElementListOrEmpty(pluginElement, "dependencies/dependency",ns);
            
            modified |=  rewriteArtifactVersions(artifacts, project, properties,ns);
        }
        
        return modified;
    }

    private boolean rewriteReportingPlugins(Element artifactContainer, MavenProject project, Element properties, Namespace ns) throws ProjectRewriteException
    {
        List<Element> artifacts = getElementListOrEmpty(artifactContainer, "reporting/plugins/plugin",ns);
        return rewriteArtifactVersions(artifacts, project, properties, ns);
    }

    private boolean rewriteArtifactVersions(List<Element> artifacts, MavenProject project, Element properties, Namespace ns) throws ProjectRewriteException
    {
        boolean modified = false;
        Model projectModel = project.getModel();
        
        if(null == artifacts || artifacts.isEmpty())
        {
            return false;
        }
        
        String projectId = ArtifactUtils.versionlessKey(project.getGroupId(),project.getArtifactId());
        
        for(Element artifact : artifacts)
        {
            Element versionElement = artifact.getChild("version", ns);
            
            if(null == versionElement)
            {
                continue;
            }
            
            String rawVersion = versionElement.getTextTrim();
            Element groupIdElement = artifact.getChild("groupId", ns);
            
            if(null == groupIdElement)
            {
                if("plugin".equals(artifact.getName()))
                {
                    groupIdElement = new Element("groupId");
                    groupIdElement.setText("org.apache.maven.plugins");
                }
                else
                {
                    continue;
                }
            }
            
            String groupId = null;
            String artifactId = null;
            try
            {
                groupId = ReleaseUtil.interpolate(groupIdElement.getTextTrim(),projectModel);

                Element artifactIdElement = artifact.getChild("artifactId", ns);
                if(null == artifactIdElement)
                {
                    continue;
                }
                
                artifactId = ReleaseUtil.interpolate(artifactIdElement.getTextTrim(),projectModel);
            }
            catch (ReleaseExecutionException e)
            {
                throw new ProjectRewriteException("error interpolating pom variable: " + e.getMessage(),e);
            }

            String artifactKey = ArtifactUtils.versionlessKey(groupId,artifactId);
            String mappedVersion = releaseVersions.get(artifactKey);
            String originalVersion = originalVersions.get(artifactKey);
            
            if(null != mappedVersion 
                    && mappedVersion.endsWith(Artifact.SNAPSHOT_VERSION)
                    && !rawVersion.endsWith(Artifact.SNAPSHOT_VERSION)
                    && !updateDependencies)
            {
                continue;
            }
            
            if(null != mappedVersion)
            {
                if(rawVersion.equals(originalVersion))
                {
                    workLog.append(LF).append("updating ").append(artifactId).append(" to ").append(mappedVersion);
                    versionElement.setText(mappedVersion);
                    modified = true;
                }
                else if(rawVersion.matches("\\$\\{.+\\}"))
                {
                    String propName = rawVersion.substring(2,rawVersion.length() - 1);
                    
                    if(propName.startsWith("project.")
                            || propName.startsWith("pom.")
                            || "version".equals(propName)
                            )
                    {
                        if(!mappedVersion.equals(releaseVersions.get(projectId)))
                        {
                            workLog.append(LF).append("updating ").append(artifactId).append(" to ").append(mappedVersion);
                            versionElement.setText(mappedVersion);
                            modified = true;
                        }
                    }
                    else if(null != properties)
                    {
                        Element prop = properties.getChild(propName, ns);
                        if(null != prop)
                        {
                            String propValue = prop.getTextTrim();
                            if(propValue.equals(originalVersion))
                            {
                                workLog.append(LF).append("updating ").append(rawVersion).append(" to ").append(mappedVersion);
                                prop.setText(mappedVersion);
                                modified = true;
                            }
                            else if(!mappedVersion.equals(rawVersion))
                            {
                                if(!mappedVersion.matches("\\$\\{project.+\\}")
                                        && !mappedVersion.matches("\\$\\{pom.+\\}")
                                        && !"${version}".equals(mappedVersion))
                                {
                                    throw new ProjectRewriteException("The artifact (" + artifactKey + ") requires a "
                                            + "different version (" + mappedVersion + ") than what is found ("
                                            + propValue + ") for the expression (" + propName + ") in the "
                                            + "project (" + projectId + ").");
                                }
                            }
                        }
                        else
                        {
                            throw new ProjectRewriteException("Error updating version '" + rawVersion + "' for artifact " + artifactKey);
                        }
                    }
                }
            }
        }
        
        return modified;
    }

    @Override
    public String toString()
    {
        return workLog.toString();
    }
}
