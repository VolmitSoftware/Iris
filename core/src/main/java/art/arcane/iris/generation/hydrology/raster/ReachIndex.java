package art.arcane.iris.generation.hydrology.raster;

import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondMask;
import art.arcane.iris.generation.hydrology.water.Reach;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ReachIndex {
    private static final int BUCKET = 16;
    private static final int CHUNK = 16;

    private final List<Reach> reaches;
    private final List<Pond> ponds;
    private final float[][] corridors;
    private final int[][] footprints;
    private final int originX;
    private final int originZ;
    private final int width;
    private final int depth;
    private final Buckets reachBuckets;
    private final Buckets pondBuckets;
    private final Map<Long, List<Reach>> feeders;

    private ReachIndex(List<Reach> reaches, List<Pond> ponds) {
        this.reaches = List.copyOf(reaches);
        this.ponds = List.copyOf(ponds);
        this.corridors = new float[this.reaches.size()][];
        for (int index = 0; index < corridors.length; index++) {
            corridors[index] = corridor(this.reaches.get(index));
        }
        this.footprints = new int[this.ponds.size()][];
        for (int index = 0; index < footprints.length; index++) {
            footprints[index] = footprint(this.ponds.get(index).mask());
        }
        int[] bounds = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int index = 0; index < corridors.length; index++) {
            visitReach(index, (item, minX, minZ, maxX, maxZ) -> include(bounds, minX, minZ, maxX, maxZ));
        }
        for (int index = 0; index < footprints.length; index++) {
            visitPond(index, (item, minX, minZ, maxX, maxZ) -> include(bounds, minX, minZ, maxX, maxZ));
        }
        boolean empty = bounds[0] > bounds[2];
        this.originX = empty ? 0 : bounds[0];
        this.originZ = empty ? 0 : bounds[1];
        this.width = empty ? 0 : Math.addExact(Math.subtractExact(bounds[2], bounds[0]), 1);
        this.depth = empty ? 0 : Math.addExact(Math.subtractExact(bounds[3], bounds[1]), 1);
        this.reachBuckets = buckets(corridors.length, this::visitReach);
        this.pondBuckets = buckets(footprints.length, this::visitPond);
        this.feeders = feeders(this.reaches);
    }

    public static ReachIndex build(List<Reach> reaches, List<Pond> ponds) {
        return new ReachIndex(reaches, ponds);
    }

    public void candidates(int chunkX, int chunkZ, int halo, List<Reach> outReaches, List<Pond> outPonds) {
        if (halo < 0) {
            throw new IllegalArgumentException("Negative candidate halo " + halo);
        }
        int minBlockX = Math.subtractExact(Math.multiplyExact(chunkX, CHUNK), halo);
        int minBlockZ = Math.subtractExact(Math.multiplyExact(chunkZ, CHUNK), halo);
        int endBlockX = Math.addExact(minBlockX, CHUNK + 2 * halo);
        int endBlockZ = Math.addExact(minBlockZ, CHUNK + 2 * halo);
        float minX = chunkX * 16f - halo;
        float maxX = chunkX * 16f + 16f + halo;
        float minZ = chunkZ * 16f - halo;
        float maxZ = chunkZ * 16f + 16f + halo;
        BitSet found = gather(reachBuckets, minBlockX, minBlockZ, endBlockX, endBlockZ, reaches.size());
        for (int index = found.nextSetBit(0); index >= 0; index = found.nextSetBit(index + 1)) {
            if (meets(corridors[index], minX, minZ, maxX, maxZ)) {
                outReaches.add(reaches.get(index));
            }
        }
        BitSet wet = gather(pondBuckets, minBlockX, minBlockZ, endBlockX - 1, endBlockZ - 1, ponds.size());
        for (int index = wet.nextSetBit(0); index >= 0; index = wet.nextSetBit(index + 1)) {
            int[] footprint = footprints[index];
            if (footprint[0] < endBlockX && footprint[2] > minBlockX && footprint[1] < endBlockZ && footprint[3] > minBlockZ) {
                outPonds.add(ponds.get(index));
            }
        }
    }

    public List<Reach> feeders(long downstreamId) {
        return feeders.getOrDefault(downstreamId, List.of());
    }

    public static ReachHit nearest(List<Reach> candidates, double x, double z) {
        int segments = 0;
        for (Reach reach : candidates) {
            segments += reach.xs().length - 1;
        }
        ReachSegments table = new ReachSegments(segments);
        for (Reach reach : candidates) {
            table.addAll(reach);
        }
        ReachCursor cursor = new ReachCursor();
        table.nearest(x, z, ReachSegments.Scope.NEAREST, cursor);
        return cursor.hit();
    }

    private static Map<Long, List<Reach>> feeders(List<Reach> reaches) {
        Map<Long, List<Reach>> grouped = new HashMap<>();
        for (Reach reach : reaches) {
            grouped.computeIfAbsent(reach.downstreamId(), ignored -> new ArrayList<>()).add(reach);
        }
        Map<Long, List<Reach>> frozen = new HashMap<>();
        for (Map.Entry<Long, List<Reach>> entry : grouped.entrySet()) {
            frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(frozen);
    }

    private static float[] corridor(Reach reach) {
        int segments = reach.xs().length - 1;
        float[] boxes = new float[segments * 4];
        int bank = reach.tier().bankBand();
        for (int segment = 0; segment < segments; segment++) {
            float x0 = reach.xs()[segment];
            float x1 = reach.xs()[segment + 1];
            float z0 = reach.zs()[segment];
            float z1 = reach.zs()[segment + 1];
            float radius = Math.max(reach.halfWidth()[segment], reach.halfWidth()[segment + 1]) + bank;
            boxes[segment * 4] = Math.min(x0, x1) - radius;
            boxes[segment * 4 + 1] = Math.min(z0, z1) - radius;
            boxes[segment * 4 + 2] = Math.max(x0, x1) + radius;
            boxes[segment * 4 + 3] = Math.max(z0, z1) + radius;
        }
        return boxes;
    }

    private static int[] footprint(PondMask mask) {
        int half = mask.step() >> 1;
        int minX = mask.originX() - half;
        int minZ = mask.originZ() - half;
        return new int[]{minX, minZ, minX + mask.width() * mask.step(), minZ + mask.depth() * mask.step()};
    }

    private static boolean meets(float[] boxes, float minX, float minZ, float maxX, float maxZ) {
        for (int box = 0; box < boxes.length; box += 4) {
            if (boxes[box] <= maxX && boxes[box + 2] >= minX && boxes[box + 1] <= maxZ && boxes[box + 3] >= minZ) {
                return true;
            }
        }
        return false;
    }

    private void visitReach(int index, Area area) {
        float[] boxes = corridors[index];
        for (int box = 0; box < boxes.length; box += 4) {
            area.accept(index, bucket(boxes[box]), bucket(boxes[box + 1]), bucket(boxes[box + 2]), bucket(boxes[box + 3]));
        }
    }

    private void visitPond(int index, Area area) {
        int[] footprint = footprints[index];
        if (footprint[2] > footprint[0] && footprint[3] > footprint[1]) {
            area.accept(index, bucket(footprint[0]), bucket(footprint[1]), bucket(footprint[2] - 1), bucket(footprint[3] - 1));
        }
    }

    private static int bucket(float block) {
        return (int) Math.floor(block / BUCKET);
    }

    private static int bucket(int block) {
        return Math.floorDiv(block, BUCKET);
    }

    private static void include(int[] bounds, int minX, int minZ, int maxX, int maxZ) {
        bounds[0] = Math.min(bounds[0], minX);
        bounds[1] = Math.min(bounds[1], minZ);
        bounds[2] = Math.max(bounds[2], maxX);
        bounds[3] = Math.max(bounds[3], maxZ);
    }

    private Buckets buckets(int items, Visitor visitor) {
        int cells = Math.multiplyExact(width, depth);
        int[] counts = new int[cells + 1];
        int[] last = new int[cells];
        Arrays.fill(last, -1);
        for (int item = 0; item < items; item++) {
            visitor.visit(item, (index, minX, minZ, maxX, maxZ) -> {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int x = minX; x <= maxX; x++) {
                        int cell = (z - originZ) * width + (x - originX);
                        if (last[cell] != index) {
                            last[cell] = index;
                            counts[cell + 1]++;
                        }
                    }
                }
            });
        }
        for (int cell = 0; cell < cells; cell++) {
            counts[cell + 1] = Math.addExact(counts[cell + 1], counts[cell]);
        }
        int[] entries = new int[counts[cells]];
        int[] cursor = Arrays.copyOf(counts, cells);
        Arrays.fill(last, -1);
        for (int item = 0; item < items; item++) {
            visitor.visit(item, (index, minX, minZ, maxX, maxZ) -> {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int x = minX; x <= maxX; x++) {
                        int cell = (z - originZ) * width + (x - originX);
                        if (last[cell] != index) {
                            last[cell] = index;
                            entries[cursor[cell]++] = index;
                        }
                    }
                }
            });
        }
        return new Buckets(counts, entries);
    }

    private BitSet gather(Buckets buckets, int minBlockX, int minBlockZ, int maxBlockX, int maxBlockZ, int size) {
        BitSet found = new BitSet(size);
        if (width == 0) {
            return found;
        }
        int minX = Math.max(bucket(minBlockX) - originX, 0);
        int minZ = Math.max(bucket(minBlockZ) - originZ, 0);
        int maxX = Math.min(bucket(maxBlockX) - originX, width - 1);
        int maxZ = Math.min(bucket(maxBlockZ) - originZ, depth - 1);
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                int cell = z * width + x;
                for (int entry = buckets.offsets()[cell]; entry < buckets.offsets()[cell + 1]; entry++) {
                    found.set(buckets.entries()[entry]);
                }
            }
        }
        return found;
    }

    private interface Area {
        void accept(int item, int minX, int minZ, int maxX, int maxZ);
    }

    private interface Visitor {
        void visit(int item, Area area);
    }

    private record Buckets(int[] offsets, int[] entries) {
    }
}
