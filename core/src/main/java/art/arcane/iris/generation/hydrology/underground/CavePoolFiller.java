package art.arcane.iris.generation.hydrology.underground;

import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.hydrology.grid.CellHash;
import art.arcane.iris.generation.hydrology.plan.HydrologySettings;
import art.arcane.iris.generation.mantle.MantleWriter;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.Arrays;
import java.util.Set;

public final class CavePoolFiller {
    public static final int REACH = HydrologySettings.MAX_CAVE_POOL_RADIUS + 1;
    static final int SURFACE_DEPTH = 3;
    private static final int SETTLE_LIMIT = 4096;
    private static final long SALT_SITES = 0x43415645534954L;
    private static final long SALT_COLUMN = 0x434156434f4c00L;
    private static final long SALT_FLOOR = 0x4341564652L << 20;
    private static final long SALT_FREEBOARD = 0x43415646524244L;
    private static final HydrologyCaveCell GUARD = HydrologyCaveCell.of(HydrologyCaveAction.SEAL_GUARD);
    private static final CaveVoxel[] VOXELS = CaveVoxel.values();
    private static final int[] DX = {1, -1, 0, 0, 0, 0};
    private static final int[] DY = {0, 0, 0, 0, 1, -1};
    private static final int[] DZ = {0, 0, 1, -1, 0, 0};
    private static final int UP = 4;
    private static final int DOWN = 5;
    private static final byte FLUID_MATCH = 1;
    private static final byte FLUID_DIFFERS = 2;

    private final HydrologySettings.CavePools pools;
    private final Set<String> fluids;

    public CavePoolFiller(HydrologySettings settings) {
        this.pools = settings.underground().cavePools();
        this.fluids = settings.fluids().keySet();
    }

    public void fill(int chunkX, int chunkZ, CarvedTerrain carved, MantleWriter writer, long seed) {
        if (!pools.enabled()) {
            return;
        }
        int attempts = attempts(seed, chunkX, chunkZ);
        for (int attempt = 0; attempt < attempts; attempt++) {
            long column = CellHash.mix(seed, SALT_COLUMN + attempt, chunkX, chunkZ);
            int x = (chunkX << 4) + (int) (column & 15);
            int z = (chunkZ << 4) + (int) ((column >>> 4) & 15);
            int y = floor(x, z, carved, seed);
            if (y >= 0) {
                long site = settle(carved, x, y, z, seed);
                Pool pool = new Search(carved, x, z, unpackX(site), unpackY(site), unpackZ(site), seed).pool();
                if (pool != null) {
                    write(chunkX, chunkZ, pool, writer);
                }
            }
        }
    }

    private int attempts(long seed, int chunkX, int chunkZ) {
        double density = pools.density();
        int whole = (int) density;
        return whole + (CellHash.unit(CellHash.mix(seed, SALT_SITES, chunkX, chunkZ)) < density - whole ? 1 : 0);
    }

    private static int floor(int x, int z, CarvedTerrain carved, long seed) {
        int top = carved.surface(x, z) - SURFACE_DEPTH;
        int chosen = -1;
        long chosenHash = Long.MAX_VALUE;
        boolean below = carved.voxel(x, 0, z) == CaveVoxel.SOLID;
        for (int y = 1; y <= top; y++) {
            CaveVoxel voxel = carved.voxel(x, y, z);
            if (voxel == CaveVoxel.AIR && below) {
                long hash = floorHash(seed, x, y, z);
                if (hash < chosenHash) {
                    chosenHash = hash;
                    chosen = y;
                }
            }
            below = voxel == CaveVoxel.SOLID;
        }
        return chosen;
    }

    private long settle(CarvedTerrain carved, int boxX, int startY, int boxZ, long seed) {
        int radius = pools.radius();
        long best = pack(boxX, startY, boxZ);
        long bestHash = floorHash(seed, boxX, startY, boxZ);
        LongOpenHashSet visited = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        visited.add(best);
        queue.enqueue(best);
        while (!queue.isEmpty() && visited.size() <= SETTLE_LIMIT) {
            long cell = queue.dequeueLong();
            int x = unpackX(cell);
            int y = unpackY(cell);
            int z = unpackZ(cell);
            CaveVoxel below = carved.voxel(x, y - 1, z);
            if (below == CaveVoxel.AIR) {
                long fall = pack(x, y - 1, z);
                if (visited.add(fall)) {
                    queue.enqueue(fall);
                }
                continue;
            }
            if (below != CaveVoxel.SOLID) {
                continue;
            }
            long hash = floorHash(seed, x, y, z);
            if (y < unpackY(best) || y == unpackY(best) && hash < bestHash) {
                best = cell;
                bestHash = hash;
            }
            for (int direction = 0; direction < 4; direction++) {
                int nextX = x + DX[direction];
                int nextZ = z + DZ[direction];
                if (Math.abs(nextX - boxX) > radius || Math.abs(nextZ - boxZ) > radius) {
                    continue;
                }
                long next = pack(nextX, y, nextZ);
                if (!visited.contains(next) && carved.voxel(nextX, y, nextZ) == CaveVoxel.AIR) {
                    visited.add(next);
                    queue.enqueue(next);
                }
            }
        }
        return best;
    }

