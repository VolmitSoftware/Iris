package com.volmit.generation;

import java.util.HashSet;
import java.util.Set;
import com.volmit.resource.ResourceId;

/** Immutable ordering declarations referencing modifier IDs, not feature IDs.
 * Requirements must exist and finish first. Optional constraints apply only when present.
 * Nothing here activates a feature. The host resolves and validates the graph at pack load.
 */
public record Dependencies(Set<String> required, Set<String> after, Set<String> before) {
    public Dependencies {
        required = validatedCopy(required);
        after = validatedCopy(after);
        before = validatedCopy(before);
    }

    public static Dependencies none() {
        return new Dependencies(Set.of(), Set.of(), Set.of());
    }

    public static Dependencies requires(String... ids) {
        return none().andRequires(ids);
    }

    /** Adds required predecessors without changing this declaration. */
    public Dependencies andRequires(String... ids) {
        return new Dependencies(adding(required, ids), after, before);
    }

    public Dependencies afterIfPresent(String... ids) {
        return new Dependencies(required, adding(after, ids), before);
    }

    public Dependencies beforeIfPresent(String... ids) {
        return new Dependencies(required, after, adding(before, ids));
    }

    private static Set<String> adding(Set<String> existing, String[] ids) {
        var result = new HashSet<>(existing);
        for (String id : ids) result.add(id);
        return result;
    }

    private static Set<String> validatedCopy(Set<String> ids) {
        var copy = Set.copyOf(ids);
        for (String id : copy) {
            ResourceId.parse(id);
        }
        return copy;
    }
}
