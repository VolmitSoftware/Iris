package com.volmit.host;

import com.volmit.resource.ResourceId;
import com.volmit.stage.Pipeline;
import com.volmit.stage.StageKind;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/** Immutable, validated order of one pipeline, resolved once per pack load. Adjacent block
 * stages form one fused block pass; any invocation stage between them splits the pass, even
 * when that stage is inactive for a particular invocation. Execution never sorts or hashes.
 */
public final class ResolvedGraph {
    private final Pipeline pipeline;
    private final List<ResolvedStage> stages;
    private final List<ExecutableStage> executables;
    private final List<Class<?>> settingsTypes;
    private final List<Segment> segments;

    ResolvedGraph(
            Pipeline pipeline,
            List<ResolvedStage> stages,
            List<ExecutableStage> executables,
            List<Class<?>> settingsTypes) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.stages = List.copyOf(stages);
        this.executables = List.copyOf(executables);
        this.settingsTypes = List.copyOf(settingsTypes);
        segments = segments(this.executables);
    }

    public Pipeline pipeline() {
        return pipeline;
    }

    /** Stages in execution order. */
    public List<ResolvedStage> stages() {
        return stages;
    }

    /** Each fused block pass as stage ids in execution order. */
    public List<List<ResourceId>> blockPasses() {
        return segments.stream()
                .filter(segment -> segment.kind() == StageKind.BLOCK)
                .map(segment -> stages.subList(segment.start(), segment.end()).stream()
                        .map(stage -> stage.stage().id())
                        .toList())
                .toList();
    }

    /** Pairs with no path between them in either direction, first stage earlier in the order.
     * Their relative order comes only from the deterministic tie-break, so a pack author should
     * check that they do not conflict.
     */
    public List<UnorderedPair> unorderedPairs() {
        var reachable = reachability();
        var pairs = new ArrayList<UnorderedPair>();
        for (int first = 0; first < stages.size(); first++) {
            for (int second = first + 1; second < stages.size(); second++) {
                if (!reachable[first].get(second)) {
                    pairs.add(new UnorderedPair(id(first), id(second)));
                }
            }
        }
        return pairs;
    }

    /** Human-readable order, owners, edges, block passes and unordered pairs. */
    public String describe() {
        return GraphDescription.describe(this);
    }

    int size() {
        return executables.size();
    }

    ExecutableStage executable(int position) {
        return executables.get(position);
    }

    List<Segment> segments() {
        return segments;
    }

    List<Class<?>> settingsTypes() {
        return settingsTypes;
    }

    private ResourceId id(int position) {
        return stages.get(position).stage().id();
    }

    /** Edges always point forward in the order, so a reverse sweep completes each stage's
     * reachable set before any predecessor merges it.
     */
    private BitSet[] reachability() {
        var positions = new HashMap<ResourceId, Integer>();
        var reachable = new BitSet[stages.size()];
        for (int position = 0; position < stages.size(); position++) {
            positions.put(id(position), position);
            reachable[position] = new BitSet();
        }
        for (int position = stages.size() - 1; position >= 0; position--) {
            for (var edge : stages.get(position).incoming()) {
                int predecessor = positions.get(edge.from());
                reachable[predecessor].set(position);
                reachable[predecessor].or(reachable[position]);
            }
        }
        return reachable;
    }

    private static List<Segment> segments(List<ExecutableStage> executables) {
        var segments = new ArrayList<Segment>();
        int position = 0;
        while (position < executables.size()) {
            int end = segmentEnd(executables, position);
            segments.add(new Segment(executables.get(position).stage().kind(), position, end));
            position = end;
        }
        return List.copyOf(segments);
    }

    private static int segmentEnd(List<ExecutableStage> executables, int start) {
        if (executables.get(start).stage().kind() != StageKind.BLOCK) {
            return start + 1;
        }
        int end = start + 1;
        while (end < executables.size() && executables.get(end).stage().kind() == StageKind.BLOCK) {
            end++;
        }
        return end;
    }

    public record UnorderedPair(ResourceId first, ResourceId second) {
        public UnorderedPair {
            Objects.requireNonNull(first, "first");
            Objects.requireNonNull(second, "second");
        }
    }
}
