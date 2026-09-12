package art.arcane.iris.generation.hydrology;

import java.util.List;
import java.util.Map;

record CompiledGraph(
        List<DrainageNode> nodes,
        List<DrainageEdge> edges,
        List<RiverOutlet> outlets,
        Map<Integer, DrainageEdge> edgeByUpstream
) {
}
