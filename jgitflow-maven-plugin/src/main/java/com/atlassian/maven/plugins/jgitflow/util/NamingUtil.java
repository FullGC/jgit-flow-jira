package com.atlassian.maven.plugins.jgitflow.util;

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

        dashed = dashed.replaceAll("--", "-");

        return dashed.toLowerCase();
    }

    public static String unprefixedBranchName(String prefix, String branchName)
    {
        return StringUtils.substringAfter(branchName, prefix);
    }
    
    public static String afterLastNewline(String str)
    {
        String[] lines = str.split("\\r\\n|\\r|\\n");
        return lines[lines.length - 1];
    }
}
