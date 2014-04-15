package com.atlassian.maven.plugins.jgitflow.rewrite;

import java.io.*;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.atlassian.maven.plugins.jgitflow.exception.ProjectRewriteException;

import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.release.util.ReleaseUtil;
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
public class MavenProjectRewriter implements ProjectRewriter
{
    private static final int POM_INDENTATION = 4;
    private static final String ls = System.getProperty("line.separator");
    
    @Override
    public void applyChanges(MavenProject project, ProjectChangeset changes) throws ProjectRewriteException
    {
        File pomFile = project.getFile();
        
        if(null == pomFile || !pomFile.exists() || !pomFile.canRead())
        {
            String pomPath = (null == pomFile) ? "null" : pomFile.getAbsolutePath();
            
            throw new ProjectRewriteException("pom file must be readable! " + pomPath);
        }
        
        //Document document = readPom(pomFile);
        DocumentDescriptor dd = readPom(pomFile);
        Document document = dd.getDocument();
        Element root = document.getRootElement();
        
        boolean pomWasModified = false;
        
        pomWasModified |= applyAllChanges(project, root, changes.getItems());
        
        if(pomWasModified)
        {
            writePom(dd,pomFile);
        }
        
    }

    private void writePom(DocumentDescriptor dd, File f) throws ProjectRewriteException
    {
        Writer writer = null;
        String intro = dd.getIntro();
        String outtro = dd.getOuttro();
        Document document = dd.getDocument();
        
        try
        {
            writer = WriterFactory.newXmlWriter(f);

            if ( intro != null )
            {
                writer.write( intro );
            }

            Format format = Format.getRawFormat();
            format.setLineSeparator( ls );
            XMLOutputter out = new XMLOutputter( format );
            out.output( document.getRootElement(), writer );

            if ( outtro != null )
            {
                writer.write( outtro );
            }
        }
        catch (IOException e)
        {
            throw new ProjectRewriteException("Error writing pom!", e);
        }
        finally
        {
            IOUtil.close( writer );
        }

    }
    
    /*
    private void writePom(Document doc, File f) throws ProjectRewriteException
    {
        FileOutputStream fos = null;
        
        try
        {
            fos = new FileOutputStream(f);

            Format format = Format.getRawFormat();
            XMLOutputter out = new XMLOutputter(format);
            
            out.output(doc,fos);
        }
        
        catch (IOException e)
        {
            throw new ProjectRewriteException("Error writing pom!", e);
        }
        finally
        {
            IOUtil.close(fos);
        }
    }
    */

    private boolean applyAllChanges(MavenProject project, Element root, Iterable<ProjectChange> items) throws ProjectRewriteException
    {
        boolean modified = false;
        
        for(ProjectChange change : items)
        {
            boolean result = change.applyChange(project,root);
            
            if(!modified)
            {
                modified = result;
            }
        }
        
        return modified;
    }

    private DocumentDescriptor readPom(File pomFile) throws ProjectRewriteException
    {
        String intro = null;
        String outtro = null;
        
        try
        {
            String content = ReleaseUtil.readXmlFile(pomFile, ls);
            // we need to eliminate any extra whitespace inside elements, as JDOM will nuke it
            content = content.replaceAll( "<([^!][^>]*?)\\s{2,}([^>]*?)>", "<$1 $2>" );
            content = content.replaceAll( "(\\s{2,}|[^\\s])/>", "$1 />" );

            SAXBuilder builder = new SAXBuilder();
            Document document = builder.build( new StringReader( content ) );

            // Normalize line endings to platform's style (XML processors like JDOM normalize line endings to "\n" as
            // per section 2.11 of the XML spec)
            normaliseLineEndings( document );

            // rewrite DOM as a string to find differences, since text outside the root element is not tracked
            StringWriter w = new StringWriter();
            Format format = Format.getRawFormat();
            format.setLineSeparator( ls );
            XMLOutputter out = new XMLOutputter( format );
            out.output( document.getRootElement(), w );

            int index = content.indexOf( w.toString() );
            if ( index >= 0 )
            {
                intro = content.substring( 0, index );
                outtro = content.substring( index + w.toString().length() );
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

                Matcher matcher = Pattern.compile(POM).matcher( content );
                if ( matcher.matches() )
                {
                    intro = matcher.group( 1 );
                    outtro = matcher.group( matcher.groupCount() );
                }
            }
            
            return new DocumentDescriptor(document,intro,outtro);
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

    private void normaliseLineEndings( Document document )
    {
        for ( Iterator<?> i = document.getDescendants( new ContentFilter( ContentFilter.COMMENT ) ); i.hasNext(); )
        {
            Comment c = (Comment) i.next();
            c.setText( ReleaseUtil.normalizeLineEndings( c.getText(), ls ) );
        }
        for ( Iterator<?> i = document.getDescendants( new ContentFilter( ContentFilter.CDATA ) ); i.hasNext(); )
        {
            CDATA c = (CDATA) i.next();
            c.setText( ReleaseUtil.normalizeLineEndings( c.getText(), ls ) );
        }
    }
    
    /*
    private Document readPom(File pomFile) throws ProjectRewriteException
    {
        FileInputStream fis = null;
        try 
        {
            fis = new FileInputStream(pomFile);
            final StAXStreamBuilder builder = new StAXStreamBuilder();
            XMLInputFactory inputFactory = XMLInputFactory.newInstance();
            XMLStreamReader streamReader = inputFactory.createXMLStreamReader(fis);
            
            return builder.build(streamReader);
        }
        catch (XMLStreamException e)
        {
            throw new ProjectRewriteException("unable to read pom!", e);
        }
        catch (JDOMException e)
        {
            throw new ProjectRewriteException("unable to read pom!", e);
        }
        catch (IOException e)
        {
            throw new ProjectRewriteException("unable to read pom!", e);
        }
        finally
        {
            if(null != fis)
            {
                IOUtil.close(fis);
            }
        }
    }
    */

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
