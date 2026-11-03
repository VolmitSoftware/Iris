package art.arcane.iris.generation.hydrology.underground;

import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.hydrology.grid.CellHash;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.mantle.MantleWriter;
import art.arcane.volmlib.util.matter.slices.UpdateMatter;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.Set;
import java.util.function.Supplier;

public final class CaveSpringPlacer {
    private static final int FLOW_REACH = HydrologySettings.MAX_CAVE_POOL_RADIUS;
    private static final int FLOW_LIMIT = 4096;
    private static final long SALT_SITES = 0x535052495445L;
    private static final long SALT_DENSITY = 0x535052444e53L;
    private static final long SALT_COLUMN = 0x535052434f4cL;
    private static final long SALT_SEEP = 0x5350525345L << 20;
    private static final int[] SIDE_X = {1, -1, 0, 0};
    private static final int[] SIDE_Z = {0, 0, 1, -1};

    private final HydrologySettings.CaveSprings springs;
    private final Set<String> fluids;
    private final float density;

    public CaveSpringPlacer(HydrologySettings settings) {
        this.springs = settings.underground().caveSprings();
        this.fluids = settings.fluids().keySet();
        this.density = settings.density();
    }

    public void place(int chunkX, int chunkZ, Supplier<? extends CarvedTerrain> terrain, MantleWriter writer, long seed) {
        if (!springs.enabled() || springs.density() == 0f || density == 0f) {
            return;
        }
        int attempts = attempts(seed, chunkX, chunkZ);
        for (int attempt = 0; attempt < attempts; attempt++) {
            if (density < 1f && CellHash.unit(CellHash.mix(seed, SALT_DENSITY + attempt, chunkX, chunkZ)) >= density) {
                continue;
            }
            CarvedTerrain carved = terrain.get();
            long column = CellHash.mix(seed, SALT_COLUMN + attempt, chunkX, chunkZ);
            int x = (chunkX << 4) + (int) (column & 15);
            int z = (chunkZ << 4) + (int) ((column >>> 4) & 15);
            int y = seep(carved, x, z, seed);
            if (y >= 0 && contained(carved, x, y, z) && !carved.structureNear(x, y, z)) {
                write(chunkX, chunkZ, x, y, z, new HydrologyCaveCell(HydrologyCaveAction.SPRING_SOURCE, fluid(carved, x, y, z)), writer);
            }
        }
    }

    private int attempts(long seed, int chunkX, int chunkZ) {
        double sites = springs.density();
        int whole = (int) sites;
        return whole + (CellHash.unit(CellHash.mix(seed, SALT_SITES, chunkX, chunkZ)) < sites - whole ? 1 : 0);
    }

    private int seep(CarvedTerrain carved, int x, int z, long seed) {
        int top = carved.surface(x, z) - springs.minDepthBelowSurface();
        int chosen = -1;
        long chosenHash = Long.MAX_VALUE;
        CaveVoxel below = carved.voxel(x, 1, z);
        CaveVoxel voxel = carved.voxel(x, 2, z);
        for (int y = 2; y <= top; y++) {
            CaveVoxel above = carved.voxel(x, y + 1, z);
            if (below == CaveVoxel.SOLID && voxel == CaveVoxel.SOLID && above == CaveVoxel.SOLID && opens(carved, x, y, z)) {
                long hash = CellHash.mix(seed, SALT_SEEP + y, x, z);
                if (hash < chosenHash) {
                    chosenHash = hash;
                    chosen = y;
                }
            }
            below = voxel;
            voxel = above;
        }
        return chosen;
    }

    private static boolean opens(CarvedTerrain carved, int x, int y, int z) {
        for (int side = 0; side < 4; side++) {
            int sideX = x + SIDE_X[side];
            int sideZ = z + SIDE_Z[side];
            if (carved.voxel(sideX, y, sideZ) == CaveVoxel.AIR && carved.voxel(sideX, y - 1, sideZ) == CaveVoxel.AIR) {
                return true;
            }
        }
        return false;
    }

    private boolean contained(CarvedTerrain carved, int seepX, int seepY, int seepZ) {
        LongOpenHashSet visited = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        for (int side = 0; side < 4; side++) {
            int sideX = seepX + SIDE_X[side];
            int sideZ = seepZ + SIDE_Z[side];
            CaveVoxel voxel = carved.voxel(sideX, seepY, sideZ);
            if (voxel == CaveVoxel.FOREIGN) {
                return false;
            }
            if (voxel == CaveVoxel.AIR) {
                if (!roofed(carved, sideX, seepY, sideZ)) {
                    return false;
                }
                long cell = pack(sideX, seepY, sideZ);
                visited.add(cell);
                queue.enqueue(cell);
            }
        }
        while (!queue.isEmpty() && visited.size() <= FLOW_LIMIT) {
            long cell = queue.dequeueLong();
            int x = unpackX(cell);
            int y = unpackY(cell);
            int z = unpackZ(cell);
            if (y > carved.surface(x, z) - springs.minDepthBelowSurface()) {
                return false;
            }
            for (int next = 0; next < 5; next++) {
                int nextX = next < 4 ? x + SIDE_X[next] : x;
                int nextY = next < 4 ? y : y - 1;
                int nextZ = next < 4 ? z + SIDE_Z[next] : z;
                if (Math.abs(nextX - seepX) > FLOW_REACH || Math.abs(nextZ - seepZ) > FLOW_REACH) {
                    continue;
                }
                CaveVoxel voxel = carved.voxel(nextX, nextY, nextZ);
                if (voxel == CaveVoxel.FOREIGN) {
                    return false;
                }
                if (voxel == CaveVoxel.AIR && visited.add(pack(nextX, nextY, nextZ))) {
                    queue.enqueue(pack(nextX, nextY, nextZ));
                }
            }
        }
        return true;
    }

    private static boolean roofed(CarvedTerrain carved, int x, int startY, int z) {
        int y = startY + 1;
        CaveVoxel voxel = carved.voxel(x, y, z);
        while (voxel == CaveVoxel.AIR) {
            voxel = carved.voxel(x, ++y, z);
        }
        return voxel == CaveVoxel.SOLID;
    }

    private String fluid(CarvedTerrain carved, int x, int y, int z) {
        String policy = carved.caveFluid(x, y, z);
        String fluid = policy != null ? policy : springs.fluid();
        if (!fluids.contains(fluid)) {
            throw new IllegalStateException("Cave spring at " + x + "," + y + "," + z + " names fluid '" + fluid
                    + "', which is not one of the hydrology fluids " + fluids);
        }
        return fluid;
    }

    private static void write(int chunkX, int chunkZ, int x, int y, int z, HydrologyCaveCell source, MantleWriter writer) {
        writer.withChunkFence(chunkX, chunkZ, () -> {
            if (writer.getDataIfPresent(x, y, z, HydrologyCaveCell.class) == null) {
                writer.setData(x, y, z, source);
                writer.setData(x, y, z, UpdateMatter.ON);
            }
        });
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x + (1 << 25)) << 38) | ((long) (z + (1 << 25)) << 12) | y;
    }

    private static int unpackX(long cell) {
        return (int) (cell >>> 38) - (1 << 25);
    }

    private static int unpackZ(long cell) {
        return (int) ((cell >>> 12) & ((1L << 26) - 1)) - (1 << 25);
    }

    private static int unpackY(long cell) {
        return (int) (cell & 4095);
    }
}
