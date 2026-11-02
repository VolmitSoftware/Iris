package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.raster.WaterHeads;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.hydrology.water.ConfluenceGrader;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondKind;
import art.arcane.iris.generation.hydrology.water.PondMask;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachShaper;
import art.arcane.iris.generation.hydrology.water.ReachStyle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class WaterInvariants {
    static final int EDGE_MARGIN = 32;
    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 1, -1};

    private WaterInvariants() {
    }

    static List<Long> detachedOutlets(PlannedScene scene) {
        Map<Long, Reach> reaches = byId(scene.reaches());
        int seaLevel = scene.settings().seaLevel();
        List<Long> detached = new ArrayList<>();
        for (Pond pond : checkedPonds(scene)) {
            Reach outlet = reaches.get(pond.outletReachId());
            if (!floodsIntoOutlet(scene, pond, outlet, WaterHeads.snap(pond.level(), seaLevel))) {
                detached.add(outlet.id());
            }
        }
        return detached;
    }

    static List<Pond> checkedPonds(PlannedScene scene) {
        Map<Long, Reach> reaches = byId(scene.reaches());
        List<Pond> checked = new ArrayList<>();
        for (Pond pond : scene.ponds()) {
            Reach outlet = pond.kind() == PondKind.LAKE ? null : reaches.get(pond.outletReachId());
            if (outlet != null && !nearEdge(scene, outletBox(pond.mask(), outlet))) {
                checked.add(pond);
            }
        }
        return checked;
    }

    static List<Long> confluenceTongues(PlannedScene scene) {
        int seaLevel = scene.settings().seaLevel();
        List<Long> tongues = new ArrayList<>();
        for (Junction junction : junctions(scene)) {
            if (perched(scene, junction, seaLevel, false)) {
                tongues.add(junction.feeder().id());
            }
        }
        return tongues;
    }

    static List<Long> heldLipRiffles(PlannedScene scene) {
        int seaLevel = scene.settings().seaLevel();
        List<Long> riffles = new ArrayList<>();
        for (Junction junction : junctions(scene)) {
            if (perched(scene, junction, seaLevel, false) && !perched(scene, junction, seaLevel, true)) {
                riffles.add(junction.feeder().id());
            }
        }
        return riffles;
    }

    static List<Reach> checkedFeeders(PlannedScene scene) {
        List<Reach> checked = new ArrayList<>();
        for (Junction junction : junctions(scene)) {
            checked.add(junction.feeder());
        }
        return checked;
    }

    static List<Long> subSeaAir(PlannedScene scene, int seaLevel) {
        Set<Long> features = new TreeSet<>();
        for (int z = scene.minZ() + EDGE_MARGIN; z <= scene.maxZ() - EDGE_MARGIN; z++) {
            for (int x = scene.minX() + EDGE_MARGIN; x <= scene.maxX() - EDGE_MARGIN; x++) {
                int head = scene.head(x, z);
                if (head != Integer.MIN_VALUE && head < seaLevel && scene.terrain(x, z) < head) {
                    features.add(scene.feature(x, z));
                }
            }
        }
        return List.copyOf(features);
    }

    static double along(Reach reach, int segment, double x, double z) {
        double ax = reach.xs()[segment];
        double az = reach.zs()[segment];
        double dx = reach.xs()[segment + 1] - ax;
        double dz = reach.zs()[segment + 1] - az;
        double length = dx * dx + dz * dz;
        return length == 0D ? 0D : Math.clamp(((x - ax) * dx + (z - az) * dz) / length, 0D, 1D);
    }

    static double offset(Reach reach, int segment, double t, double x, double z) {
        double px = reach.xs()[segment] + t * (reach.xs()[segment + 1] - reach.xs()[segment]);
        double pz = reach.zs()[segment] + t * (reach.zs()[segment + 1] - reach.zs()[segment]);
        return Math.hypot(x - px, z - pz);
    }

    static double width(Reach reach, int segment, double t) {
        return reach.halfWidth()[segment] + t * (reach.halfWidth()[segment + 1] - reach.halfWidth()[segment]);
    }

    private static boolean floodsIntoOutlet(PlannedScene scene, Pond pond, Reach outlet, int head) {
        int[] box = maskBox(pond.mask());
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int z = box[1]; z <= box[3]; z++) {
            for (int x = box[0]; x <= box[2]; x++) {
                if (scene.role(x, z) == WaterRole.POND && scene.feature(x, z) == pond.id() && scene.head(x, z) == head) {
                    seen.add(pack(x, z));
                    queue.add(new int[]{x, z});
                }
            }
        }
        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            for (int side = 0; side < 4; side++) {
                int x = cell[0] + DX[side];
                int z = cell[1] + DZ[side];
                long feature = scene.feature(x, z);
                boolean outletWater = feature == outlet.id() || feature != pond.id() && covers(outlet, x, z);
                if ((feature != pond.id() && !outletWater) || scene.head(x, z) != head || !seen.add(pack(x, z))) {
                    continue;
                }
                if (outletWater) {
                    return true;
                }
                queue.add(new int[]{x, z});
            }
        }
        return false;
    }

    private static List<Junction> junctions(PlannedScene scene) {
        Map<Long, Reach> reaches = byId(scene.reaches());
        Map<Long, List<Reach>> feeders = feeders(scene.reaches(), reaches);
        Map<Long, List<PondMask>> lips = lips(scene.ponds());
        int seaLevel = scene.settings().seaLevel();
        List<Junction> junctions = new ArrayList<>();
        for (Reach feeder : scene.reaches()) {
            Reach downstream = feeder.downstreamId() == 0L ? null : reaches.get(feeder.downstreamId());
            if (downstream == null) {
                continue;
            }
            int level = Math.min(feeder.grade()[feeder.grade().length - 1], downstream.grade()[0]);
            boolean[] pool = pool(feeder, receiving(feeder, downstream, feeders.get(downstream.id())), level, feeders.containsKey(feeder.id()));
            int[] box = interior(scene, poolBox(feeder, pool));
            if (box != null) {
                boolean[] held = held(feeder, lips.getOrDefault(feeder.id(), List.of()));
                junctions.add(new Junction(feeder, WaterHeads.snap(level, seaLevel), pool, held, box));
            }
        }
        return junctions;
    }

    private static boolean perched(PlannedScene scene, Junction junction, int seaLevel, boolean riffles) {
        Reach feeder = junction.feeder();
        boolean endsAtTheJunction = WaterHeads.snap(feeder.grade()[feeder.grade().length - 1], seaLevel) <= junction.head();
        int[] box = junction.box();
        for (int z = box[1]; z <= box[3]; z++) {
            for (int x = box[0]; x <= box[2]; x++) {
                if (scene.feature(x, z) != feeder.id() || scene.head(x, z) <= junction.head()) {
                    continue;
                }
                int point = coveringPoint(feeder, x, z);
                if (point < 0 || !junction.pool()[point]) {
                    continue;
                }
                if (!endsAtTheJunction || !junction.held()[point] || !fallsToTheJunction(feeder, point, seaLevel, riffles)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean[] pool(Reach feeder, List<Reach> receiving, int level, boolean fedStart) {
        int last = feeder.xs().length - 1;
        boolean[] pool = new boolean[last + 1];
        pool[last] = true;
        double arc = 0D;
        for (int point = last - 1; point >= (fedStart ? 1 : 0); point--) {
            arc += Math.hypot(feeder.xs()[point + 1] - feeder.xs()[point], feeder.zs()[point + 1] - feeder.zs()[point]);
            double edge = edgeDistance(receiving, feeder.xs()[point], feeder.zs()[point]);
            if (arc > ConfluenceGrader.POOL_CAP || edge > feeder.halfWidth()[point] + ConfluenceGrader.POOL_MARGIN
                    || edge > 0D && feeder.grade()[point] - level > ConfluenceGrader.MARGIN_LOWERING) {
                return pool;
            }
            pool[point] = true;
        }
        return pool;
    }

    private static Map<Long, List<PondMask>> lips(List<Pond> ponds) {
        Map<Long, List<PondMask>> lips = new HashMap<>();
        for (Pond pond : ponds) {
            if (pond.kind() != PondKind.LAKE && pond.outletReachId() != 0L) {
                lips.computeIfAbsent(pond.outletReachId(), ignored -> new ArrayList<>()).add(pond.mask());
            }
        }
        return lips;
    }

    private static boolean[] held(Reach outlet, List<PondMask> lips) {
        boolean[] held = new boolean[outlet.xs().length];
        boolean lip = !lips.isEmpty();
        for (int point = 0; point < held.length; point++) {
            lip = lip && (point == 0 || nearLip(lips, outlet.xs()[point], outlet.zs()[point], outlet.halfWidth()[point]));
            held[point] = lip || outlet.style()[point] == ReachStyle.POND_OUTLET;
        }
        return held;
    }

    private static boolean nearLip(List<PondMask> lips, float x, float z, float halfWidth) {
        for (PondMask lip : lips) {
            if (ReachShaper.nearLip(lip, x, z, halfWidth)) {
                return true;
            }
        }
        return false;
    }

    private static boolean fallsToTheJunction(Reach feeder, int point, int seaLevel, boolean riffles) {
        for (int next = point + 1; next < feeder.grade().length; next++) {
            boolean step = WaterHeads.snap(feeder.grade()[next], seaLevel) < WaterHeads.snap(feeder.grade()[next - 1], seaLevel);
            ReachStyle style = feeder.style()[next];
            boolean riffle = riffles && feeder.grade()[next - 1] - feeder.grade()[next] == 1;
            if (step && style != ReachStyle.CASCADE && style != ReachStyle.WATERFALL && !riffle) {
                return false;
            }
        }
        return true;
    }

    private static Map<Long, List<Reach>> feeders(List<Reach> reaches, Map<Long, Reach> byId) {
        Map<Long, List<Reach>> feeders = new HashMap<>();
        for (Reach reach : reaches) {
            if (byId.containsKey(reach.downstreamId())) {
                feeders.computeIfAbsent(reach.downstreamId(), ignored -> new ArrayList<>()).add(reach);
            }
        }
        return feeders;
    }

    private static List<Reach> receiving(Reach feeder, Reach downstream, List<Reach> siblings) {
        List<Reach> receiving = new ArrayList<>(siblings.size());
        receiving.add(downstream);
        for (Reach sibling : siblings) {
            if (sibling.id() != feeder.id()) {
                receiving.add(sibling);
            }
        }
        return receiving;
    }

    private static int[] poolBox(Reach feeder, boolean[] pool) {
        int[] box = null;
        for (int point = 0; point < pool.length; point++) {
            if (!pool[point]) {
                continue;
            }
            int from = Math.max(0, point - 1);
            int to = Math.min(pool.length - 1, point + 1);
            float extent = 0f;
            for (int near = from; near <= to; near++) {
                extent = Math.max(extent, feeder.halfWidth()[near] + 1f);
            }
            for (int near = from; near <= to; near++) {
                int[] around = {(int) Math.floor(feeder.xs()[near] - extent), (int) Math.floor(feeder.zs()[near] - extent),
                        (int) Math.ceil(feeder.xs()[near] + extent), (int) Math.ceil(feeder.zs()[near] + extent)};
                box = box == null ? around : new int[]{Math.min(box[0], around[0]), Math.min(box[1], around[1]),
                        Math.max(box[2], around[2]), Math.max(box[3], around[3])};
            }
        }
        return box;
    }

    private static int[] interior(PlannedScene scene, int[] box) {
        int[] clipped = {Math.max(box[0], scene.minX() + EDGE_MARGIN), Math.max(box[1], scene.minZ() + EDGE_MARGIN),
                Math.min(box[2], scene.maxX() - EDGE_MARGIN), Math.min(box[3], scene.maxZ() - EDGE_MARGIN)};
        return clipped[0] > clipped[2] || clipped[1] > clipped[3] ? null : clipped;
    }

    private static int[] outletBox(PondMask mask, Reach outlet) {
        int[] box = maskBox(mask);
        float extent = outlet.halfWidth()[0] + 1f;
        return new int[]{Math.min(box[0], (int) Math.floor(outlet.xs()[0] - extent)), Math.min(box[1], (int) Math.floor(outlet.zs()[0] - extent)),
                Math.max(box[2], (int) Math.ceil(outlet.xs()[0] + extent)), Math.max(box[3], (int) Math.ceil(outlet.zs()[0] + extent))};
    }

    private static int[] maskBox(PondMask mask) {
        return new int[]{mask.originX() - mask.step(), mask.originZ() - mask.step(),
                mask.originX() + mask.width() * mask.step(), mask.originZ() + mask.depth() * mask.step()};
    }

    private static boolean nearEdge(PlannedScene scene, int[] box) {
        return box[0] < scene.minX() + EDGE_MARGIN || box[1] < scene.minZ() + EDGE_MARGIN
                || box[2] > scene.maxX() - EDGE_MARGIN || box[3] > scene.maxZ() - EDGE_MARGIN;
    }

    private static double edgeDistance(List<Reach> reaches, double x, double z) {
        double best = Double.POSITIVE_INFINITY;
        for (Reach reach : reaches) {
            for (int segment = 0; segment + 1 < reach.xs().length; segment++) {
                double t = along(reach, segment, x, z);
                best = Math.min(best, offset(reach, segment, t, x, z) - width(reach, segment, t));
            }
        }
        return best;
    }

    private static int coveringPoint(Reach reach, int x, int z) {
        double nearest = Double.POSITIVE_INFINITY;
        int point = -1;
        for (int segment = 0; segment + 1 < reach.xs().length; segment++) {
            double t = along(reach, segment, x, z);
            double distance = offset(reach, segment, t, x, z);
            if (distance <= width(reach, segment, t) && distance < nearest) {
                nearest = distance;
                point = t < 0.5D ? segment : segment + 1;
            }
        }
        return point;
    }

    private static boolean covers(Reach reach, int x, int z) {
        for (int segment = 0; segment + 1 < reach.xs().length; segment++) {
            double t = along(reach, segment, x, z);
            if (offset(reach, segment, t, x, z) <= width(reach, segment, t)) {
                return true;
            }
        }
        return false;
    }

    private static Map<Long, Reach> byId(List<Reach> reaches) {
        Map<Long, Reach> byId = new HashMap<>(reaches.size() * 2);
        for (Reach reach : reaches) {
            byId.put(reach.id(), reach);
        }
        return byId;
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private record Junction(Reach feeder, int head, boolean[] pool, boolean[] held, int[] box) {
    }
}
