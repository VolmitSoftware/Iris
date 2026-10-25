package art.arcane.iris.probe;

import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.plan.WindowPlan;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.Reach;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

final class HydrologyPlanMetrics {
    static final float RIVER_LENGTH = 384f;
    static final int PERCHED_HEIGHT = 8;
    private static final int PERCHED_SEARCH = 1024;
    private static final int VERTEX_BUCKET = 64;

    private final HydrologySettings settings;
    private final Map<WindowKey, WindowPlan> windows;
    private final List<Pond> lakes;
    private final HydrologyWindows.WindowRange range;
    private final Consumer<String> examples;
    private final Map<Long, Reach> reaches = new LinkedHashMap<>();
    private final Map<Long, WindowKey> reachOwners = new HashMap<>();
    private final Map<Long, WindowKey> pondOwners = new HashMap<>();
    private final Map<WindowKey, Long> seams = new HashMap<>();
    private final Map<Long, Pond> endPonds = new HashMap<>();
    private final Map<Long, Set<Long>> pondReaches = new HashMap<>();
    private final Map<WindowKey, Integer> rivers;
    private final VertexIndex vertices;

    HydrologyPlanMetrics(HydrologySettings settings, Map<WindowKey, WindowPlan> windows, List<Pond> lakes, HydrologyWindows.WindowRange range,
                         Consumer<String> examples) {
        this.settings = settings;
        this.windows = windows;
        this.lakes = List.copyOf(lakes);
        this.range = range;
        this.examples = examples;
        for (Map.Entry<WindowKey, WindowPlan> entry : windows.entrySet()) {
            WindowKey key = entry.getKey();
            for (Reach reach : entry.getValue().reaches()) {
                reaches.putIfAbsent(reach.id(), reach);
                WindowKey owner = reachOwners.putIfAbsent(reach.id(), key);
                if (owner != null) {
                    seam(key, "reach " + reach.id() + " owned by " + owner + " and " + key);
                }
            }
            for (Pond pond : entry.getValue().ponds()) {
                WindowKey owner = pondOwners.putIfAbsent(pond.id(), key);
                if (owner != null) {
                    seam(key, "pond " + pond.id() + " owned by " + owner + " and " + key);
                }
            }
        }
        linkSeams();
        pondSeams();
        endPonds();
        this.rivers = stems();
        this.vertices = new VertexIndex(reaches.values());
    }

    Map<Long, Reach> reaches() {
        return reaches;
    }

    Pond endPond(Reach reach) {
        return endPonds.get(reach.id());
    }

    long seams(WindowKey key) {
        return seams.getOrDefault(key, 0L);
    }

    int rivers(WindowKey key) {
        return rivers.getOrDefault(key, 0);
    }

    double streams(WindowKey key) {
        double streams = 0D;
        for (Reach reach : windows.get(key).reaches()) {
            if (!river(reach)) {
                streams += length(reach);
            }
        }
        return streams;
    }

    int ponds(WindowKey key) {
        return windows.get(key).ponds().size();
    }

    Set<Integer> levels(WindowKey key) {
        Set<Integer> levels = new TreeSet<>();
        for (Pond pond : windows.get(key).ponds()) {
            levels.add(pond.level());
        }
        return levels;
    }

    int perched(WindowKey key) {
        int perched = 0;
        for (Pond pond : windows.get(key).ponds()) {
            Set<Long> connected = pondReaches.getOrDefault(pond.id(), Set.of());
            int grade = vertices.nearestGrade(pond.floorX(), pond.floorZ(), settings.seaLevel(), connected);
            if (pond.level() >= grade + PERCHED_HEIGHT) {
                perched++;
            }
        }
        return perched;
    }

    private void seam(WindowKey owner, String what) {
        if (range.contains(owner)) {
            seams.merge(owner, 1L, Long::sum);
            examples.accept("seam " + what);
        }
    }

    private void linkSeams() {
        for (Reach reach : reaches.values()) {
            if (reach.downstreamId() != 0L && !reachOwners.containsKey(reach.downstreamId())) {
                seam(reachOwners.get(reach.id()), "reach " + reach.id() + " links to missing reach " + reach.downstreamId());
            }
        }
        for (Map.Entry<WindowKey, WindowPlan> entry : windows.entrySet()) {
            for (Pond pond : entry.getValue().ponds()) {
                if (pond.outletReachId() != 0L && !reachOwners.containsKey(pond.outletReachId())) {
                    seam(entry.getKey(), "pond " + pond.id() + " drains to missing reach " + pond.outletReachId());
                }
            }
        }
    }

    private void pondSeams() {
        List<WindowKey> keys = new ArrayList<>(windows.keySet());
        for (WindowKey key : keys) {
            for (WindowKey other : keys) {
                if (other.wx() < key.wx() - 1 || other.wx() > key.wx() + 1 || other.wz() < key.wz() - 1 || other.wz() > key.wz() + 1
                        || compare(other, key) <= 0) {
                    continue;
                }
                for (Pond pond : windows.get(key).ponds()) {
                    for (Pond neighbour : windows.get(other).ponds()) {
                        if (pond.mask().overlaps(neighbour.mask())) {
                            seam(range.contains(other) ? other : key, "ponds " + pond.id() + " and " + neighbour.id() + " from " + key
                                    + " and " + other + " overlap");
                        }
                    }
                }
            }
        }
    }

