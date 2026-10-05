package com.volmit.host;

import com.volmit.generation.Dependencies;
import com.volmit.resource.ResourceId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;

/** Host-independent ordering of one complete modifier graph. Ready IDs use lexical order,
 * independent of discovery. Required IDs must exist. Optional edges apply only when present.
 * Self-dependencies and cycles fail. This graph never activates features or permits parallel writes.
 */
public final class DependencyOrder {
    private DependencyOrder() {}

    public static List<ResourceId> resolve(Map<ResourceId, Dependencies> declarations) {
        var graph = Map.copyOf(declarations);
        var incomingEdges = initializeIncomingEdges(graph.keySet());
        var outgoingEdges = new HashMap<ResourceId, Set<ResourceId>>();

        addDeclaredEdges(graph, incomingEdges, outgoingEdges);
        return topologicalOrder(graph, incomingEdges, outgoingEdges);
    }

    private static Map<ResourceId, Integer> initializeIncomingEdges(Set<ResourceId> ids) {
        var incomingEdges = new HashMap<ResourceId, Integer>();
        ids.forEach(id -> incomingEdges.put(id, 0));
        return incomingEdges;
    }

    private static void addDeclaredEdges(
            Map<ResourceId, Dependencies> graph,
            Map<ResourceId, Integer> incomingEdges,
            Map<ResourceId, Set<ResourceId>> outgoingEdges) {
        graph.forEach((id, dependencies) -> {
            Objects.requireNonNull(dependencies, "dependencies");
            addRequiredEdges(id, dependencies, graph, incomingEdges, outgoingEdges);
            addOptionalEdges(id, dependencies, graph, incomingEdges, outgoingEdges);
        });
    }

    private static void addRequiredEdges(
            ResourceId id,
            Dependencies dependencies,
            Map<ResourceId, Dependencies> graph,
            Map<ResourceId, Integer> incomingEdges,
            Map<ResourceId, Set<ResourceId>> outgoingEdges) {
        for (var name : dependencies.required()) {
            var predecessor = ResourceId.parse(name);
            if (!graph.containsKey(predecessor)) {
                throw new IllegalArgumentException(id + " requires missing modifier " + predecessor);
            }
            addEdge(predecessor, id, incomingEdges, outgoingEdges);
        }
    }

    private static void addOptionalEdges(
            ResourceId id,
            Dependencies dependencies,
            Map<ResourceId, Dependencies> graph,
            Map<ResourceId, Integer> incomingEdges,
            Map<ResourceId, Set<ResourceId>> outgoingEdges) {
        for (var name : dependencies.after()) {
            var predecessor = ResourceId.parse(name);
            rejectSelf(id, predecessor);
            if (graph.containsKey(predecessor)) {
                addEdge(predecessor, id, incomingEdges, outgoingEdges);
            }
        }
        for (var name : dependencies.before()) {
            var successor = ResourceId.parse(name);
            rejectSelf(id, successor);
            if (graph.containsKey(successor)) {
                addEdge(id, successor, incomingEdges, outgoingEdges);
            }
        }
    }

    private static List<ResourceId> topologicalOrder(
            Map<ResourceId, Dependencies> graph,
            Map<ResourceId, Integer> incomingEdges,
            Map<ResourceId, Set<ResourceId>> outgoingEdges) {
        var ready = new PriorityQueue<ResourceId>();
        incomingEdges.forEach((id, count) -> {
            if (count == 0) {
                ready.add(id);
            }
        });

        var ordered = new ArrayList<ResourceId>(graph.size());
        while (!ready.isEmpty()) {
            var id = ready.remove();
            ordered.add(id);
            releaseSuccessors(id, outgoingEdges, incomingEdges, ready);
        }

        if (ordered.size() != graph.size()) {
            throw cycleException(graph.keySet(), ordered);
        }
        return List.copyOf(ordered);
    }

    private static void releaseSuccessors(
            ResourceId id,
            Map<ResourceId, Set<ResourceId>> outgoingEdges,
            Map<ResourceId, Integer> incomingEdges,
            PriorityQueue<ResourceId> ready) {
        for (var successor : outgoingEdges.getOrDefault(id, Set.of())) {
            int remainingEdges = incomingEdges.compute(successor, (ignored, count) -> count - 1);
            if (remainingEdges == 0) {
                ready.add(successor);
            }
        }
    }

    private static IllegalArgumentException cycleException(Set<ResourceId> ids, List<ResourceId> ordered) {
        var unresolved = new TreeSet<>(ids);
        unresolved.removeAll(ordered);
        return new IllegalArgumentException("Modifier dependency cycle: " + unresolved);
    }

    private static void rejectSelf(ResourceId id, ResourceId other) {
        if (id.equals(other)) {
            throw new IllegalArgumentException("Self-dependency: " + id);
        }
    }

    private static void addEdge(
            ResourceId from,
            ResourceId to,
            Map<ResourceId, Integer> incomingEdges,
            Map<ResourceId, Set<ResourceId>> outgoingEdges) {
        rejectSelf(from, to);
        if (outgoingEdges.computeIfAbsent(from, ignored -> new HashSet<>()).add(to)) {
            incomingEdges.compute(to, (ignored, count) -> count + 1);
        }
    }
}
