package com.atlassian.maven.plugins.jgitflow.util;

import com.google.common.base.CaseFormat;

import org.apache.commons.lang.StringUtils;

/**
 * @since version
 */
public class NamingUtil
{
    public static String camelCaseOrSpaceToDashed(String s)
    {
//        String dashed = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN,s);
//        String trimmed = StringUtils.replace(StringUtils.replace(StringUtils.replace(dashed, " -", "-"), "- ", "-"), " ", "-");
//
//        return trimmed.toLowerCase();

        String trimmed = s.replaceAll("[\\s]", "");

        String dashed = trimmed.replaceAll(
                String.format("%s|%s|%s",
                        "(?<=[A-Z])(?=[A-Z][a-z])",
                        "(?<=[^A-Z])(?=[A-Z])",
                        "(?<=[A-Za-z])(?=[^A-Za-z])"
                ),
                "-"
        );

        dashed = dashed.replaceAll("--","-");
        
        return dashed.toLowerCase();
    }
}
