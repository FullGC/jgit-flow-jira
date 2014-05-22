package com.atlassian.maven.plugins.jgitflow.rewrite;

import java.io.*;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.atlassian.jgitflow.core.CoreEol;
import com.atlassian.jgitflow.core.exception.JGitFlowException;
import com.atlassian.maven.plugins.jgitflow.exception.ProjectRewriteException;
import com.atlassian.maven.plugins.jgitflow.provider.JGitFlowProvider;

import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.release.util.ReleaseUtil;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.WriterFactory;
import org.jdom2.*;
import org.jdom2.filter.ContentFilter;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

/**
 * @since version
 */
@Component(role = ProjectRewriter.class)
public class MavenProjectRewriter implements ProjectRewriter
{
    private static final int POM_INDENTATION = 4;

    @Requirement
    private JGitFlowProvider jGitFlowProvider;

    @Override
    public void applyChanges(MavenProject project, ProjectChangeset changes) throws ProjectRewriteException
    {
        String eol = null;

        try
        {
            eol = CoreEol.getConfigValue(jGitFlowProvider.gitFlow().git().getRepository().getConfig()).getEol();
        }
        catch (JGitFlowException e)
        {
            throw new ProjectRewriteException("Error determining proper EOL!", e);
        }

        File pomFile = project.getFile();

        if (null == pomFile || !pomFile.exists() || !pomFile.canRead())
        {
            String pomPath = (null == pomFile) ? "null" : pomFile.getAbsolutePath();

            throw new ProjectRewriteException("pom file must be readable! " + pomPath);
        }

        //Document document = readPom(pomFile);
        DocumentDescriptor dd = readPom(pomFile, eol);
        Document document = dd.getDocument();
        Element root = document.getRootElement();

        boolean pomWasModified = false;

        pomWasModified |= applyAllChanges(project, root, changes.getItems());

        if (pomWasModified)
        {
            writePom(dd, pomFile, eol);
        }

    }

    private void writePom(DocumentDescriptor dd, File f, String eol) throws ProjectRewriteException
    {
        Writer writer = null;
        String intro = dd.getIntro();
        String outtro = dd.getOuttro();
        Document document = dd.getDocument();

        try
        {
            writer = WriterFactory.newXmlWriter(f);

            if (intro != null)
            {
                writer.write(intro);
            }

            Format format = Format.getRawFormat();
            format.setLineSeparator(eol);
            XMLOutputter out = new XMLOutputter(format);
            out.output(document.getRootElement(), writer);

            if (outtro != null)
            {
                writer.write(outtro);
            }
        }
        catch (IOException e)
        {
            throw new ProjectRewriteException("Error writing pom!", e);
        }
        finally
        {
            IOUtil.close(writer);
        }

    }

    private boolean applyAllChanges(MavenProject project, Element root, Iterable<ProjectChange> items) throws ProjectRewriteException
    {
        boolean modified = false;

        for (ProjectChange change : items)
        {
            boolean result = change.applyChange(project, root);

            if (!modified)
            {
                modified = result;
            }
        }

        return modified;
    }

    private DocumentDescriptor readPom(File pomFile, String eol) throws ProjectRewriteException
    {
        String intro = null;
        String outtro = null;

        try
        {
            String content = ReleaseUtil.readXmlFile(pomFile, eol);
            // we need to eliminate any extra whitespace inside elements, as JDOM will nuke it
            content = content.replaceAll("<([^!][^>]*?)\\s{2,}([^>]*?)>", "<$1 $2>");
            content = content.replaceAll("(\\s{2,}|[^\\s])/>", "$1 />");

            SAXBuilder builder = new SAXBuilder();
            Document document = builder.build(new StringReader(content));

            // Normalize line endings to platform's style (XML processors like JDOM normalize line endings to "\n" as
            // per section 2.11 of the XML spec)
            normaliseLineEndings(document, eol);

            // rewrite DOM as a string to find differences, since text outside the root element is not tracked
            StringWriter w = new StringWriter();
            Format format = Format.getRawFormat();
            format.setLineSeparator(eol);
            XMLOutputter out = new XMLOutputter(format);
            out.output(document.getRootElement(), w);

            int index = content.indexOf(w.toString());
            if (index >= 0)
            {
                intro = content.substring(0, index);
                outtro = content.substring(index + w.toString().length());
            }
            else
            {
                /*
                 * NOTE: Due to whitespace, attribute reordering or entity expansion the above indexOf test can easily
                 * fail. So let's try harder. Maybe some day, when JDOM offers a StaxBuilder and this builder employes
                 * XMLInputFactory2.P_REPORT_PROLOG_WHITESPACE, this whole mess can be avoided.
                 */
                final String SPACE = "\\s++";
                final String XML = "<\\?(?:(?:[^\"'>]++)|(?:\"[^\"]*+\")|(?:'[^\']*+'))*+>";
                final String INTSUB = "\\[(?:(?:[^\"'\\]]++)|(?:\"[^\"]*+\")|(?:'[^\']*+'))*+\\]";
                final String DOCTYPE =
                        "<!DOCTYPE(?:(?:[^\"'\\[>]++)|(?:\"[^\"]*+\")|(?:'[^\']*+')|(?:" + INTSUB + "))*+>";
                final String PI = XML;
                final String COMMENT = "<!--(?:[^-]|(?:-[^-]))*+-->";

                final String INTRO =
                        "(?:(?:" + SPACE + ")|(?:" + XML + ")|(?:" + DOCTYPE + ")|(?:" + COMMENT + ")|(?:" + PI + "))*";
                final String OUTRO = "(?:(?:" + SPACE + ")|(?:" + COMMENT + ")|(?:" + PI + "))*";
                final String POM = "(?s)(" + INTRO + ")(.*?)(" + OUTRO + ")";

                Matcher matcher = Pattern.compile(POM).matcher(content);
                if (matcher.matches())
                {
                    intro = matcher.group(1);
                    outtro = matcher.group(matcher.groupCount());
                }
            }

            return new DocumentDescriptor(document, intro, outtro);
        }
        catch (IOException e)
        {
            throw new ProjectRewriteException("unable to read pom!", e);
        }
        catch (JDOMException e)
        {
            throw new ProjectRewriteException("unable to read pom!", e);
        }

    }

    private void normaliseLineEndings(Document document, String eol)
    {
        for (Iterator<?> i = document.getDescendants(new ContentFilter(ContentFilter.COMMENT)); i.hasNext(); )
        {
            Comment c = (Comment) i.next();
            c.setText(ReleaseUtil.normalizeLineEndings(c.getText(), eol));
        }
        for (Iterator<?> i = document.getDescendants(new ContentFilter(ContentFilter.CDATA)); i.hasNext(); )
        {
            CDATA c = (CDATA) i.next();
            c.setText(ReleaseUtil.normalizeLineEndings(c.getText(), eol));
        }
    }

    private class DocumentDescriptor
    {
        private final Document document;
        private final String intro;
        private final String outtro;

        private DocumentDescriptor(Document document, String intro, String outtro)
        {
            this.document = document;
            this.intro = intro;
            this.outtro = outtro;
        }

        public Document getDocument()
        {
            return document;
        }

        public String getIntro()
        {
            return intro;
        }

        public String getOuttro()
        {
            return outtro;
        }
    }
}
