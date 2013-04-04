package com.atlassian.maven.plugins.jgitflow.rewrite;

import java.util.List;

import com.google.common.base.Predicate;

import org.jdom2.Element;

import static com.google.common.collect.Lists.newArrayList;

/**
 * @since version
 */
public class ProjectChangeUtils
{
    public static boolean definesProperty(String propName, Element root)
    {
        Element properties = getElementOrNull(root, "properties");
        if(null != properties)
        {
            return null != getElementOrNull(properties,propName);
        }

        return false;
    }

    public static Element getOrCreateElement(Element container, String path)
    {
        Element last = container;
        for (String pathName : path.split("/"))
        {
            last = container.getChild(pathName, container.getNamespace());
            if (last == null)
            {
                last = new Element(pathName);
                container.addContent("    ").addContent(last).addContent("\n  ");
            }
            container = last;
        }
        return last;
    }

    public static Element getElementOrNull(Element container, String path)
    {
        for (String pathName : path.split("/"))
        {
            if (container != null)
            {
                container = container.getChild(pathName, container.getNamespace());
            }
        }
        return container;
    }

    public static List<Element> getElementListOrEmpty(Element container, String path)
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
                    container = container.getChild(pathName, container.getNamespace());
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
                    List<Element> el = container.getChildren(pathName,container.getNamespace());
                    if(null != el)
                    {
                        elements = el;
                    }
                }
            }
        }
        
        return elements;
    }

    public static Predicate<? super Element> childElementValue(final String name, final String value)
    {
        return new Predicate<Element>()
        {
            public boolean apply(Element input)
            {
                Element child = input.getChild(name, input.getNamespace());
                return (child == null) ? value.equals("") : value.equals(child.getText());
            }
        };
    }
}
