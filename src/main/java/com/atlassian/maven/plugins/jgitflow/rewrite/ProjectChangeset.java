package com.atlassian.maven.plugins.jgitflow.rewrite;

import java.util.Collection;
import java.util.Map;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.*;

import static com.google.common.base.Functions.toStringFunction;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Iterables.*;

/**
 * Describes changes that should be applied to the project.
 * <p>
 * This class is immutable; all of its non-getter methods return new instances.
 * <p>
 * This class also contains static factory methods for all supported change types.
 */
public final class ProjectChangeset
{
    private static final ProjectChangeset EMPTY = new ProjectChangeset();

    private final ImmutableList<ProjectChange> changes;

    public static ProjectChangeset changeset()
    {
        return EMPTY;
    }

    public ProjectChangeset()
    {
        this(ImmutableList.<ProjectChange>of());
    }

    private ProjectChangeset(Iterable<ProjectChange> changes)
    {
        this.changes = ImmutableList.copyOf(changes);
    }

    /**
     * Returns all changes in the changeset.
     */
    public Iterable<ProjectChange> getItems()
    {
        return changes;
    }

    /**
     * Returns only the changes of the specified class.
     */
    public <T extends ProjectChange> Iterable<T> getItems(Class<T> itemClass)
    {
        return filter(changes, itemClass);
    }

    /**
     * Returns true if the changeset contains any items of the specified class.
     */
    public boolean hasItems(Class<? extends ProjectChange> itemClass)
    {
        return !isEmpty(getItems(itemClass));
    }

    /**
     * Returns a copy of this changeset with the specified item(s) added.
     */
    public ProjectChangeset with(ProjectChange... newChanges)
    {
        return new ProjectChangeset(concat(changes, ImmutableList.copyOf(newChanges)));
    }

    /**
     * Returns a copy of this changeset with the specified item(s) added.
     */
    public ProjectChangeset with(Iterable<? extends ProjectChange> newChanges)
    {
        return new ProjectChangeset(concat(changes, ImmutableList.copyOf(newChanges)));
    }

    /**
     * Returns a changeset consisting of this changeset plus all items from another changeset.
     */
    public ProjectChangeset with(ProjectChangeset other)
    {
        return new ProjectChangeset(concat(changes, other.changes));
    }

    /**
     * Returns the toString() description of every change in the changeset.
     */
    public Iterable<String> getAllChangeDescriptions()
    {
        return transform(changes, toStringFunction());
    }

    /**
     * Returns the toString() description of every change in the changeset, sorted by type,
     * except for changes that implement {@link SummarizeAsGroup}, which will instead be counted.
     */
    public Iterable<String> getChangeDescriptionsOrSummaries()
    {
        Iterable<String> uniqueDescriptions = ImmutableSet.copyOf(transform(filter(changes, not(summarizable)), toStringFunction()));
        Multimap<String, ProjectChange> summaries = Multimaps.index(changes, summarizableGroupName);
        return concat(Ordering.<String>natural().sortedCopy(uniqueDescriptions),
                filter(transform(summaries.asMap().entrySet(), summaryDescription), Predicates.notNull()));
    }

    @Override
    public String toString()
    {
        return Joiner.on(",\n").join(getAllChangeDescriptions());
    }

    private static Predicate<ProjectChange> summarizable = new Predicate<ProjectChange>()
    {
        public boolean apply(ProjectChange input)
        {
            return input instanceof SummarizeAsGroup;
        }
    };

    private static Function<ProjectChange, String> summarizableGroupName = new Function<ProjectChange, String>()
    {
        public String apply(ProjectChange input)
        {
            return (input instanceof SummarizeAsGroup) ? ((SummarizeAsGroup) input).getGroupName() : "";
        }
    };

    private static Function<Map.Entry<String, Collection<ProjectChange>>, String> summaryDescription =
            new Function<Map.Entry<String, Collection<ProjectChange>>, String>()
            {
                public String apply(Map.Entry<String, Collection<ProjectChange>> input)
                {
                    return (input.getKey().equals("")) ? null : (input.getKey() + ": " + input.getValue().size());
                }
            };
}
