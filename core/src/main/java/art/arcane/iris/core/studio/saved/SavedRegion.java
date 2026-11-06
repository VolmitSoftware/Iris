/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core.studio.saved;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * What the view needs of one region file, 512 by 512 columns: the ground's top block under any water, its block and
 * biome, and where the water over it ends. Columns of chunks whose terrain isn't finished have no ground.
 * <p>
 * A chunk is read the first time one of its columns is asked for, so a view far out, which asks for a column every few
 * blocks, reads only the chunks it lands in. Called from several bridge threads at once.
 */
final class SavedRegion {
    static final int BLOCKS_ACROSS = RegionFile.CHUNKS_ACROSS * SavedChunk.BLOCKS_ACROSS;
    static final int NO_WATER = Short.MIN_VALUE;
    private static final int CHUNK_COUNT = RegionFile.CHUNKS_ACROSS * RegionFile.CHUNKS_ACROSS;
    private static final int CHUNK_COLUMNS = SavedChunk.BLOCKS_ACROSS * SavedChunk.BLOCKS_ACROSS;
    // Chunks share a lock with the others in their stripe, so different chunks of one region mostly read side by side.
    private static final int CHUNK_LOCK_STRIPES = 64;
    private static final short NO_GROUND = Short.MIN_VALUE;
    /** Where no region file was saved: every column is ungenerated. */
    static final SavedRegion NOTHING_SAVED = new SavedRegion((chunkX, chunkZ) -> Optional.empty(), block -> true);

    private final ChunkSource source;
    private final SavedGround ground;
    private final AtomicIntegerArray readChunks = new AtomicIntegerArray(CHUNK_COUNT);
    private final Object[] chunkLocks = new Object[CHUNK_LOCK_STRIPES];
    // Columns are written while holding this region, and read once their chunk is marked read.
    private final short[] groundY = new short[BLOCKS_ACROSS * BLOCKS_ACROSS];
    private final short[] waterSurfaceY = new short[BLOCKS_ACROSS * BLOCKS_ACROSS];
    private final short[] blockIds = new short[BLOCKS_ACROSS * BLOCKS_ACROSS];
    private final short[] biomeIds = new short[BLOCKS_ACROSS * BLOCKS_ACROSS];
    private final List<String> blocks = new CopyOnWriteArrayList<>();
    private final List<String> biomes = new CopyOnWriteArrayList<>();
    private final Map<String, Short> blockIndex = new HashMap<>();
    private final Map<String, Short> biomeIndex = new HashMap<>();
    private final Map<Integer, Integer> waterColumnsBySurface = new HashMap<>();
    private int minY = Integer.MAX_VALUE;
    private int lowestGround = Integer.MAX_VALUE;
    private int highestGround = Integer.MIN_VALUE;

    /** Where a region's chunks are read from: chunk within the region, 0 to 31, to the chunk or empty. */
    @FunctionalInterface
    interface ChunkSource {
        Optional<SavedChunk> read(int chunkX, int chunkZ);
    }

    SavedRegion(ChunkSource source, SavedGround ground) {
        this.source = source;
        this.ground = ground;
        Arrays.setAll(chunkLocks, stripe -> new Object());
        Arrays.fill(groundY, NO_GROUND);
        Arrays.fill(waterSurfaceY, (short) NO_WATER);
    }

    static SavedRegion of(RegionFile file, SavedGround ground) {
        return new SavedRegion((chunkX, chunkZ) -> file.chunk(chunkX, chunkZ).flatMap(SavedChunk::of), ground);
    }

    /**
     * The column at this block, or null where its chunk's terrain isn't finished.
     *
     * @param x block within the region, 0 to 511
     * @param z block within the region, 0 to 511
     */
    SavedColumn columnAt(int x, int z) {
        readChunk(x / SavedChunk.BLOCKS_ACROSS, z / SavedChunk.BLOCKS_ACROSS);
        int index = x + z * BLOCKS_ACROSS;
        if (groundY[index] == NO_GROUND) {
            return null;
        }
        return new SavedColumn(groundY[index], waterSurfaceY[index], blocks.get(blockIds[index]), biomes.get(biomeIds[index]));
    }

    /** Reads every chunk not read yet, for what is measured over the whole region. */
    void readAll() {
        for (int chunkZ = 0; chunkZ < RegionFile.CHUNKS_ACROSS; chunkZ++) {
            for (int chunkX = 0; chunkX < RegionFile.CHUNKS_ACROSS; chunkX++) {
                readChunk(chunkX, chunkZ);
            }
        }
    }

