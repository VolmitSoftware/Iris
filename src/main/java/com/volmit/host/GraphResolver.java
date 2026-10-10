package com.volmit.host;

import com.volmit.resource.ResourceId;
import com.volmit.stage.Pipeline;
import com.volmit.stage.Stage;
import com.volmit.stage.StageKind;
import com.volmit.stage.StageRef;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Validates and orders the stages of one pipeline. Nodes are numbered in lexical id order,
 * which makes every choice below independent of module install order.
 */
final class GraphResolver {
    private final Pipeline pipeline;
    private final Map<ResourceId, StageDeclaration> installed = new HashMap<>();
    private final List<StageDeclaration> nodes;
    private final Map<ResourceId, Integer> nodeById = new HashMap<>();
    private final List<Map<Integer, StageEdge.Kind>> predecessors = new ArrayList<>();
    private final List<List<StageRef>> absentSoftTargets = new ArrayList<>();

    private GraphResolver(Pipeline pipeline, Collection<StageDeclaration> declarations) {
        this.pipeline = pipeline;
        declarations.forEach(declaration -> installed.put(declaration.stage().id(), declaration));
        nodes = declarations.stream()
                .filter(declaration -> declaration.stage().pipeline() == pipeline)
                .sorted(Comparator.comparing(declaration -> declaration.stage().id()))
                .toList();
        for (int node = 0; node < nodes.size(); node++) {
            nodeById.put(nodes.get(node).stage().id(), node);
            predecessors.add(new TreeMap<>());
            absentSoftTargets.add(new ArrayList<>());
        }
    }

    static ResolvedGraph resolve(Pipeline pipeline, Collection<StageDeclaration> declarations) {
        var resolver = new GraphResolver(pipeline, declarations);
        resolver.addDeclaredEdges();
        return resolver.toResolvedGraph(resolver.executionOrder());
    }

    private void addDeclaredEdges() {
        for (int node = 0; node < nodes.size(); node++) {
            addRequiredEdges(node);
            addSoftEdges(node);
        }
    }

    private void addRequiredEdges(int node) {
        var declaration = nodes.get(node);
        for (var required : declaration.requirements()) {
            var target = findRequired(declaration, required);
            addEdge(nodeById.get(target.stage().id()), node, StageEdge.Kind.REQUIRED);
        }
    }

    private void addSoftEdges(int node) {
        var declaration = nodes.get(node);
        for (var reference : declaration.softAfter()) {
            findSoftTarget(declaration, reference).ifPresentOrElse(
                    target -> addEdge(target, node, StageEdge.Kind.SOFT),
                    () -> absentSoftTargets.get(node).add(reference));
        }
        for (var reference : declaration.softBefore()) {
            findSoftTarget(declaration, reference).ifPresentOrElse(
                    target -> addEdge(node, target, StageEdge.Kind.SOFT),
                    () -> absentSoftTargets.get(node).add(reference));
        }
    }

    private StageDeclaration findRequired(StageDeclaration dependent, Stage<?> required) {
        var target = installed.get(required.id());
        if (target == null) {
            throw new IllegalArgumentException("Stage " + dependent + " requires " + required
                    + ", which no installed module defines (expected a module in namespace '"
                    + required.id().namespace() + "')");
        }
        requireSamePipeline(dependent, target);
        if (target.stage() != required) {
            throw new IllegalArgumentException("Stage " + dependent + " requires a different declaration of "
                    + target + "; reference the owning module's stage constant");
        }
        return target;
    }

    private Optional<Integer> findSoftTarget(StageDeclaration dependent, StageRef reference) {
        var target = installed.get(reference.id());
        if (target == null) {
            return Optional.empty();
        }
        requireSamePipeline(dependent, target);
        return Optional.of(nodeById.get(reference.id()));
    }

    private void requireSamePipeline(StageDeclaration dependent, StageDeclaration target) {
        if (target.stage().pipeline() == pipeline) {
            return;
        }
        throw new IllegalArgumentException("Stage " + dependent + " in pipeline " + pipeline
                + " depends on " + target + " in pipeline " + target.stage().pipeline()
                + "; dependencies cannot cross pipelines");
    }

    private void addEdge(int from, int to, StageEdge.Kind kind) {
        predecessors.get(to).merge(from, kind, StageEdge.Kind::strongest);
    }

    private int[] executionOrder() {
        int[] waitingOn = new int[nodes.size()];
        var successors = successors();
        var ready = new ReadyStages();
        for (int node = 0; node < nodes.size(); node++) {
            waitingOn[node] = predecessors.get(node).size();
            if (waitingOn[node] == 0) {
                ready.add(node, kind(node));
            }
        }

        int[] order = new int[nodes.size()];
        int ordered = 0;
        while (!ready.isEmpty()) {
            int node = ready.next();
            order[ordered++] = node;
            for (int successor : successors.get(node)) {
                if (--waitingOn[successor] == 0) {
                    ready.add(successor, kind(successor));
                }
            }
        }
        if (ordered != nodes.size()) {
            throw cycleError(waitingOn);
        }
        return order;
    }

