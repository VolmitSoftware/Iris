package art.arcane.iris.probe;

import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.plan.WindowPlan;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.raster.WaterHeads;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.hydrology.water.Pond;
import art.arcane.iris.generation.hydrology.water.PondKind;
import art.arcane.iris.generation.hydrology.water.Reach;
import art.arcane.iris.generation.hydrology.water.ReachStyle;
import art.arcane.iris.generation.hydrology.raster.NaturalHeights;
import art.arcane.volmlib.nativelib.terrain.structure.StructureStreamLowering;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

final class HydrologyRasterMetrics {
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final HydrologySettings settings;
    private final HydrologyPlanMetrics plans;
    private final Rasters rasters;
    private final NaturalHeights naturalHeight;
    private final HydrologyWindows.WindowRange range;
    private static final int LINK_SEARCH = 32;
    private final Consumer<String> examples;

    @FunctionalInterface
    interface Rasters {
        ChunkWaterRaster get(int chunkX, int chunkZ);
    }

    HydrologyRasterMetrics(HydrologySettings settings, HydrologyPlanMetrics plans, Rasters rasters, NaturalHeights naturalHeight,
                           HydrologyWindows.WindowRange range, Consumer<String> examples) {
        this.settings = settings;
        this.plans = plans;
        this.rasters = rasters;
        this.naturalHeight = naturalHeight;
        this.range = range;
        this.examples = examples;
    }

    long rising(WindowKey key, WindowPlan plan) {
        long rising = 0L;
        for (Reach reach : plan.reaches()) {
            int[] heads = heads(reach);
            int previous = HydrologyVoxels.NO_WATER;
            for (int point = 0; point < heads.length; point++) {
                int head = heads[point];
                if (head == HydrologyVoxels.NO_WATER) {
                    continue;
                }
                if (previous != HydrologyVoxels.NO_WATER && head > previous) {
                    rising++;
                    examples.accept("rise along reach " + reach.id() + " from " + previous + " to " + head);
                }
                if (reach.style()[point] == ReachStyle.MOUTH && head < settings.seaLevel()) {
                    rising++;
                    examples.accept("rise from the sea at " + settings.seaLevel() + " into the mouth of reach " + reach.id() + " at " + head);
                }
                previous = head;
            }
            if (previous == HydrologyVoxels.NO_WATER) {
                continue;
            }
            Reach downstream = plans.reaches().get(reach.downstreamId());
            if (downstream != null) {
                int first = first(heads(downstream));
                if (first != HydrologyVoxels.NO_WATER && first > previous) {
                    rising++;
                    examples.accept("rise from reach " + reach.id() + " at " + previous + " into reach " + downstream.id() + " at " + first);
                }
            }
            Pond end = plans.endPond(reach);
            if (end != null && head(end) > previous) {
                rising++;
                examples.accept("rise from reach " + reach.id() + " at " + previous + " into pond " + end.id() + " at " + head(end));
            }
        }
        for (Pond pond : plan.ponds()) {
            Reach outlet = plans.reaches().get(pond.outletReachId());
            if (outlet != null) {
                int first = first(heads(outlet));
                if (first != HydrologyVoxels.NO_WATER && first > head(pond)) {
                    rising++;
                    examples.accept("rise from pond " + pond.id() + " at " + head(pond) + " into outlet " + outlet.id() + " at " + first);
                }
            }
        }
        return rising;
    }

