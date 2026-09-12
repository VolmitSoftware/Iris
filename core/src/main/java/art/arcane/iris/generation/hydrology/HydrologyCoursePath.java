package art.arcane.iris.generation.hydrology;

import java.util.List;

/**
 * A routed source-to-outlet path, or, when {@code junction} is set, the cut prefix of one that ends
 * on the stem it joins: the path then owns only the drainage edges upstream of the junction.
 */
record HydrologyCoursePath(
        List<HydrologyPoint> points,
        List<DrainageEdge> pairEdges,
        List<DrainageEdge> edges,
        RiverOutlet outlet,
        boolean reachesOutlet,
        boolean organicSurfaceRequired,
        HydrologyPoint junction
) {
}
