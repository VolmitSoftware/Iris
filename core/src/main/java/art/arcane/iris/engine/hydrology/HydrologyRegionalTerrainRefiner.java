package art.arcane.iris.engine.hydrology;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

final class HydrologyRegionalTerrainRefiner {
    private static final int GRID_SPACING = 16;
    private static final int MAXIMUM_EXPANSIONS = 4096;
    private static final int MAXIMUM_SAMPLES = 65536;
    private static final int MAXIMUM_SKIPPED_WAYPOINTS = 2;
    private static final int[][] NEIGHBOURS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}, {1, 1}, {-1, 1}, {-1, -1}, {1, -1}};

    private final HydrologyPlanner planner;
    private final Cache<Edge, Reach> edges;
    private final HydrologyRegionalHydraulics hydraulics;
    private final Cache<Long, Terrain> terrain;

    HydrologyRegionalTerrainRefiner(HydrologyPlanner planner) {
        this.planner = planner;
        this.hydraulics = new HydrologyRegionalHydraulics(planner.settings);
        this.edges = Caffeine.newBuilder().maximumSize(128).build();
        this.terrain = Caffeine.newBuilder().maximumSize(MAXIMUM_SAMPLES).build();
    }

    void clear() {
        edges.invalidateAll();
        terrain.invalidateAll();
    }

    HydrologyTerrainSample sample(int x, int z) {
        return terrain.get(RiverFootprint.pack(x, z), key -> new Terrain(planner.naturalSampler == null
                ? planner.sampler.sample(x, z) : planner.naturalSampler.sampleBasisWithoutSlope(x, z))).sample();
    }

    List<HydrologyPoint> refine(List<HydrologyPoint> guide, String profile, boolean coastal, HydrologyTerrainSampler receiver) {
        if (guide.size() < 2) {
            return List.of();
        }
        double maximumLength = planner.settings.routing().maximumRouteLength();
        double remaining = 0D;
        for (int index = 1; index < guide.size(); index++) {
            remaining += distance(guide.get(index - 1), guide.get(index));
            if (remaining > maximumLength) {
                return List.of();
            }
        }
        Terminal terminal = terminal(guide.getLast(), profile, coastal, receiver);
        int[] requiredHead = requiredHeads(guide, terminal, MAXIMUM_SKIPPED_WAYPOINTS + 1);
        if (requiredHead.length == 0) {
            return List.of();
        }
        int availableHead = Integer.MAX_VALUE;
        double length = 0D;
        ArrayList<HydrologyPoint> refined = new ArrayList<>();
        refined.add(guide.getFirst());
        Map<Long, Integer> stations = new HashMap<>();
        stations.put(RiverFootprint.pack(guide.getFirst().x(), guide.getFirst().z()), 0);
        for (int index = 1; index < guide.size(); index++) {
            int start = index - 1;
            Reach reach = null;
            int selected = index;
            for (int candidate = index; candidate < guide.size() && candidate <= index + MAXIMUM_SKIPPED_WAYPOINTS; candidate++) {
                Edge edge = new Edge(guide.get(start), guide.get(candidate), profile, coastal,
                        requiredHead[candidate], availableHead, terminal);
                reach = edges.get(edge, this::route);
                if (!reach.points().isEmpty()) {
                    selected = candidate;
                    break;
                }
            }
            if (reach.points().isEmpty()) {
                return List.of();
            }
            List<HydrologyPoint> points = reach.points();
            availableHead = reach.availableHead();
            for (int skipped = start; skipped < selected; skipped++) {
                remaining -= distance(guide.get(skipped), guide.get(skipped + 1));
            }
            index = selected;
            for (int point = 1; point < points.size(); point++) {
                HydrologyPoint next = points.get(point);
                long key = RiverFootprint.pack(next.x(), next.z());
                Integer previous = stations.get(key);
                if (previous != null) {
                    while (refined.size() > previous + 1) {
                        HydrologyPoint removed = refined.removeLast();
                        stations.remove(RiverFootprint.pack(removed.x(), removed.z()));
                        length -= distance(refined.getLast(), removed);
                    }
                } else {
                    length += distance(refined.getLast(), next);
                    stations.put(key, refined.size());
                    refined.add(next);
                }
                if (length + Math.max(0D, remaining) > maximumLength) {
                    return List.of();
                }
            }
        }
        return simplify(refined, profile, coastal, terminal);
    }

    boolean receivingTerminal(HydrologyPoint point, String profile, boolean coastal, HydrologyTerrainSampler receiver) {
        HydrologyTerrainSample land = sample(point.x(), point.z());
        if (land == null || land.ocean() || land.naturalHeight() < planner.settings.seaLevel() || !land.outletAllowed()) {
            return false;
        }
        for (int index = 0; index < 4; index++) {
            int[] offset = NEIGHBOURS[index];
            HydrologyTerrainSample ocean = sample(point.x() + offset[0], point.z() + offset[1]);
            if (ocean != null && receiver.receivingWater(point.x() + offset[0], point.z() + offset[1], planner.settings.seaLevel())
                    && ocean.preferredProfileKeys().contains(profile) && land.drainsInto(ocean)
                    && (!coastal || ocean.drainsInto(land))) {
                return true;
            }
        }
        return false;
    }

    private Terminal terminal(HydrologyPoint point, String profile, boolean coastal, HydrologyTerrainSampler receiver) {
        int inlet = receivingTerminal(point, profile, coastal, receiver) ? planner.settings.surface().banks().inlet().length() : 0;
        return new Terminal(point, inlet + inlet / 2);
    }

    private int minimumHead(HydrologyTerrainSample terrain, int x, int z, Terminal terminal) {
        boolean inlet = terminal.possibleStations() > 0
                && Math.max(Math.abs(x - terminal.landward().x()), Math.abs(z - terminal.landward().z())) < terminal.possibleStations();
        return inlet ? hydraulics.inletMinimumHead(terrain) : hydraulics.minimumHead(terrain);
    }

    private int[] requiredHeads(List<HydrologyPoint> points, Terminal terminal, int maximumAdvance) {
        int[] heads = new int[points.size()];
        for (int index = points.size() - 1; index >= 0; index--) {
            HydrologyPoint point = points.get(index);
            HydrologyTerrainSample terrain = sample(point.x(), point.z());
            if (terrain == null) {
                return new int[0];
            }
            int required = planner.settings.seaLevel();
            if (index + 1 < points.size()) {
                required = Integer.MAX_VALUE;
                for (int downstream = index + 1; downstream < points.size() && downstream <= index + maximumAdvance; downstream++) {
                    required = Math.min(required, heads[downstream]);
                }
            }
            heads[index] = Math.max(required, minimumHead(terrain, point.x(), point.z(), terminal));
        }
        return heads;
    }

    private List<HydrologyPoint> simplify(List<HydrologyPoint> points, String profile, boolean coastal, Terminal terminal) {
        double maximumReach = Math.min(512D, planner.settings.routing().regional().sampleSpacing() * 2D);
        Map<Long, HydrologyTerrainSample> samples = new HashMap<>();
        ArrayList<HydrologyPoint> simplified = new ArrayList<>();
        int current = 0;
        int[] requiredHead = requiredHeads(points, terminal, 1);
        if (requiredHead.length == 0) {
            return List.of();
        }
        int availableHead = Integer.MAX_VALUE;
        simplified.add(points.getFirst());
        while (current + 1 < points.size()) {
            int farthest = current + 1;
            double reach = distance(points.get(current), points.get(farthest));
            while (farthest + 1 < points.size()) {
                double next = distance(points.get(farthest), points.get(farthest + 1));
                if (reach + next > maximumReach) {
                    break;
                }
                reach += next;
                farthest++;
            }
            int selected = current + 1;
            Traversal selectedTraversal = traverse(points.get(current), points.get(selected),
                    new Edge(points.get(current), points.get(selected), profile, coastal, requiredHead[selected], availableHead, terminal),
                    samples, availableHead);
            for (int candidate = farthest; candidate > current + 1; candidate--) {
                Edge shortcut = new Edge(points.get(current), points.get(candidate), profile, coastal,
                        requiredHead[candidate], availableHead, terminal);
                Traversal traversal = traverse(shortcut.start(), shortcut.end(), shortcut, samples, availableHead);
                if (traversal.cost() == 0D) {
                    selected = candidate;
                    selectedTraversal = traversal;
                    break;
                }
            }
            if (!Double.isFinite(selectedTraversal.cost())) {
                return List.copyOf(points);
            }
            availableHead = selectedTraversal.availableHead();
            current = selected;
            simplified.add(points.get(current));
        }
        return List.copyOf(simplified);
    }

    private Reach route(Edge edge) {
        Map<Long, HydrologyTerrainSample> samples = new HashMap<>();
        Traversal direct = traverse(edge.start(), edge.end(), edge, samples, edge.maximumHead());
        if (direct.cost() == 0D) {
            return new Reach(List.of(edge.start(), edge.end()), direct.availableHead());
        }
        int margin = planner.settings.routing().regional().sampleSpacing();
        int minimumX = Math.min(edge.start().x(), edge.end().x()) - margin;
        int maximumX = Math.max(edge.start().x(), edge.end().x()) + margin;
        int minimumZ = Math.min(edge.start().z(), edge.end().z()) - margin;
        int maximumZ = Math.max(edge.start().z(), edge.end().z()) + margin;
        Map<Long, List<Node>> visited = new HashMap<>();
        PriorityQueue<Pending> pending = new PriorityQueue<>(Comparator.comparingDouble(Pending::estimate)
                .thenComparingLong(Pending::key)
                .thenComparing(Comparator.comparingInt(Pending::availableHead).reversed()));
        long firstKey = RiverFootprint.pack(edge.start().x(), edge.start().z());
        long lastKey = RiverFootprint.pack(edge.end().x(), edge.end().z());
        Search search = new Search(edge, samples, visited, pending);
        Node first = new Node(edge.start(), 0D, edge.maximumHead(), null);
        ArrayList<Node> firstLabels = new ArrayList<>(1);
        firstLabels.add(first);
        visited.put(firstKey, firstLabels);
        pending.add(new Pending(firstKey, first, distance(edge.start(), edge.end())));
        for (int expanded = 0; !pending.isEmpty() && expanded < MAXIMUM_EXPANSIONS; expanded++) {
            Pending next = pending.remove();
            Node current = next.node();
            if (!retained(visited.get(next.key()), current)) {
                continue;
            }
            if (next.key() == lastKey) {
                return new Reach(path(current), current.availableHead());
            }
            if (distance(current.point(), edge.end()) <= GRID_SPACING * StrictMath.sqrt(2D)) {
                offer(search, current, edge.end());
            }
            for (int[] offset : NEIGHBOURS) {
                int x = current.point().x() + offset[0] * GRID_SPACING;
                int z = current.point().z() + offset[1] * GRID_SPACING;
                if (x < minimumX || x > maximumX || z < minimumZ || z > maximumZ) {
                    continue;
                }
                offer(search, current, new HydrologyPoint(x, current.point().y(), z));
            }
        }
        return new Reach(List.of(), edge.maximumHead());
    }

    private void offer(Search search, Node current, HydrologyPoint target) {
        HydrologyTerrainSample terrain = sample(target.x(), target.z(), search.samples());
        if (!allowed(terrain, search.edge())) {
            return;
        }
        HydrologyPoint point = new HydrologyPoint(target.x(), terrain.naturalHeight(), target.z());
        double cost = current.cost() + distance(current.point(), point)
                + Math.max(0D, point.y() - current.point().y()) * planner.settings.routing().uphillPenalty()
                + Math.max(0D, terrain.routingCost() * terrain.routingMultiplier());
        long key = RiverFootprint.pack(point.x(), point.z());
        List<Node> labels = search.visited().get(key);
        if (dominated(labels, cost, current.availableHead())) {
            return;
        }
        Traversal traversal = traverse(current.point(), point, search.edge(), search.samples(), current.availableHead());
        cost += traversal.cost();
        if (!Double.isFinite(cost) || dominated(labels, cost, traversal.availableHead())) {
            return;
        }
        cost += clearanceCost(point, search.edge(), search.samples());
        if (dominated(labels, cost, traversal.availableHead())) {
            return;
        }
        if (labels == null) {
            labels = new ArrayList<>(2);
            search.visited().put(key, labels);
        }
        for (int index = labels.size() - 1; index >= 0; index--) {
            Node previous = labels.get(index);
            if (cost <= previous.cost() && traversal.availableHead() >= previous.availableHead()) {
                labels.remove(index);
            }
        }
        Node retained = new Node(point, cost, traversal.availableHead(), current);
        labels.add(retained);
        search.pending().add(new Pending(key, retained, cost + distance(point, search.edge().end())));
    }

    private static boolean dominated(List<Node> labels, double cost, int availableHead) {
        if (labels == null) {
            return false;
        }
        for (Node previous : labels) {
            if (previous.cost() <= cost && previous.availableHead() >= availableHead) {
                return true;
            }
        }
        return false;
    }

    private static boolean retained(List<Node> labels, Node current) {
        for (Node retained : labels) {
            if (retained == current) {
                return true;
            }
        }
        return false;
    }

    private Traversal traverse(HydrologyPoint start, HydrologyPoint end, Edge edge,
                               Map<Long, HydrologyTerrainSample> samples, int availableHead) {
        int steps = Math.max(Math.abs(end.x() - start.x()), Math.abs(end.z() - start.z()));
        HydrologyTerrainSample previous = null;
        double distance = distance(start, end);
        double tangentX = distance == 0D ? 1D : (end.x() - start.x()) / distance;
        double tangentZ = distance == 0D ? 0D : (end.z() - start.z()) / distance;
        HydrologyTerrainSampler bankSampler = (x, z) -> sample(x, z, samples);
        double cost = 0D;
        for (int step = 0; step <= steps; step++) {
            double progress = steps == 0 ? 0D : step / (double) steps;
            int x = (int) StrictMath.round(start.x() + (end.x() - start.x()) * progress);
            int z = (int) StrictMath.round(start.z() + (end.z() - start.z()) * progress);
            HydrologyTerrainSample terrain = sample(x, z, samples);
            if (!allowed(terrain, edge) || previous != null && (!previous.drainsInto(terrain)
                    || edge.coastal() && !terrain.drainsInto(previous))) {
                return new Traversal(Double.POSITIVE_INFINITY, availableHead);
            }
            if (!edge.coastal()) {
                availableHead = hydraulics.supportedHead(new HydrologyRegionalHydraulics.HeadStation(
                        x, z, tangentX, tangentZ, terrain, availableHead), bankSampler);
                if (availableHead < Math.max(edge.minimumHead(), minimumHead(terrain, x, z, edge.terminal()))) {
                    return new Traversal(Double.POSITIVE_INFINITY, availableHead);
                }
            }
            cost += ridgeCost(x, z, terrain, edge);
            previous = terrain;
        }
        return new Traversal(cost, availableHead);
    }

    private double ridgeCost(int x, int z, HydrologyTerrainSample terrain, Edge edge) {
        if (edge.coastal()) {
            return 0D;
        }
        double deltaX = edge.end().x() - edge.start().x();
        double deltaZ = edge.end().z() - edge.start().z();
        double squared = deltaX * deltaX + deltaZ * deltaZ;
        double progress = squared == 0D ? 0D : Math.max(0D, Math.min(1D,
                ((x - edge.start().x()) * deltaX + (z - edge.start().z()) * deltaZ) / squared));
        double envelope = edge.start().y() + (edge.end().y() - edge.start().y()) * progress;
        int incision = terrain.surfacePolicy().maximumIncision(planner.settings.surface().maximumIncision());
        double permitted = Math.min(incision, StrictMath.floor(incision * terrain.incisionMultiplier()));
        double excess = terrain.naturalHeight() - envelope + planner.settings.surface().minimumDepth()
                + planner.settings.surface().banks().sink() - permitted;
        return Math.max(0D, excess) * Math.max(1D, planner.settings.routing().uphillPenalty());
    }

    private double clearanceCost(HydrologyPoint point, Edge edge, Map<Long, HydrologyTerrainSample> samples) {
        double radius = planner.settings.surface().maximumWidth() / 2D
                * (1D + planner.settings.surface().banks().roughness()) + 1D;
        int blocked = 0;
        for (int[] offset : NEIGHBOURS) {
            double scale = offset[0] != 0 && offset[1] != 0 ? radius / StrictMath.sqrt(2D) : radius;
            int x = point.x() + (int) StrictMath.round(offset[0] * scale);
            int z = point.z() + (int) StrictMath.round(offset[1] * scale);
            if (!allowed(sample(x, z, samples), edge)) {
                blocked++;
            }
        }
        return blocked * GRID_SPACING / 2D;
    }

    private boolean allowed(HydrologyTerrainSample terrain, Edge edge) {
        return terrain != null && !terrain.ocean() && terrain.naturalHeight() >= planner.settings.seaLevel() && terrain.transitAllowed()
                && terrain.preferredProfileKeys().contains(edge.profile())
                && (!edge.coastal() || terrain.naturalHeight() - planner.settings.seaLevel()
                + planner.settings.surface().minimumDepth()
                <= planner.settings.routing().regional().maximumCoastalIncision());
    }

    private HydrologyTerrainSample sample(int x, int z, Map<Long, HydrologyTerrainSample> samples) {
        long key = RiverFootprint.pack(x, z);
        HydrologyTerrainSample sampled = samples.get(key);
        if (sampled != null || samples.containsKey(key) || samples.size() >= MAXIMUM_SAMPLES) {
            return sampled;
        }
        sampled = sample(x, z);
        samples.put(key, sampled);
        return sampled;
    }

    private static List<HydrologyPoint> path(Node current) {
        ArrayList<HydrologyPoint> points = new ArrayList<>();
        while (current != null) {
            points.add(current.point());
            current = current.parent();
        }
        Collections.reverse(points);
        return List.copyOf(points);
    }

    private static double distance(HydrologyPoint first, HydrologyPoint second) {
        return StrictMath.sqrt(first.distanceSquared2D(second));
    }

    private record Edge(HydrologyPoint start, HydrologyPoint end, String profile, boolean coastal,
                        int minimumHead, int maximumHead, Terminal terminal) {
    }

    private record Terminal(HydrologyPoint landward, int possibleStations) {
    }

    private record Reach(List<HydrologyPoint> points, int availableHead) {
    }

    private record Traversal(double cost, int availableHead) {
    }

    private record Terrain(HydrologyTerrainSample sample) {
    }

    private record Search(Edge edge, Map<Long, HydrologyTerrainSample> samples, Map<Long, List<Node>> visited,
                          PriorityQueue<Pending> pending) {
    }

    private record Node(HydrologyPoint point, double cost, int availableHead, Node parent) {
    }

    private record Pending(long key, Node node, double estimate) {
        private int availableHead() {
            return node.availableHead();
        }
    }
}
