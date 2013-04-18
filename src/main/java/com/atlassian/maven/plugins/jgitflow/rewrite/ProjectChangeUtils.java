package com.atlassian.maven.plugins.jgitflow.rewrite;

import java.util.List;

import com.google.common.base.Predicate;

import org.jdom2.Element;
import org.jdom2.Namespace;

import static com.google.common.collect.Lists.newArrayList;

/**
 * @since version
 */
public class ProjectChangeUtils
{
    public static boolean definesProperty(String propName, Element root, Namespace ns)
    {
        Element properties = getElementOrNull(root, "properties", ns);
        if(null != properties)
        {
            return null != getElementOrNull(properties,propName,ns);
        }

        return false;
    }

    public static Element getOrCreateElement(Element container, String path, Namespace ns)
    {
        Element last = container;
        for (String pathName : path.split("/"))
        {
            last = container.getChild(pathName, ns);
            if (last == null)
            {
                last = new Element(pathName,ns);
                container.addContent("    ").addContent(last).addContent("\n  ");
            }
            container = last;
        }
        return last;
    }

    public static Element getElementOrNull(Element container, String path, Namespace ns)
    {
        for (String pathName : path.split("/"))
        {
            if (container != null)
            {
                container = container.getChild(pathName, ns);
            }
        }
        return container;
    }

    public static Namespace getNamespaceOrNull(Element container)
    {
        Namespace ns = container.getNamespace();
        if(ns.equals(Namespace.NO_NAMESPACE))
        {
            return null;
        }
        
        return ns;
    }

    public static List<Element> getElementListOrEmpty(Element container, String path, Namespace ns)
    {
        if(null == container)
        {
            return newArrayList();
        }
        
        List<Element> elements = newArrayList();
        
        String[] paths = path.split("/");
        int lastIndex = paths.length - 1;
        
        for(int i=0; i<paths.length; i++)
        {
            String pathName = paths[i];
            
            if(i != lastIndex)
            {
                if(null != container)
                {
                    container = container.getChild(pathName, ns);
                }
                else
                {
                    break;
                }
            }
            else
            {
                if(null != container)
                {
                    List<Element> el = container.getChildren(pathName,ns);
                    if(null != el)
                    {
                        elements = el;
                    }
                }
            }
        }
        
        return elements;
    }

    public static Predicate<? super Element> childElementValue(final String name, final String value, final Namespace ns)
    {
        return new Predicate<Element>()
        {
            public boolean apply(Element input)
            {
                Element child = input.getChild(name, ns);
                return (child == null) ? value.equals("") : value.equals(child.getText());
            }
        };
    }
}