    private List<List<Integer>> successors() {
        var successors = new ArrayList<List<Integer>>();
        nodes.forEach(ignored -> successors.add(new ArrayList<>()));
        for (int node = 0; node < nodes.size(); node++) {
            for (int predecessor : predecessors.get(node).keySet()) {
                successors.get(predecessor).add(node);
            }
        }
        return successors;
    }

    private StageKind kind(int node) {
        return nodes.get(node).stage().kind();
    }

    private IllegalArgumentException cycleError(int[] waitingOn) {
        var cycle = findCycle(waitingOn);
        var description = new StringBuilder(nodes.get(cycle.getFirst()).toString());
        for (int index = 0; index < cycle.size(); index++) {
            int from = cycle.get(index);
            int to = cycle.get((index + 1) % cycle.size());
            var kind = predecessors.get(to).get(from).name().toLowerCase();
            description.append(" -[").append(kind).append("]-> ").append(nodes.get(to));
        }
        return new IllegalArgumentException("Stage cycle in pipeline " + pipeline + ": " + description);
    }

    /** Every unordered node still waits on an unordered predecessor, so walking predecessors
     * from any of them must revisit a node; the revisited stretch is a cycle.
     */
    private List<Integer> findCycle(int[] waitingOn) {
        int current = firstWaiting(waitingOn);
        var walk = new ArrayList<Integer>();
        var walkIndex = new HashMap<Integer, Integer>();
        while (!walkIndex.containsKey(current)) {
            walkIndex.put(current, walk.size());
            walk.add(current);
            current = firstWaitingPredecessor(current, waitingOn);
        }
        var cycle = new ArrayList<>(walk.subList(walkIndex.get(current), walk.size()));
        Collections.reverse(cycle);
        Collections.rotate(cycle, -cycle.indexOf(Collections.min(cycle)));
        return cycle;
    }

    private static int firstWaiting(int[] waitingOn) {
        for (int node = 0; node < waitingOn.length; node++) {
            if (waitingOn[node] > 0) {
                return node;
            }
        }
        throw new IllegalStateException("No stage is waiting");
    }

    private int firstWaitingPredecessor(int node, int[] waitingOn) {
        return predecessors.get(node).keySet().stream()
                .filter(predecessor -> waitingOn[predecessor] > 0)
                .findFirst()
                .orElseThrow();
    }

    private ResolvedGraph toResolvedGraph(int[] order) {
        int[] position = new int[order.length];
        for (int index = 0; index < order.length; index++) {
            position[order[index]] = index;
        }

        var stages = new ArrayList<ResolvedStage>(order.length);
        var executables = new ExecutableStage[order.length];
        var readable = new BitSet[order.length];
        var settingsTypes = new ArrayList<Class<?>>();
        for (int index = 0; index < order.length; index++) {
            int node = order[index];
            var declaration = nodes.get(node);
            int[] requirements = requiredPositions(node, position);
            readable[index] = transitiveRequirements(requirements, readable);
            executables[index] = new ExecutableStage(
                    declaration.stage(),
                    declaration.body(),
                    requirements,
                    readable[index].stream().mapToObj(readablePosition -> nodes.get(order[readablePosition]).stage())
                            .toArray(Stage<?>[]::new),
                    readable[index].stream().toArray(),
                    settingsSlot(declaration, settingsTypes),
                    StageStreams.idHash(declaration.stage().id()),
                    declaration.log());
            stages.add(new ResolvedStage(declaration.stage(), declaration.module(), declaration.settings(),
                    incomingEdges(node, position), absentSoftTargets.get(node), declaration.reach()));
        }
        return new ResolvedGraph(pipeline, stages, List.of(executables), settingsTypes);
    }

    private int[] requiredPositions(int node, int[] position) {
        return predecessors.get(node).entrySet().stream()
                .filter(edge -> edge.getValue() == StageEdge.Kind.REQUIRED)
                .mapToInt(edge -> position[edge.getKey()])
                .sorted()
                .toArray();
    }

    private static BitSet transitiveRequirements(int[] requirements, BitSet[] readable) {
        var result = new BitSet();
        for (int required : requirements) {
            result.set(required);
            result.or(readable[required]);
        }
        return result;
    }

    private static int settingsSlot(StageDeclaration declaration, List<Class<?>> settingsTypes) {
        if (declaration.settings().isEmpty()) {
            return ExecutableStage.NO_SETTINGS;
        }
        var type = declaration.settings().orElseThrow().type();
        if (!settingsTypes.contains(type)) {
            settingsTypes.add(type);
        }
        return settingsTypes.indexOf(type);
    }

    private List<StageEdge> incomingEdges(int node, int[] position) {
        var target = nodes.get(node).stage().id();
        return predecessors.get(node).entrySet().stream()
                .sorted(Comparator.comparingInt(edge -> position[edge.getKey()]))
                .map(edge -> new StageEdge(nodes.get(edge.getKey()).stage().id(), target, edge.getValue()))
                .toList();
    }
}
