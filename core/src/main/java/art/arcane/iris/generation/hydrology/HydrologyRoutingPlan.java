package art.arcane.iris.generation.hydrology;

import java.util.List;

record HydrologyRoutingPlan(
        double[] potential,
        int[] parent,
        int[] outletIndex,
        int[] routeLengths,
        List<OutletCandidate> outlets,
        boolean organicSurfaceRequired
) {
}
