package com.volmit.host;

import com.volmit.placement.PlacementContext;
import com.volmit.placement.PlacementEdit;
import com.volmit.placement.PlacementOutcome;
import com.volmit.placement.PlacementRequest;
import com.volmit.stage.Pipeline;
import java.util.Objects;

/** Reference executor of a resolved placement graph. It runs active stages in resolved order;
 * each fused block pass loops once over the request object's blocks and runs every active stage
 * of the pass for a block before moving to the next block. A rejection stops the attempt after
 * the current stage or block; the edit is committed only when no stage rejects or throws.
 */
public final class PlacementRunner {
    private final ResolvedGraph graph;

    public PlacementRunner(ResolvedGraph graph) {
        this.graph = Objects.requireNonNull(graph, "graph");
        if (graph.pipeline() != Pipeline.PLACEMENT) {
            throw new IllegalArgumentException("Expected a placement graph, got " + graph.pipeline());
        }
    }

    /** Takes ownership of a fresh edit and closes it on success, rejection or exception.
     * The host prepares the edit area, the read-only inputs and the settings configured for this
     * placement, and derives {@code invocationSeed} from the world seed and the placement's stable
     * identity. Each stage draws from its own stream of that seed ({@link StageStreams}). Stages never
     * receive commit/close rights. {@code timings} hears how long each stage that ran took; a block stage
     * is reported once per pass, with its time summed over the blocks it ran for, when the pass ends.
     */
    public PlacementOutcome place(
            PlacementRequest request,
            PlacementEdit edit,
            PlacementContext inputs,
            long invocationSeed,
            ConfiguredSettings settings,
            StageTimings timings) {
        Objects.requireNonNull(edit, "edit");
        try (edit) {
            Objects.requireNonNull(timings, "timings");
            var stages = new StageInvocation(graph, invocationSeed, settings);
            var access = PlacementViews.restrict(edit, edit.bounds());
            var placement = new PlacementInvocation(stages, request, inputs, access);
            runSegments(placement, timings);
            if (placement.isRejected()) {
                return PlacementOutcome.rejected(placement.rejection());
            }
            edit.commit();
            return PlacementOutcome.placed();
        }
    }

    private void runSegments(PlacementInvocation placement, StageTimings timings) {
        for (var segment : graph.segments()) {
            if (placement.isRejected()) {
                return;
            }
            switch (segment.kind()) {
                case INVOCATION -> runInvocationStage(segment.start(), placement, timings);
                case BLOCK -> runBlockPass(segment, placement, timings);
            }
        }
    }

    private void runInvocationStage(int position, PlacementInvocation placement, StageTimings timings) {
        if (!placement.stages().isActive(position)) {
            return;
        }
        long started = System.nanoTime();
        placement.stages().runInvocationStage(position, placement, placement::isRejected);
        timings.record(graph.stages().get(position), System.nanoTime() - started);
    }

    private void runBlockPass(Segment segment, PlacementInvocation placement, StageTimings timings) {
        var stages = placement.stages();
        int[] active = stages.activePositions(segment);
        if (active.length == 0) {
            return;
        }
        var pass = new BlockPass(active.length);
        for (int index = 0; index < active.length; index++) {
            pass.accumulators[index] = stages.newAccumulator(active[index]);
        }

        for (var block : placement.request().object().blocks()) {
            placement.beginBlock(block);
            runBlockStages(active, pass, placement);
            if (placement.isRejected()) {
                break;
            }
        }
        placement.endBlockPass();
        reportBlockPass(active, pass, timings);
        if (placement.isRejected()) {
            return;
        }
        for (int index = 0; index < active.length; index++) {
            stages.complete(active[index], pass.accumulators[index]);
        }
    }

    /** One nanoTime per stage boundary: each stage's time ends where the next one's starts. */
    private static void runBlockStages(int[] active, BlockPass pass, PlacementInvocation placement) {
        long started = System.nanoTime();
        for (int index = 0; index < active.length; index++) {
            placement.stages().runBlockStage(active[index], placement, pass.accumulators[index]);
            long finished = System.nanoTime();
            pass.nanos[index] += finished - started;
            pass.ran[index] = true;
            started = finished;
            if (placement.isRejected() || placement.isBlockSkipped()) {
                return;
            }
        }
    }

    private void reportBlockPass(int[] active, BlockPass pass, StageTimings timings) {
        for (int index = 0; index < active.length; index++) {
            if (pass.ran[index]) {
                timings.record(graph.stages().get(active[index]), pass.nanos[index]);
            }
        }
    }

    /** Per active stage of one block pass, in pass order: its accumulator, its summed time and whether it ran. */
    private static final class BlockPass {
        final Object[] accumulators;
        final long[] nanos;
        final boolean[] ran;

        BlockPass(int stages) {
            accumulators = new Object[stages];
            nanos = new long[stages];
            ran = new boolean[stages];
        }
    }
}
