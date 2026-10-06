package com.volmit.host;

import com.volmit.placement.PlacementContext;
import com.volmit.placement.PlacementEdit;
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
     * receive commit/close rights.
     */
    public PlacementOutcome place(
            PlacementRequest request,
            PlacementEdit edit,
            PlacementContext inputs,
            long invocationSeed,
            ConfiguredSettings settings) {
        Objects.requireNonNull(edit, "edit");
        try (edit) {
            var stages = new StageInvocation(graph, invocationSeed, settings);
            var access = PlacementViews.restrict(edit, edit.bounds());
            var placement = new PlacementInvocation(stages, request, inputs, access);
            runSegments(placement);
            if (placement.isRejected()) {
                return PlacementOutcome.rejected(placement.rejection());
            }
            edit.commit();
            return PlacementOutcome.placed();
        }
    }

    private void runSegments(PlacementInvocation placement) {
        for (var segment : graph.segments()) {
            if (placement.isRejected()) {
                return;
            }
            switch (segment.kind()) {
                case INVOCATION -> placement.stages().runInvocationStage(segment.start(), placement);
                case BLOCK -> runBlockPass(segment, placement);
            }
        }
    }

    private static void runBlockPass(Segment segment, PlacementInvocation placement) {
        var stages = placement.stages();
        int[] active = stages.activePositions(segment);
        if (active.length == 0) {
            return;
        }
        var accumulators = new Object[active.length];
        for (int index = 0; index < active.length; index++) {
            accumulators[index] = stages.newAccumulator(active[index]);
        }

        for (var block : placement.request().object().blocks()) {
            placement.beginBlock(block);
            runBlockStages(active, accumulators, placement);
            if (placement.isRejected()) {
                return;
            }
        }
        placement.endBlockPass();
        for (int index = 0; index < active.length; index++) {
            stages.complete(active[index], accumulators[index]);
        }
    }

    private static void runBlockStages(int[] active, Object[] accumulators, PlacementInvocation placement) {
        for (int index = 0; index < active.length; index++) {
            placement.stages().runBlockStage(active[index], placement, accumulators[index]);
            if (placement.isRejected() || placement.isBlockSkipped()) {
                return;
            }
        }
    }
}