    long unlinkedWater(WindowPlan plan) {
        long unlinked = 0L;
        for (Reach reach : plan.reaches()) {
            int lastX = 0;
            int lastZ = 0;
            boolean wet = false;
            for (int point = 0; point + 1 < reach.xs().length; point++) {
                float fromX = reach.xs()[point];
                float fromZ = reach.zs()[point];
                float toX = reach.xs()[point + 1];
                float toZ = reach.zs()[point + 1];
                int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(toX - fromX), Math.abs(toZ - fromZ))));
                for (int step = 0; step <= steps; step++) {
                    int x = (int) Math.floor(fromX + (toX - fromX) * step / steps);
                    int z = (int) Math.floor(fromZ + (toZ - fromZ) * step / steps);
                    if (!wetInRange(x, z)) {
                        continue;
                    }
                    if (wet && !linkedByWater(lastX, lastZ, x, z)) {
                        unlinked++;
                        examples.accept("water gap along reach " + reach.id() + " from " + lastX + "," + lastZ + " to " + x + "," + z);
                    }
                    lastX = x;
                    lastZ = z;
                    wet = true;
                }
            }
        }
        return unlinked;
    }

    private boolean wetInRange(int x, int z) {
        return range.containsBlock(x, z) && rasters.get(x >> 4, z >> 4).role(x & 15, z & 15).wet();
    }

    private boolean linkedByWater(int fromX, int fromZ, int toX, int toZ) {
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> open = new ArrayDeque<>();
        open.add(new int[]{fromX, fromZ});
        seen.add(HydrologyVoxels.columnKey(fromX, fromZ));
        int limit = StructureStreamLowering.LINK + LINK_SEARCH + Math.max(Math.abs(toX - fromX), Math.abs(toZ - fromZ));
        while (!open.isEmpty()) {
            int[] cell = open.poll();
            if (Math.max(Math.abs(cell[0] - toX), Math.abs(cell[1] - toZ)) <= StructureStreamLowering.LINK) {
                return true;
            }
            for (int dz = -StructureStreamLowering.LINK; dz <= StructureStreamLowering.LINK; dz++) {
                for (int dx = -StructureStreamLowering.LINK; dx <= StructureStreamLowering.LINK; dx++) {
                    int x = cell[0] + dx;
                    int z = cell[1] + dz;
                    if (Math.max(Math.abs(x - fromX), Math.abs(z - fromZ)) <= limit && wetInRange(x, z)
                            && seen.add(HydrologyVoxels.columnKey(x, z))) {
                        open.add(new int[]{x, z});
                    }
                }
            }
        }
        return false;
    }

    long chunkSeams(WindowKey key) {
        long mismatches = 0L;
        int minX = key.wx() * WindowKey.SIZE;
        int minZ = key.wz() * WindowKey.SIZE;
        for (int z = minZ; z < minZ + WindowKey.SIZE; z++) {
            for (int x = minX; x < minX + WindowKey.SIZE; x++) {
                if ((x & 15) == 15 && range.containsBlock(x + 1, z) && seamMismatch(x, z, x + 1, z)) {
                    mismatches++;
                }
                if ((z & 15) == 15 && range.containsBlock(x, z + 1) && seamMismatch(x, z, x, z + 1)) {
                    mismatches++;
                }
            }
        }
        return mismatches;
    }

    long bermOverCap(WindowKey key) {
        long overCap = 0L;
        int maxFill = settings.deformation().maxFill();
        int minX = key.wx() * WindowKey.SIZE;
        int minZ = key.wz() * WindowKey.SIZE;
        for (int z = minZ; z < minZ + WindowKey.SIZE; z++) {
            for (int x = minX; x < minX + WindowKey.SIZE; x++) {
                ChunkWaterRaster raster = rasters.get(x >> 4, z >> 4);
                WaterRole role = raster.role(x & 15, z & 15);
                if (raster.empty() || role == WaterRole.NONE || role == WaterRole.DAM || role.wet()) {
                    continue;
                }
                int terrain = raster.terrainHeight(x & 15, z & 15);
                int natural = (int) Math.round(naturalHeight.height(x, z));
                if (terrain > natural + maxFill && !heldOutletWall(x, z, terrain)) {
                    overCap++;
                    examples.accept("berm raised " + x + "," + z + " from " + natural + " to " + terrain);
                }
            }
        }
        return overCap;
    }

    private boolean heldOutletWall(int x, int z, int terrain) {
        for (int[] side : SIDES) {
            int nx = x + side[0];
            int nz = z + side[1];
            if (!range.containsBlock(nx, nz)) {
                continue;
            }
            ChunkWaterRaster raster = rasters.get(nx >> 4, nz >> 4);
            if (!raster.role(nx & 15, nz & 15).wet() || raster.waterHead(nx & 15, nz & 15) != terrain) {
                continue;
            }
            Pond pond = plans.outletPond(raster.featureId(nx & 15, nz & 15));
            if (pond != null && pond.kind() != PondKind.LAKE && head(pond) == terrain) {
                return true;
            }
        }
        return false;
    }

    private int head(Pond pond) {
        return WaterHeads.snap(pond.level(), settings.seaLevel());
    }

    WaterRole role(int x, int z) {
        return rasters.get(x >> 4, z >> 4).role(x & 15, z & 15);
    }

    int terrain(int x, int z) {
        ChunkWaterRaster raster = rasters.get(x >> 4, z >> 4);
        return raster.empty() ? (int) Math.round(naturalHeight.height(x, z)) : raster.terrainHeight(x & 15, z & 15);
    }

    private boolean seamMismatch(int x, int z, int nx, int nz) {
        ChunkWaterRaster near = rasters.get(x >> 4, z >> 4);
        ChunkWaterRaster far = rasters.get(nx >> 4, nz >> 4);
        WaterRole nearRole = near.role(x & 15, z & 15);
        WaterRole farRole = far.role(nx & 15, nz & 15);
        int nearHead = near.waterHead(x & 15, z & 15);
        int farHead = far.waterHead(nx & 15, nz & 15);
        boolean mismatch;
        String what;
        if (nearRole.wet() && farRole.wet()) {
            mismatch = nearRole == WaterRole.POND && farRole == WaterRole.POND && near.featureId(x & 15, z & 15) == far.featureId(nx & 15, nz & 15)
                    && nearHead != farHead;
            what = "one pond at two levels";
        } else if (nearRole.wet()) {
            mismatch = unbermed(nearHead, nx, nz);
            what = "dry column left below its berm";
        } else if (farRole.wet()) {
            mismatch = unbermed(farHead, x, z);
            what = "dry column left below its berm";
        } else {
            return false;
        }
        if (mismatch) {
            examples.accept("seam " + what + " across chunks at " + x + "," + z + " / " + nx + "," + nz);
        }
        return mismatch;
    }

    private boolean unbermed(int head, int x, int z) {
        int ground = terrain(x, z);
        return ground < head && !(head <= settings.seaLevel() && ground < settings.seaLevel());
    }

    private int[] heads(Reach reach) {
        int[] heads = new int[reach.xs().length];
        for (int point = 0; point < heads.length; point++) {
            int x = (int) Math.floor(reach.xs()[point]);
            int z = (int) Math.floor(reach.zs()[point]);
            heads[point] = HydrologyVoxels.NO_WATER;
            if (range.containsBlock(x, z)) {
                ChunkWaterRaster raster = rasters.get(x >> 4, z >> 4);
                if (raster.role(x & 15, z & 15).wet() && raster.featureId(x & 15, z & 15) == reach.id()) {
                    heads[point] = raster.waterHead(x & 15, z & 15);
                }
            }
        }
        return heads;
    }

    private static int first(int[] heads) {
        for (int head : heads) {
            if (head != HydrologyVoxels.NO_WATER) {
                return head;
            }
        }
        return HydrologyVoxels.NO_WATER;
    }
}
