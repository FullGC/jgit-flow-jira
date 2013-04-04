package com.atlassian.maven.plugins.jgitflow.rewrite;

import java.util.Map;

import com.atlassian.maven.plugins.jgitflow.exception.ProjectRewriteException;

import com.google.common.base.Strings;

import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.project.MavenProject;
import org.jdom2.Element;

/**
 * @since version
 */
public class ProjectReleaseVersionChange implements ProjectChange
{
    private final Map<String, String> releaseVersions;

    private ProjectReleaseVersionChange(Map<String, String> releaseVersions)
    {
        this.releaseVersions = releaseVersions;
    }
    
    public static ProjectReleaseVersionChange projectReleaseVersionChange(Map<String, String> releaseVersions)
    {
        return new ProjectReleaseVersionChange(releaseVersions);
    }

    @Override
    public boolean applyChange(MavenProject project, Element root) throws ProjectRewriteException
    {
        boolean modified = false;

        Element versionElement = root.getChild("version", root.getNamespace());
        String projectId = ArtifactUtils.versionlessKey(project.getGroupId(), project.getArtifactId());
        String releaseVersion = releaseVersions.get(projectId);
        
        if(Strings.isNullOrEmpty(releaseVersion))
        {
            throw new ProjectRewriteException("Release version for " + project.getName() + " was not found");
        }
        
        if(null == versionElement)
        {
            String parentVersion = null;
            if(project.hasParent())
            {
                MavenProject parent = project.getParent();
                String parentId = ArtifactUtils.versionlessKey(parent.getGroupId(), parent.getArtifactId());

                parentVersion = releaseVersions.get(parentId);
            }
            
            if(!releaseVersion.equals(parentVersion))
            {
                Element artifactId = root.getChild("artifactId", root.getNamespace());
                versionElement = new Element("version");

                versionElement.setText(releaseVersion);
                root.getChildren().add(root.indexOf(artifactId) + 1, versionElement);
                modified = true;
            }
        }
        else
        {
            versionElement.setText(releaseVersion);
            modified = true;
        }
        
        return modified;
    }

    @Override
    public String toString()
    {
        return "[Update Project Release Version]";
    }
}
