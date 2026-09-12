package art.arcane.iris.generation.hydrology;

import java.util.List;
import java.util.Objects;

public record DrainageEdge(
        long id,
        long upstreamNodeId,
        long downstreamNodeId,
        long outletId,
        double cost,
        int contributingSurfaceSources,
        int contributingUndergroundSources,
        List<HydrologyPoint> centerline
) {
    public DrainageEdge {
        if (upstreamNodeId == downstreamNodeId) {
            throw new IllegalArgumentException("A drainage edge cannot point to the same node.");
        }
        if (!Double.isFinite(cost) || cost <= 0D) {
            throw new IllegalArgumentException("Drainage edge cost must be finite and positive.");
        }
        if (contributingSurfaceSources < 0 || contributingUndergroundSources < 0) {
            throw new IllegalArgumentException("Contributing source counts cannot be negative.");
        }
        Objects.requireNonNull(centerline, "centerline");
        centerline = List.copyOf(centerline);
        if (centerline.size() < 2) {
            throw new IllegalArgumentException("A drainage edge requires at least two centerline points.");
        }
    }

    public int totalContributingSources() {
        return contributingSurfaceSources + contributingUndergroundSources;
    }
}
