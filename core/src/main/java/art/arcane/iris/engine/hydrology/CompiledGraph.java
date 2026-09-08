package art.arcane.iris.engine.hydrology;

import java.util.List;
import java.util.Map;

record CompiledGraph(
        List<DrainageNode> nodes,
        List<DrainageEdge> edges,
        List<RiverOutlet> outlets,
        Map<Integer, DrainageEdge> edgeByUpstream
) {
}
