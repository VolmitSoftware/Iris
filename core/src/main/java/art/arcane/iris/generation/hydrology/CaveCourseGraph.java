package art.arcane.iris.generation.hydrology;

import java.util.List;

record CaveCourseGraph(
        List<DrainageNode> nodes,
        List<DrainageEdge> edges,
        List<RiverOutlet> outlets
) {
}
