package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;

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
