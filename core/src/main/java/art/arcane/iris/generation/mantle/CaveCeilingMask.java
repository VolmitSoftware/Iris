package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.underground.SealedPassage;
import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;

import java.util.Arrays;
import java.util.List;

public final class CaveCeilingMask {
    static final int CONTAINMENT = 2;
    static final int ROOF = 2;
    static final int UNMASKED = Integer.MAX_VALUE;
    private static final int CHUNK = 16;
    public static final int FIELD = CHUNK + CONTAINMENT * 2;

    private CaveCeilingMask() {
    }

    public static boolean fill(int chunkX, int chunkZ, ChunkRasters rasters, int[] field, int[] ceilings) {
        ChunkWaterRaster own = ready(rasters.raster(chunkX, chunkZ), chunkX, chunkZ);
        if (own.empty()) {
            return false;
        }
        ChunkWaterRaster[] around = new ChunkWaterRaster[9];
        around[4] = own;
        int originX = (chunkX << 4) - CONTAINMENT;
        int originZ = (chunkZ << 4) - CONTAINMENT;
        for (int fz = 0; fz < FIELD; fz++) {
            int z = originZ + fz;
            int dz = (z >> 4) - chunkZ;
            for (int fx = 0; fx < FIELD; fx++) {
                int x = originX + fx;
                int slot = (dz + 1) * 3 + (x >> 4) - chunkX + 1;
                ChunkWaterRaster raster = around[slot];
                if (raster == null) {
                    raster = ready(rasters.raster(x >> 4, z >> 4), x >> 4, z >> 4);
                    around[slot] = raster;
                }
                int ceiling = raster.caveCeiling(x & 15, z & 15);
                field[fz * FIELD + fx] = ceiling == UNMASKED ? UNMASKED : ceiling - ROOF;
            }
        }
        boolean masked = false;
        for (int lz = 0; lz < CHUNK; lz++) {
            for (int lx = 0; lx < CHUNK; lx++) {
                int ceiling = UNMASKED;
                for (int fz = lz; fz <= lz + CONTAINMENT * 2; fz++) {
                    for (int fx = lx; fx <= lx + CONTAINMENT * 2; fx++) {
                        ceiling = Math.min(ceiling, field[fz * FIELD + fx]);
                    }
                }
                ceilings[PowerOfTwoCoordinates.packLocal16(lx, lz)] = ceiling;
                masked |= ceiling != UNMASKED;
            }
        }
        return masked;
    }

    public static long[] headroom(int chunkX, int chunkZ, HydrologyRuntime runtime, long[] bands) {
        List<SealedPassage> passages = runtime.passages(chunkX, chunkZ);
        if (passages.isEmpty()) {
            return null;
        }
        return fillHeadroom(chunkX, chunkZ, passages, bands) ? bands : null;
    }

    static boolean fillHeadroom(int chunkX, int chunkZ, List<SealedPassage> passages, long[] bands) {
        if (passages.isEmpty()) {
            return false;
        }
        int originX = (chunkX << 4) - CONTAINMENT;
        int originZ = (chunkZ << 4) - CONTAINMENT;
        int[] bottoms = new int[FIELD * FIELD];
        int[] tops = new int[FIELD * FIELD];
        Arrays.fill(bottoms, Integer.MAX_VALUE);
        Arrays.fill(tops, Integer.MIN_VALUE);
        int[] column = new int[2];
        boolean roofed = false;
        for (SealedPassage passage : passages) {
            int[] bounds = passage.bounds();
            int fromX = Math.max(originX, bounds[0]);
            int toX = Math.min(originX + FIELD - 1, bounds[2]);
            int toZ = Math.min(originZ + FIELD - 1, bounds[3]);
            for (int z = Math.max(originZ, bounds[1]); z <= toZ; z++) {
                for (int x = fromX; x <= toX; x++) {
                    if (!passage.column(x, z, column)) {
                        continue;
                    }
                    int index = (z - originZ) * FIELD + x - originX;
                    bottoms[index] = Math.min(bottoms[index], passage.waterLevel() + 1);
                    tops[index] = Math.max(tops[index], column[1] + passage.roofClearance());
                    roofed = true;
                }
            }
        }
        if (!roofed) {
            return false;
        }
        boolean masked = false;
        for (int lz = 0; lz < CHUNK; lz++) {
            for (int lx = 0; lx < CHUNK; lx++) {
                int bottom = Integer.MAX_VALUE;
                int top = Integer.MIN_VALUE;
                for (int fz = lz; fz <= lz + CONTAINMENT * 2; fz++) {
                    for (int fx = lx; fx <= lx + CONTAINMENT * 2; fx++) {
                        bottom = Math.min(bottom, bottoms[fz * FIELD + fx]);
                        top = Math.max(top, tops[fz * FIELD + fx]);
                    }
                }
                bands[PowerOfTwoCoordinates.packLocal16(lx, lz)] = SurfaceFluidBoundaryPlan.boundary(bottom, top);
                masked |= bottom <= top;
            }
        }
        return masked;
    }

    public static boolean masked(int[] ceilings, long[] headroom, int column, int y) {
        return ceilings != null && y > ceilings[column] || SurfaceFluidBoundaryPlan.protects(headroom, column, y);
    }

    public static ChunkWaterRaster ready(ChunkWaterRaster raster, int chunkX, int chunkZ) {
        if (raster.provisional()) {
            throw new IllegalStateException("The water raster for chunk " + chunkX + "," + chunkZ
                    + " is not planned yet, so its caves and falls cannot be generated.");
        }
        return raster;
    }

    @FunctionalInterface
    public interface ChunkRasters {
        ChunkWaterRaster raster(int chunkX, int chunkZ);
    }
}