    private static int compare(WindowKey left, WindowKey right) {
        return left.wz() != right.wz() ? Integer.compare(left.wz(), right.wz()) : Integer.compare(left.wx(), right.wx());
    }

    private void endPonds() {
        List<Pond> ponds = new ArrayList<>();
        for (WindowPlan plan : windows.values()) {
            ponds.addAll(plan.ponds());
        }
        ponds.addAll(lakes);
        for (Pond pond : ponds) {
            if (pond.outletReachId() != 0L) {
                pondReaches.computeIfAbsent(pond.id(), ignored -> new HashSet<>()).add(pond.outletReachId());
            }
        }
        for (Reach reach : reaches.values()) {
            if (reach.downstreamId() != 0L) {
                continue;
            }
            int last = reach.xs().length - 1;
            int x = (int) Math.floor(reach.xs()[last]);
            int z = (int) Math.floor(reach.zs()[last]);
            for (Pond pond : ponds) {
                if (pond.mask().contains(x, z)) {
                    endPonds.put(reach.id(), pond);
                    pondReaches.computeIfAbsent(pond.id(), ignored -> new HashSet<>()).add(reach.id());
                    break;
                }
            }
        }
    }

    private Reach onward(Reach reach) {
        if (reach.downstreamId() != 0L) {
            return reaches.get(reach.downstreamId());
        }
        Pond pond = endPonds.get(reach.id());
        return pond == null ? null : reaches.get(pond.outletReachId());
    }

    private Map<WindowKey, Integer> stems() {
        Map<Long, Reach> mainFeeder = new HashMap<>();
        for (Reach reach : reaches.values()) {
            Reach next = onward(reach);
            if (!river(reach) || next == null) {
                continue;
            }
            mainFeeder.merge(next.id(), reach, (current, candidate) -> candidate.catchment() > current.catchment()
                    || candidate.catchment() == current.catchment() && candidate.id() < current.id() ? candidate : current);
        }
        Map<WindowKey, Integer> counts = new HashMap<>();
        for (Reach reach : reaches.values()) {
            if (!river(reach)) {
                continue;
            }
            Reach next = onward(reach);
            if (next != null && river(next) && mainFeeder.get(next.id()) == reach) {
                continue;
            }
            float length = 0f;
            Set<Long> seen = new HashSet<>();
            for (Reach stem = reach; stem != null && seen.add(stem.id()); stem = mainFeeder.get(stem.id())) {
                length += length(stem);
            }
            WindowKey owner = reachOwners.get(reach.id());
            if (length >= RIVER_LENGTH && range.contains(owner)) {
                counts.merge(owner, 1, Integer::sum);
            }
        }
        return counts;
    }

    private boolean river(Reach reach) {
        return !reach.tier().equals(settings.tiers().get(0));
    }

    static float length(Reach reach) {
        float length = 0f;
        for (int point = 1; point < reach.xs().length; point++) {
            length += (float) Math.hypot(reach.xs()[point] - reach.xs()[point - 1], reach.zs()[point] - reach.zs()[point - 1]);
        }
        return length;
    }

    private static final class VertexIndex {
        private final Map<Long, List<long[]>> buckets = new HashMap<>();

        private VertexIndex(Iterable<Reach> reaches) {
            for (Reach reach : reaches) {
                for (int point = 0; point < reach.xs().length; point++) {
                    int x = (int) Math.floor(reach.xs()[point]);
                    int z = (int) Math.floor(reach.zs()[point]);
                    buckets.computeIfAbsent(bucket(Math.floorDiv(x, VERTEX_BUCKET), Math.floorDiv(z, VERTEX_BUCKET)), ignored -> new ArrayList<>())
                            .add(new long[]{x, z, reach.grade()[point], reach.id()});
                }
            }
        }

        private int nearestGrade(int x, int z, int fallback, Set<Long> excluded) {
            int bx = Math.floorDiv(x, VERTEX_BUCKET);
            int bz = Math.floorDiv(z, VERTEX_BUCKET);
            long best = Long.MAX_VALUE;
            int grade = fallback;
            int rings = PERCHED_SEARCH / VERTEX_BUCKET;
            for (int ring = 0; ring <= rings; ring++) {
                long reach = (long) Math.max(0, ring - 1) * VERTEX_BUCKET;
                if (best != Long.MAX_VALUE && best < reach * reach) {
                    break;
                }
                for (int dz = -ring; dz <= ring; dz++) {
                    for (int dx = -ring; dx <= ring; dx++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                            continue;
                        }
                        for (long[] vertex : buckets.getOrDefault(bucket(bx + dx, bz + dz), List.of())) {
                            if (excluded.contains(vertex[3])) {
                                continue;
                            }
                            long distance = (vertex[0] - x) * (vertex[0] - x) + (vertex[1] - z) * (vertex[1] - z);
                            if (distance < best) {
                                best = distance;
                                grade = (int) vertex[2];
                            }
                        }
                    }
                }
            }
            return grade;
        }

        private static long bucket(int bx, int bz) {
            return ((long) bx << 32) | (bz & 0xFFFFFFFFL);
        }
    }
}