    private static long floorHash(long seed, int x, int y, int z) {
        return CellHash.mix(seed, SALT_FLOOR + y, x, z);
    }

    private static void write(int chunkX, int chunkZ, Pool pool, MantleWriter writer) {
        HydrologyCaveCell wet = new HydrologyCaveCell(HydrologyCaveAction.POOL_SOURCE, pool.fluid(), "");
        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                int targetX = chunkX + offsetX;
                int targetZ = chunkZ + offsetZ;
                if (pool.touches(targetX, targetZ)) {
                    writer.withChunkFence(targetX, targetZ, () -> {
                        writeCells(targetX, targetZ, pool.cells(), wet, writer);
                        writeCells(targetX, targetZ, pool.walls(), GUARD, writer);
                    });
                }
            }
        }
    }

    private static void writeCells(int chunkX, int chunkZ, long[] cells, HydrologyCaveCell value, MantleWriter writer) {
        for (long cell : cells) {
            int x = unpackX(cell);
            int z = unpackZ(cell);
            if (x >> 4 != chunkX || z >> 4 != chunkZ) {
                continue;
            }
            int y = unpackY(cell);
            if (writer.getDataIfPresent(x, y, z, HydrologyCaveCell.class) == null) {
                writer.setData(x, y, z, value);
            }
        }
    }

    private String fluid(CarvedTerrain carved, int x, int y, int z) {
        String policy = carved.caveFluid(x, y, z);
        return policy != null ? policy : pools.fluid();
    }

    private String siteFluid(CarvedTerrain carved, int x, int y, int z) {
        String fluid = fluid(carved, x, y, z);
        if (!fluids.contains(fluid)) {
            throw new IllegalStateException("Cave pool at " + x + "," + y + "," + z + " names fluid '" + fluid
                    + "', which is not one of the hydrology fluids " + fluids);
        }
        return fluid;
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

    private record Pool(long[] cells, long[] walls, String fluid, int minX, int minZ, int maxX, int maxZ) {
        boolean touches(int chunkX, int chunkZ) {
            return (minX >> 4) <= chunkX && chunkX <= (maxX >> 4) && (minZ >> 4) <= chunkZ && chunkZ <= (maxZ >> 4);
        }
    }

    private record Level(int[] cells, int[] walls, int area) {
    }

    private final class Search {
        private final CarvedTerrain carved;
        private final int siteX;
        private final int siteY;
        private final int siteZ;
        private final long seed;
        private final int radius;
        private final int depth;
        private final int size;
        private final int originX;
        private final int originY;
        private final int originZ;
        private final String fluid;
        private final byte[] voxels;
        private final byte[] fluidMatches;
        private final int[] limits;
        private final int[] seen;
        private final int[] walled;
        private final int[] counted;
        private int[] queue = new int[256];
        private int[] walls = new int[256];

        private Search(CarvedTerrain carved, int boxX, int boxZ, int siteX, int siteY, int siteZ, long seed) {
            this.carved = carved;
            this.siteX = siteX;
            this.siteY = siteY;
            this.siteZ = siteZ;
            this.seed = seed;
            this.radius = pools.radius();
            this.depth = pools.maxDepth();
            this.size = radius * 2 + 3;
            this.originX = boxX - radius - 1;
            this.originY = siteY - depth;
            this.originZ = boxZ - radius - 1;
            this.fluid = siteFluid(carved, siteX, siteY, siteZ);
            int volume = size * size * (depth * 2 + 1);
            this.voxels = new byte[volume];
            this.fluidMatches = new byte[volume];
            this.limits = new int[size * size];
            this.seen = new int[volume];
            this.walled = new int[volume];
            this.counted = new int[size * size];
            Arrays.fill(limits, Integer.MIN_VALUE);
        }

        private Pool pool() {
            Level[] passed = new Level[depth];
            int count = 0;
            for (int level = siteY; level < siteY + depth; level++) {
                Level result = flood(level, count + 1);
                if (result == null) {
                    break;
                }
                passed[count++] = result;
            }
            int chosen = count - 1 - freeboard();
            if (chosen < 0 || passed[chosen].area() < pools.minArea()) {
                return null;
            }
            return pool(passed[chosen]);
        }

        private int freeboard() {
            HydrologySettings.IntRange range = pools.freeboard();
            long hash = CellHash.mix(seed, SALT_FREEBOARD + siteY, siteX, siteZ);
            return range.min() + (int) Math.floorMod(hash, (long) range.max() - range.min() + 1L);
        }

        private Level flood(int level, int stamp) {
            int bottom = level - depth + 1;
            int start = index(siteX - originX, siteY - originY, siteZ - originZ);
            int queued = 1;
            int wallCount = 0;
            int area = 1;
            queue[0] = start;
            seen[start] = stamp;
            counted[start % (size * size)] = stamp;
            for (int head = 0; head < queued; head++) {
                int cell = queue[head];
                int column = cell % (size * size);
                int localX = column % size;
                int localZ = column / size;
                int localY = cell / (size * size);
                int y = originY + localY;
                if (y > limit(localX, localZ) || !sameFluid(cell, localX, localY, localZ)) {
                    return null;
                }
                for (int direction = 0; direction < 6; direction++) {
                    int neighbourY = y + DY[direction];
                    int neighbourX = localX + DX[direction];
                    int neighbourZ = localZ + DZ[direction];
                    int neighbour = index(neighbourX, localY + DY[direction], neighbourZ);
                    CaveVoxel voxel = voxel(neighbourX, localY + DY[direction], neighbourZ, neighbour);
                    if (voxel == CaveVoxel.FOREIGN || direction == DOWN && neighbourY < bottom && voxel != CaveVoxel.SOLID) {
                        return null;
                    }
                    if (voxel == CaveVoxel.SOLID) {
                        if (walled[neighbour] != stamp) {
                            walled[neighbour] = stamp;
                            walls = grow(walls, wallCount);
                            walls[wallCount++] = neighbour;
                        }
                        continue;
                    }
                    if (direction == UP && neighbourY > level) {
                        continue;
                    }
                    if (Math.abs(neighbourX - radius - 1) > radius || Math.abs(neighbourZ - radius - 1) > radius) {
                        return null;
                    }
                    if (seen[neighbour] == stamp) {
                        continue;
                    }
                    seen[neighbour] = stamp;
                    queue = grow(queue, queued);
                    queue[queued++] = neighbour;
                    int neighbourColumn = neighbour % (size * size);
                    if (counted[neighbourColumn] != stamp) {
                        counted[neighbourColumn] = stamp;
                        if (++area > pools.maxArea()) {
                            return null;
                        }
                    }
                }
            }
            return new Level(Arrays.copyOf(queue, queued), Arrays.copyOf(walls, wallCount), area);
        }

        private Pool pool(Level level) {
            long[] cells = new long[level.cells().length];
            int minX = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (int index = 0; index < cells.length; index++) {
                cells[index] = world(level.cells()[index]);
            }
            long[] walls = new long[level.walls().length];
            for (int index = 0; index < walls.length; index++) {
                walls[index] = world(level.walls()[index]);
                int x = unpackX(walls[index]);
                int z = unpackZ(walls[index]);
                minX = Math.min(minX, x);
                minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x);
                maxZ = Math.max(maxZ, z);
            }
            return new Pool(cells, walls, fluid, minX, minZ, maxX, maxZ);
        }

        private long world(int cell) {
            int column = cell % (size * size);
            return pack(originX + column % size, originY + cell / (size * size), originZ + column / size);
        }

        private int index(int localX, int localY, int localZ) {
            return (localY * size + localZ) * size + localX;
        }

        private CaveVoxel voxel(int localX, int localY, int localZ, int index) {
            byte known = voxels[index];
            if (known == 0) {
                CaveVoxel read = carved.voxel(originX + localX, originY + localY, originZ + localZ);
                voxels[index] = (byte) (read.ordinal() + 1);
                return read;
            }
            return VOXELS[known - 1];
        }

        private boolean sameFluid(int index, int localX, int localY, int localZ) {
            byte known = fluidMatches[index];
            if (known == 0) {
                known = fluid.equals(fluid(carved, originX + localX, originY + localY, originZ + localZ)) ? FLUID_MATCH : FLUID_DIFFERS;
                fluidMatches[index] = known;
            }
            return known == FLUID_MATCH;
        }

        private int limit(int localX, int localZ) {
            int column = localZ * size + localX;
            int known = limits[column];
            if (known != Integer.MIN_VALUE) {
                return known;
            }
            int x = originX + localX;
            int z = originZ + localZ;
            int surface = carved.surface(x, z);
            int lowest = Math.min(surface, Math.min(Math.min(carved.surface(x + 1, z), carved.surface(x - 1, z)),
                    Math.min(carved.surface(x, z + 1), carved.surface(x, z - 1))));
            int open = surface;
            while (open > 0 && carved.voxel(x, open, z) == CaveVoxel.AIR) {
                open--;
            }
            int limit = Math.min(open, lowest - SURFACE_DEPTH);
            limits[column] = limit;
            return limit;
        }
    }

    private static int[] grow(int[] values, int count) {
        return count < values.length ? values : Arrays.copyOf(values, values.length * 2);
    }
}
