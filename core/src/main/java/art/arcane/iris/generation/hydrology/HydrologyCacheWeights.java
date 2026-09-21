package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.cave.HydrologyCavePlan;

import java.util.List;

final class HydrologyCacheWeights {
    private static final long ENTRY_BYTES = 192L;
    private static final long TERRAIN_BYTES = 256L;
    private static final long POINT_BYTES = 40L;

    private HydrologyCacheWeights() {
    }

    static int bounded(long estimatedBytes, long budget, int maximumEntries) {
        return saturated(Math.max(estimatedBytes, Math.ceilDiv(budget, maximumEntries)));
    }

    static int bounded(long estimatedBytes, long budget, int maximumEntries, int units, int maximumUnits) {
        return bounded(Math.max(estimatedBytes, Math.ceilDiv((long) units * budget, maximumUnits)), budget, maximumEntries);
    }

    static int tile(HydrologyTile tile) {
        long bytes = network(tile.nodes(), tile.edges(), tile.outlets(), tile.courses(), tile.cavePlans());
        bytes += diagnostics(tile.localDiagnosticCandidates());
        bytes += (long) tile.regionalCourseIds().size() * 64L;
        RiverFootprint footprint = tile.footprint();
        if (footprint != null) {
            bytes += (long) footprint.size() * 64L;
            for (HydrologyColumnSample sample : footprint.columns().values()) {
                bytes += column(sample);
            }
        }
        return saturated(bytes);
    }

    static int column(HydrologyColumnSample sample) {
        return saturated(96L + (long) sample.layers().size() * 192L);
    }

    static int diagnostics(List<HydrologyDiagnosticCandidate> diagnostics) {
        return saturated(ENTRY_BYTES + (long) diagnostics.size() * 112L);
    }

    static int routing(SourceRoutingContext context) {
        return saturated(ENTRY_BYTES + grid(context.grid()) + routingPlan(context.surfaceRouting())
                + routingPlan(context.undergroundRouting()) + diagnostics(context.diagnostics()));
    }

    static int owner(CrossTileResolvedOwner owner) {
        HydrologyCaveCourseFilter.Result result = owner.draft().result();
        long bytes = network(result.nodes(), result.edges(), result.outlets(), result.courses(), result.cavePlans())
                + diagnostics(owner.draft().diagnostics());
        for (CrossTileRejectedCourse rejection : owner.observedRejections()) {
            bytes += 48L + course(rejection.course());
        }
        return saturated(bytes);
    }

    static int points(List<HydrologyPoint> points) {
        return saturated(ENTRY_BYTES + (long) points.size() * POINT_BYTES);
    }

    static int regionalNetwork(HydrologyRegionalNetwork network) {
        return saturated(network(network.nodes(), network.edges(), network.outlets(), network.courses(), network.cavePlans())
                + diagnostics(network.diagnostics()));
    }

    static int regionalCoarse(HydrologyRegionalCoarsePlan plan) {
        HydrologyRegionalGraph.Tree tree = plan.tree();
        long treeBytes = tree == null ? 0L : 96L + (long) tree.selected().length * 8L
                + (long) tree.accumulation().length * 4L + tree.retainedLabelCount() * 96L;
        return saturated(ENTRY_BYTES + grid(plan.grid()) + treeBytes + (long) plan.outlets().size() * 224L
                + (long) plan.sources().size() * 32L + (long) plan.envelopes().size() * 64L);
    }

    static int terrain(HydrologyTerrainSample terrain) {
        return saturated(128L + (terrain == null ? 0L : TERRAIN_BYTES));
    }

    private static long grid(HydrologySampledGrid grid) {
        return grid == null ? 0L : 96L + (long) grid.nodes().size() * (64L + TERRAIN_BYTES);
    }

    private static long routingPlan(HydrologyRoutingPlan plan) {
        if (plan == null) {
            return 0L;
        }
        return 128L + (long) plan.potential().length * 8L
                + ((long) plan.parent().length + plan.outletIndex().length + plan.routeLengths().length) * 4L
                + (long) plan.outlets().size() * 224L;
    }

    private static long network(List<DrainageNode> nodes, List<DrainageEdge> edges, List<RiverOutlet> outlets,
                                List<RiverCourse> courses, List<HydrologyCavePlan> cavePlans) {
        long bytes = ENTRY_BYTES + (long) nodes.size() * (96L + TERRAIN_BYTES) + (long) outlets.size() * 224L;
        for (DrainageEdge edge : edges) {
            bytes += 96L + points(edge.centerline());
        }
        for (RiverCourse course : courses) {
            bytes += course(course);
        }
        for (HydrologyCavePlan plan : cavePlans) {
            bytes += plan.estimatedRetainedBytes();
        }
        return bytes;
    }

    private static long course(RiverCourse course) {
        long bytes = 128L;
        for (DrainageEdge edge : course.drainageEdges()) {
            bytes += 96L + points(edge.centerline());
        }
        for (HydraulicSegment segment : course.segments()) {
            bytes += 128L + points(segment.centerline()) + (long) segment.channelProfile().size() * 16L;
        }
        return bytes;
    }

    private static int saturated(long bytes) {
        return (int) Math.clamp(bytes, 1L, Integer.MAX_VALUE);
    }
}
