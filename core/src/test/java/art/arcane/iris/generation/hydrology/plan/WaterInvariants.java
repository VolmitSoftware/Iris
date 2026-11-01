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
            if (outlet != null && !floodsIntoOutlet(scene, pond, outlet, WaterHeads.snap(pond.level(), seaLevel))) {
                detached.add(outlet.id());
            }
        }
        return detached;
    }

    static List<Long> confluenceTongues(PlannedScene scene) {
        Map<Long, Reach> reaches = byId(scene.reaches());
        int seaLevel = scene.settings().seaLevel();
        List<Long> tongues = new ArrayList<>();
        for (Reach feeder : scene.reaches()) {
            Reach receiving = feeder.downstreamId() == 0L ? null : reaches.get(feeder.downstreamId());
            if (receiving != null && perchedInside(scene, feeder, receiving, seaLevel)) {
                tongues.add(feeder.id());
            }
        }
        return tongues;
    }

    static List<Long> subSeaAir(PlannedScene scene, int seaLevel) {
        Set<Long> features = new TreeSet<>();
        for (int z = scene.minZ(); z <= scene.maxZ(); z++) {
            for (int x = scene.minX(); x <= scene.maxX(); x++) {
                int head = scene.head(x, z);
                if (head != Integer.MIN_VALUE && head < seaLevel && scene.terrain(x, z) < head) {
                    features.add(scene.feature(x, z));
                }
            }
        }
        return List.copyOf(features);
    }

    private static boolean floodsIntoOutlet(PlannedScene scene, Pond pond, Reach outlet, int head) {
        PondMask mask = pond.mask();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        int maxX = mask.originX() + mask.width() * mask.step();
        int maxZ = mask.originZ() + mask.depth() * mask.step();
        for (int z = mask.originZ() - mask.step(); z <= maxZ; z++) {
            for (int x = mask.originX() - mask.step(); x <= maxX; x++) {
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
                if ((feature != pond.id() && feature != outlet.id()) || scene.head(x, z) != head || !seen.add(pack(x, z))) {
                    continue;
                }
                if (feature == outlet.id()) {
                    return true;
                }
                queue.add(new int[]{x, z});
            }
        }
        return false;
    }

    private static boolean perchedInside(PlannedScene scene, Reach feeder, Reach receiving, int seaLevel) {
        float[] xs = feeder.xs();
        float[] zs = feeder.zs();
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int point = 0; point < xs.length; point++) {
            float extent = feeder.halfWidth()[point] + TONGUE_MARGIN;
            if (edgeDistance(receiving, xs[point], zs[point]) <= extent) {
                minX = Math.min(minX, (int) Math.floor(xs[point] - extent));
                minZ = Math.min(minZ, (int) Math.floor(zs[point] - extent));
                maxX = Math.max(maxX, (int) Math.ceil(xs[point] + extent));
                maxZ = Math.max(maxZ, (int) Math.ceil(zs[point] + extent));
            }
        }
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
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

    private static double edgeDistance(Reach reach, double x, double z) {
        double best = Double.POSITIVE_INFINITY;
        for (int segment = 0; segment + 1 < reach.xs().length; segment++) {
            double t = along(reach, segment, x, z);
            best = Math.min(best, offset(reach, segment, t, x, z) - width(reach, segment, t));
        }
        return best;
    }

    private static int coveringGrade(Reach reach, int x, int z) {
        double nearest = Double.POSITIVE_INFINITY;
        int grade = Integer.MIN_VALUE;
        for (int segment = 0; segment + 1 < reach.xs().length; segment++) {
            double t = along(reach, segment, x, z);
            double distance = offset(reach, segment, t, x, z);
            if (distance <= width(reach, segment, t) && distance < nearest) {
                nearest = distance;
                grade = reach.grade()[t < 0.5D ? segment : segment + 1];
            }
        }
        return grade;
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
