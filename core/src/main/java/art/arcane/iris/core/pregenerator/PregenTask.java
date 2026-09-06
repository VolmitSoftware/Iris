/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.core.pregenerator;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;
import art.arcane.volmlib.util.math.Position2;
import art.arcane.volmlib.util.math.Spiraled;
import art.arcane.volmlib.util.math.Spiraler;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Builder
@Data
public class PregenTask {
    static final int MAX_WORLD_BLOCK = 29_999_984;
    /**
     * Saturation limits for block bounds. The full int range is safe downstream: the widest derived value is
     * regionToChunk(blockToRegionFloor(MAX_BLOCK)) + 31 shifted back to blocks, which lands inside int.
     */
    static final int MIN_BLOCK = Integer.MIN_VALUE;
    static final int MAX_BLOCK = Integer.MAX_VALUE;
    /**
     * Widest region span a pregen may cover on one axis: the Minecraft world limit of +/- 30,000,000 blocks,
     * which is 58594 regions each way. Clamping alone is not enough - a saturated bound spans 8.4 million
     * regions per axis, and the spiral over that is ~7e13 iterations, which never finishes and looks like a
     * hang. A request past the world limit is a bad request, so it fails at construction.
     */
    static final int MAX_REGION_SPAN = 117_189;
    static final int CHUNK_LATTICE_STRIDE = 4;
    private static final int MAX_CACHED_ORDERS = 512;
    private static final LinkedHashMap<Long, int[]> ORDERS = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, int[]> eldest) {
            return size() > MAX_CACHED_ORDERS;
        }
    };

    @Builder.Default
    private final boolean gui = false;
    @Builder.Default
    private final Position2 center = new Position2(0, 0);
    @Builder.Default
    private final int radiusX = 1;
    @Builder.Default
    private final int radiusZ = 1;

    private final Bounds bounds = new Bounds();

    protected PregenTask(boolean gui, Position2 center, int radiusX, int radiusZ) {
        if (radiusX <= 0 || radiusZ <= 0) {
            throw new IllegalArgumentException("Pregen radii must be greater than zero blocks.");
        }
        requireWithinWorld(center, radiusX, radiusZ);

        this.gui = gui;
        this.center = new ProxiedPos(center);
        this.radiusX = radiusX;
        this.radiusZ = radiusZ;
        bounds.update();
    }

    private static void requireWithinWorld(Position2 center, int radiusX, int radiusZ) {
        long minX = (long) center.getX() - radiusX;
        long maxX = (long) center.getX() + radiusX;
        long minZ = (long) center.getZ() - radiusZ;
        long maxZ = (long) center.getZ() + radiusZ;
        if (minX < -MAX_WORLD_BLOCK || maxX > MAX_WORLD_BLOCK
                || minZ < -MAX_WORLD_BLOCK || maxZ > MAX_WORLD_BLOCK) {
            throw new IllegalArgumentException("Pregen area exceeds Minecraft's coordinate limit of +/-"
                    + MAX_WORLD_BLOCK + " blocks: center " + center.getX() + "," + center.getZ()
                    + " radius " + radiusX + "x" + radiusZ + ".");
        }
    }

    public static void iterateRegion(int xr, int zr, Spiraled s, Position2 pull) {
        iterateRegion(xr, zr, s, pull.getX(), pull.getZ());
    }

    public static void iterateRegion(int xr, int zr, Spiraled s, int pullX, int pullZ) {
        for (int packed : orderForPull(pullX, pullZ)) {
            s.on(PowerOfTwoCoordinates.unpackLocal32X(packed) + PowerOfTwoCoordinates.regionToChunk(xr), PowerOfTwoCoordinates.unpackLocal32Z(packed) + PowerOfTwoCoordinates.regionToChunk(zr));
        }
    }

    public static void iterateRegion(int xr, int zr, Spiraled s) {
        iterateRegion(xr, zr, s, -PowerOfTwoCoordinates.regionToChunk(xr), -PowerOfTwoCoordinates.regionToChunk(zr));
    }

    private static int[] orderForPull(int pullX, int pullZ) {
        long key = orderKey(pullX, pullZ);
        synchronized (ORDERS) {
            int[] cached = ORDERS.get(key);
            if (cached != null) {
                return cached;
            }
        }

        int[] computed = computeOrder(key);
        synchronized (ORDERS) {
            ORDERS.put(key, computed);
        }
        return computed;
    }

    private static int[] computeOrder(long key) {
        int pullX = (int) (key >> 32);
        int pullZ = (int) key;
        if (pullX >= 0 && pullX <= 31 && pullZ >= 0 && pullZ <= 31) {
            return packOrder(contiguousOrder(pullX, pullZ));
        }

        Position2 pull = new Position2(pullX, pullZ);
        KList<Position2> p = new KList<>();
        new Spiraler(33, 33, (x, z) -> {
            int xx = (x + 15);
            int zz = (z + 15);
            if (xx < 0 || xx > 31 || zz < 0 || zz > 31) {
                return;
            }

            p.add(new Position2(xx, zz));
        }).drain();
        p.sort(Comparator.comparing((i) -> i.distance(pull)));
        return packOrder(latticeOrder(p));
    }

    private static int[] packOrder(KList<Position2> ordered) {
        int[] packed = new int[ordered.size()];
        for (int index = 0; index < ordered.size(); index++) {
            Position2 position = ordered.get(index);
            packed[index] = PowerOfTwoCoordinates.packLocal32(position.getX(), position.getZ());
        }

        return packed;
    }

    private static KList<Position2> contiguousOrder(int pullX, int pullZ) {
        KList<Position2> ordered = new KList<>();
        new Spiraler(65, 65, (x, z) -> {
            if (x >= 0 && x <= 31 && z >= 0 && z <= 31) {
                ordered.add(new Position2(x, z));
            }
        }).setOffset(pullX, pullZ).drain();
        return ordered;
    }

    /**
     * Regroups a region's chunk order into {@link #CHUNK_LATTICE_STRIDE}-spaced lattices, keeping
     * the pull order inside each lattice. Chunks generated back to back then sit a stride apart, so
     * their mantle windows do not overlap and their generations never wait on each other; the
     * first lattice writes most of the region's mantle and the later ones find it already done.
     */
    static KList<Position2> latticeOrder(KList<Position2> pulled) {
        KList<Position2> ordered = new KList<>();
        for (int classX = 0; classX < CHUNK_LATTICE_STRIDE; classX++) {
            for (int classZ = 0; classZ < CHUNK_LATTICE_STRIDE; classZ++) {
                for (Position2 position : pulled) {
                    if (Math.floorMod(position.getX(), CHUNK_LATTICE_STRIDE) == classX
                            && Math.floorMod(position.getZ(), CHUNK_LATTICE_STRIDE) == classZ) {
                        ordered.add(position);
                    }
                }
            }
        }
        return ordered;
    }

    private static long orderKey(int pullX, int pullZ) {
        long high = (long) pullX << 32;
        long low = pullZ & 0xFFFFFFFFL;
        return high | low;
    }

    public void iterateRegions(Spiraled s) {
        Bound bound = bounds.region();
        new Spiraler(bound.sizeX, bound.sizeZ, ((x, z) -> {
            if (bound.check(x, z)) s.on(x, z);
        })).setOffset(PowerOfTwoCoordinates.blockToRegionFloor(center.getX()), PowerOfTwoCoordinates.blockToRegionFloor(center.getZ())).drain();
    }

    public void iterateChunks(int rX, int rZ, Spiraled s) {
        Bound bound = bounds.chunk();
        int pullX = PowerOfTwoCoordinates.blockToChunkFloor(center.getX()) - PowerOfTwoCoordinates.regionToChunk(rX);
        int pullZ = PowerOfTwoCoordinates.blockToChunkFloor(center.getZ()) - PowerOfTwoCoordinates.regionToChunk(rZ);
        iterateRegion(rX, rZ, ((x, z) -> {
            if (bound.check(x, z)) s.on(x, z);
        }), pullX, pullZ);
    }

    public int[] regionBounds() {
        Bound bound = bounds.region();
        return new int[]{bound.minX(), bound.minZ(), bound.maxX(), bound.maxZ()};
    }

    public int[] chunkBounds() {
        Bound bound = bounds.chunk();
        return new int[]{bound.minX(), bound.minZ(), bound.maxX(), bound.maxZ()};
    }

    public long chunkCount() {
        Bound bound = bounds.chunk();
        return (long) bound.sizeX() * bound.sizeZ();
    }

    @FunctionalInterface
    public interface InterleavedChunkConsumer {
        boolean on(int regionX, int regionZ, int chunkX, int chunkZ, boolean firstChunkInRegion, boolean lastChunkInRegion);
    }

    public void iterateAllChunks(Spiraled s) {
        iterateRegions(((rX, rZ) -> iterateChunks(rX, rZ, s)));
    }

    public void iterateAllChunksInterleaved(InterleavedChunkConsumer consumer) {
        List<int[]> regions = new ArrayList<>();
        iterateRegions((rX, rZ) -> regions.add(new int[]{rX, rZ}));

        List<List<int[]>> regionChunks = new ArrayList<>();
        for (int[] region : regions) {
            List<int[]> chunks = new ArrayList<>();
            iterateChunks(region[0], region[1], (cx, cz) -> chunks.add(new int[]{region[0], region[1], cx, cz}));
            if (!chunks.isEmpty()) {
                regionChunks.add(chunks);
            }
        }

        int[] indices = new int[regionChunks.size()];
        boolean anyRemaining = true;
        while (anyRemaining) {
            anyRemaining = false;
            for (int r = 0; r < regionChunks.size(); r++) {
                List<int[]> chunks = regionChunks.get(r);
                int idx = indices[r];
                if (idx >= chunks.size()) {
                    continue;
                }
                anyRemaining = true;
                int[] entry = chunks.get(idx);
                boolean first = idx == 0;
                boolean last = idx == chunks.size() - 1;
                indices[r]++;
                if (!consumer.on(entry[0], entry[1], entry[2], entry[3], first, last)) {
                    return;
                }
            }
        }
    }

    private class Bounds {
        private Bound chunk = null;
        private Bound region = null;

        /**
         * Saturating block bounds. center +/- radius is int arithmetic that wraps for far-out centers or huge
         * radii, and a wrapped bound silently inverts min/max so every check() fails and the job pregenerates
         * nothing. Clamp in long space instead.
         */
        public void update() {
            int maxX = clampBlock((long) center.getX() + radiusX);
            int maxZ = clampBlock((long) center.getZ() + radiusZ);
            int minX = clampBlock((long) center.getX() - radiusX);
            int minZ = clampBlock((long) center.getZ() - radiusZ);

            chunk = new Bound(
                    PowerOfTwoCoordinates.blockToChunkFloor(minX),
                    PowerOfTwoCoordinates.blockToChunkFloor(minZ),
                    PowerOfTwoCoordinates.ceilDivPow2(maxX, PowerOfTwoCoordinates.CHUNK_BITS),
                    PowerOfTwoCoordinates.ceilDivPow2(maxZ, PowerOfTwoCoordinates.CHUNK_BITS)
            );
            region = new Bound(
                    PowerOfTwoCoordinates.blockToRegionFloor(minX),
                    PowerOfTwoCoordinates.blockToRegionFloor(minZ),
                    PowerOfTwoCoordinates.ceilDivPow2(maxX, PowerOfTwoCoordinates.REGION_BITS),
                    PowerOfTwoCoordinates.ceilDivPow2(maxZ, PowerOfTwoCoordinates.REGION_BITS)
            );
            requireSaneSpan(region);
        }

        /**
         * A clamped bound is ordered but can still be absurd. Refuse it here instead of handing the spiral a
         * span no run could ever finish.
         */
        private void requireSaneSpan(Bound region) {
            if (region.sizeX() > MAX_REGION_SPAN || region.sizeZ() > MAX_REGION_SPAN) {
                throw new IllegalArgumentException("Pregen area is larger than a Minecraft world: center "
                        + center.getX() + "," + center.getZ() + " radius " + radiusX + "x" + radiusZ
                        + " blocks spans " + region.sizeX() + "x" + region.sizeZ() + " regions, limit "
                        + MAX_REGION_SPAN + ".");
            }
        }

        public Bound chunk() {
            if (chunk == null) update();
            return chunk;
        }

        public Bound region() {
            if (region == null) update();
            return region;
        }
    }

    static int clampBlock(long block) {
        if (block > MAX_BLOCK) {
            return MAX_BLOCK;
        }
        return block < MIN_BLOCK ? MIN_BLOCK : (int) block;
    }

    private record Bound(int minX, int minZ, int maxX, int maxZ, int sizeX, int sizeZ) {
        private Bound(int minX, int minZ, int maxX, int maxZ) {
            this(minX, minZ, maxX, maxZ, maxX - minX + 1, maxZ - minZ + 1);
        }

        boolean check(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }

    private static class ProxiedPos extends Position2 {
        public ProxiedPos(Position2 p) {
            super(p.getX(), p.getZ());
        }

        @Override
        public void setX(int x) {
            throw new IllegalStateException("This Position2 may not be modified");
        }

        @Override
        public void setZ(int z) {
            throw new IllegalStateException("This Position2 may not be modified");
        }
    }
}