    private void readChunk(int chunkX, int chunkZ) {
        int chunk = chunkZ * RegionFile.CHUNKS_ACROSS + chunkX;
        if (readChunks.get(chunk) == 1) {
            return;
        }
        synchronized (chunkLocks[chunk % CHUNK_LOCK_STRIPES]) {
            if (readChunks.get(chunk) == 1) {
                return;
            }
            source.read(chunkX, chunkZ).ifPresent(saved ->
                    store(ChunkColumns.of(saved, ground), chunkX * SavedChunk.BLOCKS_ACROSS, chunkZ * SavedChunk.BLOCKS_ACROSS));
            readChunks.set(chunk, 1);
        }
    }

    private synchronized void store(ChunkColumns chunk, int originX, int originZ) {
        minY = Math.min(minY, chunk.minY());
        for (int column = 0; column < CHUNK_COLUMNS; column++) {
            int index = (originZ + column / SavedChunk.BLOCKS_ACROSS) * BLOCKS_ACROSS + originX + column % SavedChunk.BLOCKS_ACROSS;
            int groundTop = chunk.groundY()[column];
            int waterTop = chunk.waterSurfaceY()[column];
            groundY[index] = (short) groundTop;
            waterSurfaceY[index] = (short) waterTop;
            blockIds[index] = idOf(chunk.blocks()[column], blockIndex, blocks);
            biomeIds[index] = idOf(chunk.biomes()[column], biomeIndex, biomes);
            lowestGround = Math.min(lowestGround, groundTop);
            highestGround = Math.max(highestGround, Math.max(groundTop, waterTop));
            if (waterTop != NO_WATER) {
                waterColumnsBySurface.merge(waterTop, 1, Integer::sum);
            }
        }
    }

    private static short idOf(String name, Map<String, Short> index, List<String> names) {
        return index.computeIfAbsent(name, added -> {
            names.add(added);
            return (short) (names.size() - 1);
        });
    }

    /** The water surface most water read in this region lies at, or {@link #NO_WATER} where none was read. */
    synchronized int seaLevel() {
        return waterColumnsBySurface.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(NO_WATER);
    }

    synchronized boolean isEmpty() {
        return highestGround == Integer.MIN_VALUE;
    }

    /** The world's lowest block, or {@link Integer#MAX_VALUE} where no finished chunk was read. */
    synchronized int minY() {
        return minY;
    }

    synchronized int lowestGround() {
        return lowestGround;
    }

    synchronized int highestGround() {
        return highestGround;
    }

    /**
     * One column as saved.
     *
     * @param groundY       the top block of the ground, under any water
     * @param waterSurfaceY the top water block over it, or {@link #NO_WATER}
     */
    record SavedColumn(int groundY, int waterSurfaceY, String block, String biome) {
    }

    /** One chunk's columns, read without holding the region so other chunks can be read meanwhile. */
    private record ChunkColumns(int minY, short[] groundY, short[] waterSurfaceY, String[] blocks, String[] biomes) {
        static ChunkColumns of(SavedChunk chunk, SavedGround ground) {
            ChunkColumns columns = new ChunkColumns(chunk.minY(), new short[CHUNK_COLUMNS], new short[CHUNK_COLUMNS],
                    new String[CHUNK_COLUMNS], new String[CHUNK_COLUMNS]);
            for (int z = 0; z < SavedChunk.BLOCKS_ACROSS; z++) {
                for (int x = 0; x < SavedChunk.BLOCKS_ACROSS; x++) {
                    columns.read(chunk, ground, x, z);
                }
            }
            return columns;
        }

        // The heightmap only says where to start: blocks placed after it was saved can leave air or water above the ground.
        private void read(SavedChunk chunk, SavedGround ground, int x, int z) {
            int column = z * SavedChunk.BLOCKS_ACROSS + x;
            int y = chunk.surfaceY(x, z);
            int waterTop = NO_WATER;
            String block = chunk.blockAt(x, y, z);
            while (y > minY && !isSurface(chunk, ground, x, y, z, block)) {
                if (waterTop == NO_WATER && SavedGround.isWater(block)) {
                    waterTop = y;
                }
                y--;
                block = chunk.blockAt(x, y, z);
            }
            groundY[column] = (short) y;
            waterSurfaceY[column] = (short) waterTop;
            blocks[column] = block;
            biomes[column] = chunk.biomeAt(x, y, z);
        }

        private boolean isSurface(SavedChunk chunk, SavedGround ground, int x, int y, int z, String block) {
            if (!isGround(block, ground)) {
                return false;
            }
            return !SavedGround.isThin(block) || y <= minY || isGround(chunk.blockAt(x, y - 1, z), ground);
        }

        private static boolean isGround(String block, SavedGround ground) {
            return !SavedGround.isAir(block) && !SavedGround.isWater(block) && ground.isGround(block);
        }
    }
}
