package art.arcane.iris.engine.hydrology;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

record HydrologyRegionalCourseGraph(List<DrainageNode> nodes, List<DrainageEdge> edges, int discharge) {
    private static final long NODE_SALT = 0x5245475055424c49L;

    HydrologyRegionalCourseGraph {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    static HydrologyRegionalCourseGraph create(HydrologyTerrainSampler sampler, Options options) {
        List<HydrologyPoint> path = options.path();
        double[] distance = new double[path.size()];
        IntArrayList selected = new IntArrayList();
        selected.add(0);
        double next = options.spacing();
        for (int index = 1; index < path.size(); index++) {
            HydrologyPoint previous = path.get(index - 1);
            HydrologyPoint point = path.get(index);
            distance[index] = distance[index - 1] + StrictMath.hypot(
                    (double) point.x() - previous.x(), (double) point.z() - previous.z());
            if (distance[index] >= next || index == path.size() - 1) {
                selected.add(index);
                next = distance[index] + options.spacing();
            }
        }
        ArrayList<DrainageNode> nodes = new ArrayList<>(selected.size());
        ArrayList<DrainageEdge> edges = new ArrayList<>(Math.max(0, selected.size() - 1));
        double length = distance[distance.length - 1];
        int contribution = 1;
        for (int index = 0; index < selected.size(); index++) {
            int station = selected.getInt(index);
            HydrologyPoint point = path.get(station);
            HydrologyTerrainSample terrain = Objects.requireNonNull(sampler.sample(point.x(), point.z()),
                    "Accepted regional drainage terrain");
            DrainageNode node = new DrainageNode(HydrologyHash.mix(options.courseId(), NODE_SALT, index, point.x(), point.z()),
                    point.x(), point.z(), terrain, length - distance[station], options.outletId());
            nodes.add(node);
            contribution = Math.max(contribution, options.flow().contributionAt(point.x(), point.z()));
            if (index > 0) {
                DrainageNode upstream = nodes.get(index - 1);
                edges.add(new DrainageEdge(HydrologyHash.mix(options.courseId(), upstream.id(), node.id()),
                        upstream.id(), node.id(), options.outletId(), upstream.potential() - node.potential(),
                        contribution, 0, List.of(upstream.naturalPoint(), node.naturalPoint())));
            }
        }
        return new HydrologyRegionalCourseGraph(nodes, edges, contribution);
    }

    record Options(long courseId, long outletId, int spacing, List<HydrologyPoint> path, HydrologyRegionalFlow flow) {
        Options {
            path = List.copyOf(path);
            Objects.requireNonNull(flow, "Regional drainage flow");
            if (spacing < 1 || path.size() < 2) {
                throw new IllegalArgumentException("Regional drainage requires positive spacing and a complete path.");
            }
        }
    }
}
