package com.atlassian.maven.plugins.jgitflow.rewrite;

import java.util.Map;

import com.atlassian.maven.plugins.jgitflow.exception.ProjectRewriteException;

import com.google.common.base.Strings;

import org.apache.maven.artifact.ArtifactUtils;
import org.apache.maven.model.Scm;
import org.apache.maven.project.MavenProject;
import org.apache.maven.scm.provider.ScmUrlUtils;
import org.jdom2.Element;

import static com.atlassian.maven.plugins.jgitflow.rewrite.ProjectChangeUtils.getOrCreateElement;

/**
 * @since version
 */
public class ScmDefaultTagChange implements ProjectChange
{
    private final Map<String, String> releaseVersions;

    private ScmDefaultTagChange(Map<String, String> releaseVersions)
    {
        this.releaseVersions = releaseVersions;
    }
    
    public static ScmDefaultTagChange scmDefaultTagChange(Map<String, String> releaseVersions)
    {
        return new ScmDefaultTagChange(releaseVersions);
    }

    @Override
    public boolean applyChange(MavenProject project, Element root) throws ProjectRewriteException
    {
        boolean modified = false;
        
        Scm scm = project.getScm();
        if(null != scm)
        {
            Element scmElement = root.getChild("scm", root.getNamespace());
            
            if(null != scmElement)
            {
                String scmUrl = (null != scm.getDeveloperConnection()) ? scm.getDeveloperConnection() : scm.getConnection();
                
                if(!Strings.isNullOrEmpty(scmUrl) && "git".equals(ScmUrlUtils.getProvider(scmUrl)))
                {
                    String projectId = ArtifactUtils.versionlessKey(project.getGroupId(), project.getArtifactId());
                    String releaseVersion = releaseVersions.get(projectId);

                    if(Strings.isNullOrEmpty(releaseVersion))
                    {
                        throw new ProjectRewriteException("Release version for " + project.getName() + " was not found");
                    }
                    
                    Element tag = getOrCreateElement(scmElement,"tag");
                    tag.setText(releaseVersion);
                    modified = true;
                }
            }
        }
        
        return modified;
    }

}
