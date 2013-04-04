package com.atlassian.maven.plugins.jgitflow.rewrite;

/**
 * Interface for {@link ProjectChange} objects that should not be logged individually.
 */
public interface SummarizeAsGroup
{
    String getGroupName();
}
