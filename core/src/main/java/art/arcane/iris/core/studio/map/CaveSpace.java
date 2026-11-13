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

package art.arcane.iris.core.studio.map;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.hunk.Hunk;
import com.volmit.block.BlockState;
import com.volmit.geometry.BlockBox;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.GeneratedObject;
import com.volmit.object.ObjectBlock;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntBinaryOperator;

/**
 * The caves of a square of generated chunks: every air block under the terrain's top, whichever stage carved it. The
 * space is drawn as blocks, as the Caves view draws one cave, and only its outside is kept, as the inside could not be
 * seen. Each block takes the colour of the band of heights it lies in, so caves stacked over each other tell apart.
 * Chunks are added from several threads at once.
 */
final class CaveSpace {
    private static final int CHUNK_SHIFT = 4;
    private static final int CHUNK_BLOCKS = 1 << CHUNK_SHIFT;
    private static final int CHUNK_MASK = CHUNK_BLOCKS - 1;
    private static final int[][] NEIGHBOURS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    /** The colours from high to low, a band each, the last reaching to the world's bottom; the studio's legend mirrors them. */
    private static final List<Band> BANDS = List.of(
            new Band(64, new BlockState("minecraft:yellow_concrete")),
            new Band(32, new BlockState("minecraft:orange_concrete")),
            new Band(0, new BlockState("minecraft:red_concrete")),
            new Band(-32, new BlockState("minecraft:magenta_concrete")),
            new Band(-64, new BlockState("minecraft:purple_concrete")),
            new Band(Integer.MIN_VALUE, new BlockState("minecraft:blue_concrete")));

    private final int minChunkX;
    private final int minChunkZ;
    private final int chunksAcross;
    private final int height;
    private final int minWorldY;
    private final Map<Long, BitSet> openByChunk = new ConcurrentHashMap<>();

    /**
     * @param height    how many blocks tall the world is
     * @param minWorldY the world Y of its bottom block
     */
    CaveSpace(int minChunkX, int minChunkZ, int chunksAcross, int height, int minWorldY) {
        this.minChunkX = minChunkX;
        this.minChunkZ = minChunkZ;
        this.chunksAcross = chunksAcross;
        this.height = height;
        this.minWorldY = minWorldY;
    }

    /** @param terrainY the height of the terrain's top block in each column, by its x and z in the chunk */
    void add(int chunkX, int chunkZ, Hunk<PlatformBlockState> blocks, IntBinaryOperator terrainY) {
        BitSet open = new BitSet(CHUNK_BLOCKS * height * CHUNK_BLOCKS);
        for (int z = 0; z < CHUNK_BLOCKS; z++) {
            for (int x = 0; x < CHUNK_BLOCKS; x++) {
                int top = Math.min(terrainY.applyAsInt(x, z), height - 1);
                for (int y = 0; y <= top; y++) {
                    PlatformBlockState block = blocks.get(x, y, z);
                    if (block == null || block.isAir()) {
                        open.set(cellOf(x, y, z));
                    }
                }
            }
        }
        openByChunk.put(keyOf(chunkX, chunkZ), open);
    }

    /** The outside of the space, its anchor at the middle of the square's bottom; chunks never added hold no caves. */
    GeneratedObject scene() {
        List<ObjectBlock> blocks = new ArrayList<>();
        int across = chunksAcross * CHUNK_BLOCKS;
        int lowest = height;
        int highest = -1;
        for (Map.Entry<Long, BitSet> chunk : openByChunk.entrySet()) {
            int chunkX = (int) (chunk.getKey() >> Integer.SIZE);
            int chunkZ = (int) (long) chunk.getKey();
            int sceneMinX = (chunkX - minChunkX) * CHUNK_BLOCKS;
            int sceneMinZ = (chunkZ - minChunkZ) * CHUNK_BLOCKS;
            BitSet open = chunk.getValue();
            for (int cell = open.nextSetBit(0); cell >= 0; cell = open.nextSetBit(cell + 1)) {
                int x = sceneMinX + (cell & CHUNK_MASK);
                int z = sceneMinZ + ((cell >> CHUNK_SHIFT) & CHUNK_MASK);
                int y = cell >> (2 * CHUNK_SHIFT);
                if (touchesRock(x, y, z)) {
                    blocks.add(new ObjectBlock(new BlockPosition(x, y, z), colourAt(y + minWorldY)));
                    lowest = Math.min(lowest, y);
                    highest = Math.max(highest, y);
                }
            }
        }
        BlockBox bounds = highest < 0 ? new BlockBox(0, 0, 0, across, 1, across) : new BlockBox(0, lowest, 0, across, highest + 1, across);
        return new GeneratedObject(bounds, new BlockPosition(across / 2, bounds.minY(), across / 2), blocks, List.of());
    }

    static BlockState colourAt(int worldY) {
        for (Band band : BANDS) {
            if (worldY >= band.lowestY()) {
                return band.colour();
            }
        }
        return BANDS.getLast().colour();
    }

    // Beside a chunk never added, or past the square's side, counts as rock, so a cave cut off there shows its cut.
    private boolean touchesRock(int sceneX, int y, int sceneZ) {
        for (int[] step : NEIGHBOURS) {
            if (!isOpen(sceneX + step[0], y + step[1], sceneZ + step[2])) {
                return true;
            }
        }
        return false;
    }

    private boolean isOpen(int sceneX, int y, int sceneZ) {
        int across = chunksAcross * CHUNK_BLOCKS;
        if (sceneX < 0 || sceneZ < 0 || sceneX >= across || sceneZ >= across || y < 0 || y >= height) {
            return false;
        }
        BitSet open = openByChunk.get(keyOf(minChunkX + (sceneX >> CHUNK_SHIFT), minChunkZ + (sceneZ >> CHUNK_SHIFT)));
        return open != null && open.get(cellOf(sceneX & CHUNK_MASK, y, sceneZ & CHUNK_MASK));
    }

    private static int cellOf(int x, int y, int z) {
        return x | z << CHUNK_SHIFT | y << (2 * CHUNK_SHIFT);
    }

    private static long keyOf(int chunkX, int chunkZ) {
        return ((long) chunkX << Integer.SIZE) | (chunkZ & 0xFFFFFFFFL);
    }

    /** @param lowestY the world Y of the band's lowest block */
    private record Band(int lowestY, BlockState colour) {
    }
}
