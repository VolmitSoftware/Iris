package art.arcane.iris.generation.hydrology;

import art.arcane.iris.generation.hydrology.surface.SurfaceBounds;

import java.util.List;

record HydrologyRegionalCoarsePlan(
        HydrologySampledGrid grid,
        List<OutletCandidate> outlets,
        HydrologyRegionalGraph.Tree tree,
        List<Integer> sources,
        List<HydrologyRegionalEnvelope> envelopes,
        double longest,
        boolean coastal,
        int weight
) {
    HydrologyRegionalCoarsePlan {
        outlets = List.copyOf(outlets);
        sources = List.copyOf(sources);
        envelopes = List.copyOf(envelopes);
    }

    boolean intersects(SurfaceBounds bounds) {
        if (coastal) {
            return true;
        }
        for (HydrologyRegionalEnvelope envelope : envelopes) {
            if (envelope.intersects(bounds)) {
                return true;
            }
        }
        return false;
    }
}
