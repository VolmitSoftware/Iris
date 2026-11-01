package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.raster.WaterHeads;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondKind;
import art.arcane.iris.generation.hydrology.water.PondMask;
import art.arcane.iris.generation.hydrology.water.Reach;

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
    private static final float TONGUE_MARGIN = 2f;

    private WaterInvariants() {
    }

    static List<Long> detachedOutlets(PlannedScene scene) {
        Map<Long, Reach> reaches = byId(scene.reaches());
        int seaLevel = scene.settings().seaLevel();
        List<Long> detached = new ArrayList<>();
        for (Pond pond : scene.ponds()) {
            Reach outlet = pond.kind() == PondKind.LAKE ? null : reaches.get(pond.outletReachId());
            if (outlet == null || nearEdge(scene, outletBox(pond.mask(), outlet))) {
                continue;
            }
            if (!floodsIntoOutlet(scene, pond, outlet, WaterHeads.snap(pond.level(), seaLevel))) {
                detached.add(outlet.id());
            }
        }
        return detached;
    }

    static List<Long> confluenceTongues(PlannedScene scene) {
        Map<Long, Reach> reaches = byId(scene.reaches());
        Map<Long, List<Reach>> feeders = feeders(scene.reaches(), reaches);
        int seaLevel = scene.settings().seaLevel();
        List<Long> tongues = new ArrayList<>();
        for (Reach feeder : scene.reaches()) {
            Reach downstream = feeder.downstreamId() == 0L ? null : reaches.get(feeder.downstreamId());
            if (downstream == null) {
                continue;
            }
            List<Reach> receiving = receiving(feeder, downstream, feeders.get(downstream.id()));
            int[] box = tongueBox(feeder, receiving);
            if (box != null && !nearEdge(scene, box) && perched(scene, feeder, receiving, box, seaLevel)) {
                tongues.add(feeder.id());
            }
        }
        return tongues;
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

    private static boolean perched(PlannedScene scene, Reach feeder, List<Reach> receiving, int[] box, int seaLevel) {
        for (int z = box[1]; z <= box[3]; z++) {
            for (int x = box[0]; x <= box[2]; x++) {
                if (scene.feature(x, z) != feeder.id()) {
                    continue;
                }
                int receivingGrade = coveringGrade(receiving, x, z);
                if (receivingGrade != Integer.MIN_VALUE && scene.head(x, z) > WaterHeads.snap(receivingGrade, seaLevel)) {
                    return true;
                }
            }
        }
        return false;
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

    private static int[] tongueBox(Reach feeder, List<Reach> receiving) {
        float[] xs = feeder.xs();
        float[] zs = feeder.zs();
        int[] box = null;
        for (int point = 0; point < xs.length; point++) {
            float extent = feeder.halfWidth()[point] + TONGUE_MARGIN;
            if (edgeDistance(receiving, xs[point], zs[point]) > extent) {
                continue;
            }
            int[] around = {(int) Math.floor(xs[point] - extent), (int) Math.floor(zs[point] - extent),
                    (int) Math.ceil(xs[point] + extent), (int) Math.ceil(zs[point] + extent)};
            box = box == null ? around : new int[]{Math.min(box[0], around[0]), Math.min(box[1], around[1]),
                    Math.max(box[2], around[2]), Math.max(box[3], around[3])};
        }
        return box;
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

    private static int coveringGrade(List<Reach> reaches, int x, int z) {
        double nearest = Double.POSITIVE_INFINITY;
        long nearestId = 0L;
        int nearestSegment = 0;
        int grade = Integer.MIN_VALUE;
        for (Reach reach : reaches) {
            for (int segment = 0; segment + 1 < reach.xs().length; segment++) {
                double t = along(reach, segment, x, z);
                double distance = offset(reach, segment, t, x, z);
                if (distance > width(reach, segment, t)) {
                    continue;
                }
                if (grade == Integer.MIN_VALUE || distance < nearest || distance == nearest
                        && (reach.id() < nearestId || reach.id() == nearestId && segment < nearestSegment)) {
                    nearest = distance;
                    nearestId = reach.id();
                    nearestSegment = segment;
                    grade = reach.grade()[t < 0.5D ? segment : segment + 1];
                }
            }
        }
        return grade;
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

    private static double along(Reach reach, int segment, double x, double z) {
        double ax = reach.xs()[segment];
        double az = reach.zs()[segment];
        double dx = reach.xs()[segment + 1] - ax;
        double dz = reach.zs()[segment + 1] - az;
        double length = dx * dx + dz * dz;
        return length == 0D ? 0D : Math.clamp(((x - ax) * dx + (z - az) * dz) / length, 0D, 1D);
    }

    private static double offset(Reach reach, int segment, double t, double x, double z) {
        double px = reach.xs()[segment] + t * (reach.xs()[segment + 1] - reach.xs()[segment]);
        double pz = reach.zs()[segment] + t * (reach.zs()[segment + 1] - reach.zs()[segment]);
        return Math.hypot(x - px, z - pz);
    }

    private static double width(Reach reach, int segment, double t) {
        return reach.halfWidth()[segment] + t * (reach.halfWidth()[segment + 1] - reach.halfWidth()[segment]);
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
}
