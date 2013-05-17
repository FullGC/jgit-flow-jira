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
        String dashed = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_HYPHEN,s);
        String trimmed = StringUtils.replace(StringUtils.replace(StringUtils.replace(dashed, " -", "-"), "- ", "-"), " ", "-");

        return trimmed.toLowerCase();
    }
}
