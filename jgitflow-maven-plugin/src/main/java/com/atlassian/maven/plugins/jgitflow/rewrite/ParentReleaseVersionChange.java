package com.atlassian.maven.plugins.jgitflow.rewrite;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.atlassian.maven.plugins.jgitflow.exception.ProjectRewriteException;

import com.google.common.base.Joiner;

import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.project.MavenProject;
import org.jdom2.Element;
import org.jdom2.Namespace;

import static com.atlassian.maven.plugins.jgitflow.rewrite.ProjectChangeUtils.getNamespaceOrNull;

/**
 * @since version
 */
public class ParentReleaseVersionChange implements ProjectChange
{
    private final Map<String, String> originalVersions;
    private final Map<String, String> releaseVersions;
    private final List<String> workLog;

    private ParentReleaseVersionChange(Map<String, String> originalVersions, Map<String, String> releaseVersions)
    {
        this.originalVersions = originalVersions;
        this.releaseVersions = releaseVersions;
        this.workLog = new ArrayList<String>();
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
            Namespace ns = getNamespaceOrNull(root);
            Element parentVersionElement = root.getChild("parent", ns).getChild("version", ns);
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
                workLog.add("setting parent version to '" + parentVersion + "'");
                parentVersionElement.setText(parentVersion);
                modified = true;
            }
        }

        return modified;
    }

    @Override
    public String toString()
    {
        if(workLog.isEmpty())
        {
            return "[Update Parent Release Version]";
        }
        else
        {
            return "[Update Parent Release Version]\n - " + Joiner.on("\n - ").join(workLog);
        }
    }
}
