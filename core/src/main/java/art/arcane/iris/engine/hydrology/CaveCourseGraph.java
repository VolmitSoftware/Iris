package art.arcane.iris.engine.hydrology;

import java.util.List;

record CaveCourseGraph(
        List<DrainageNode> nodes,
        List<DrainageEdge> edges,
        List<RiverOutlet> outlets
) {
}
