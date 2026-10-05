package com.volmit.host;

import com.volmit.resource.ResourceId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Orders configured occurrences by dependency identity, then by occurrence ID. */
final class OccurrenceOrder {
    private OccurrenceOrder() {}

    static <T> List<T> order(
            List<ResourceId> orderedIds,
            List<T> occurrences,
            Function<? super T, ResourceId> identity,
            Function<? super T, ResourceId> occurrenceId) {
        Map<ResourceId, List<T>> occurrencesByIdentity = groupByIdentity(occurrences, identity);
        var ordered = new ArrayList<T>(occurrences.size());

        for (var id : orderedIds) {
            var group = occurrencesByIdentity.get(id);
            if (group == null) {
                continue;
            }

            group.sort((left, right) -> occurrenceId.apply(left).compareTo(occurrenceId.apply(right)));
            ordered.addAll(group);
        }

        return List.copyOf(ordered);
    }

    private static <T> Map<ResourceId, List<T>> groupByIdentity(
            List<T> occurrences,
            Function<? super T, ResourceId> identity) {
        var occurrencesByIdentity = new HashMap<ResourceId, List<T>>();
        for (var occurrence : occurrences) {
            occurrencesByIdentity.computeIfAbsent(identity.apply(occurrence), ignored -> new ArrayList<>())
                    .add(occurrence);
        }
        return occurrencesByIdentity;
    }
}
