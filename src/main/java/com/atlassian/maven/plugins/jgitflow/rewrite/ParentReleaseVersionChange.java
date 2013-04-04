package com.atlassian.maven.plugins.jgitflow.rewrite;

import java.util.Map;

import com.atlassian.maven.plugins.jgitflow.exception.ProjectRewriteException;

import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.project.MavenProject;
import org.jdom2.Element;

/**
 * @since version
 */
public class ParentReleaseVersionChange implements ProjectChange
{
    private final Map<String, String> originalVersions;
    private final Map<String, String> releaseVersions;

    private ParentReleaseVersionChange(Map<String, String> originalVersions, Map<String, String> releaseVersions)
    {
        this.originalVersions = originalVersions;
        this.releaseVersions = releaseVersions;
    }

    public static ParentReleaseVersionChange parentReleaseVersionChange(Map<String, String> originalVersions, Map<String, String> releaseVersions)
    {
        return new ParentReleaseVersionChange(originalVersions, releaseVersions);
    }

    @Override
    public boolean applyChange(MavenProject project, Element root) throws ProjectRewriteException
    {
        boolean modified = false;

        if (project.hasParent())
        {
            Element parentVersionElement = root.getChild("parent", root.getNamespace()).getChild("version", root.getNamespace());
            MavenProject parent = project.getParent();
            String parentId = ArtifactUtils.versionlessKey(parent.getGroupId(), parent.getArtifactId());

            String parentVersion = releaseVersions.get(parentId);

            if (null == parentVersion)
            {
                if (parent.getVersion().equals(originalVersions.get(parentId)))
                {
                    throw new ProjectRewriteException("Release version for parent " + parent.getName() + " was not found");
                }
            }
            else
            {
                parentVersionElement.setText(parentVersion);
                modified = true;
            }
        }

        return modified;
    }

    @Override
    public String toString()
    {
        return "[Update Parent Release Version]";
    }
}
