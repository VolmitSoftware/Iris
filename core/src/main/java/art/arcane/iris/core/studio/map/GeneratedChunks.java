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

import art.arcane.iris.core.link.CustomBlockSource;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.engine.framework.GenerationSessionLease;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.context.IrisContext;
import art.arcane.iris.util.project.hunk.Hunk;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * The tops of chunks generated for the studio alone: each chunk runs through the world's own stages into memory, so its
 * columns hold what the world would, trees and structures included, and nothing is placed in the world. Generating
 * plans the chunk's surroundings in the world's mantle, as generating it in the world would. A chunk is generated once
 * and its top kept; asked for from several bridge threads at once.
 */
final class GeneratedChunks implements ColumnTops {
    private static final int CHUNK_SHIFT = 4;
    private static final int BLOCKS_ACROSS = 1 << CHUNK_SHIFT;
    private static final int CHUNK_MASK = BLOCKS_ACROSS - 1;
    // A chunk's top is about 5 KB, so this many stay under 50 MB.
    private static final int MOST_CHUNKS_KEPT = 8192;
    private static final String LEASE_NAME = "studio_block_preview";

    private final Engine engine;
    private final LoadingCache<Long, ChunkTop> tops;
    private final Map<String, PlatformBlockState> seenByKey = new ConcurrentHashMap<>();

    GeneratedChunks(Engine engine) {
        this.engine = engine;
        this.tops = Caffeine.newBuilder().maximumSize(MOST_CHUNKS_KEPT).build(this::generate);
    }

    @Override
    public ColumnTop at(int worldX, int worldZ) {
        ChunkTop chunk = tops.get(keyOf(worldX >> CHUNK_SHIFT, worldZ >> CHUNK_SHIFT));
        return chunk.columns[(worldZ & CHUNK_MASK) * BLOCKS_ACROSS + (worldX & CHUNK_MASK)];
    }

    private ChunkTop generate(long key) throws Exception {
        int chunkX = (int) (key >> Integer.SIZE);
        int chunkZ = (int) key;
        int height = engine.getHeight();
        Hunk<PlatformBlockState> blocks = Hunk.newArrayHunk(BLOCKS_ACROSS, height, BLOCKS_ACROSS);
        Hunk<PlatformBiome> biomes = Hunk.newArrayHunk(BLOCKS_ACROSS, height, BLOCKS_ACROSS);
        try (GenerationSessionLease lease = engine.acquireGenerationLease(LEASE_NAME);
             IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            engine.getGeneration().generate(chunkX << CHUNK_SHIFT, chunkZ << CHUNK_SHIFT, blocks, biomes, false, lease.sessionId());
        }
        return new ChunkTop(topsOf(blocks, this::seenByPlayers));
    }

    // A plugin's block can be solid on the server where players see grass they walk through.
    private PlatformBlockState seenByPlayers(PlatformBlockState block) {
        return seenByKey.computeIfAbsent(block.key(), key -> {
            CustomBlockSource plugins = IrisServices.getOrNull(CustomBlockSource.class);
            Optional<PlatformBlockState> shown = plugins == null
                    ? Optional.empty()
                    : plugins.lookOf(key).map(look -> B.getStateOrNull(look.shownAs(), false));
            return shown.orElse(block);
        });
    }

    private static long keyOf(int chunkX, int chunkZ) {
        return ((long) chunkX << Integer.SIZE) | (chunkZ & 0xFFFFFFFFL);
    }

    private record ChunkTop(ColumnTop[] columns) {
    }

    /**
     * The top of each column of a chunk's blocks, row by row from the north west.
     *
     * @param seenByPlayers the state players see in place of a block, which decides whether it is ground; the block
     *                      itself for all but a plugin's
     */
    static ColumnTop[] topsOf(Hunk<PlatformBlockState> blocks, UnaryOperator<PlatformBlockState> seenByPlayers) {
        ColumnTop[] columns = new ColumnTop[BLOCKS_ACROSS * BLOCKS_ACROSS];
        for (int z = 0; z < BLOCKS_ACROSS; z++) {
            for (int x = 0; x < BLOCKS_ACROSS; x++) {
                columns[z * BLOCKS_ACROSS + x] = topOf(blocks, x, z, seenByPlayers);
            }
        }
        return columns;
    }

    // The ground is the highest block something can stand on, so a tree's leaves and a roof count; what stands on it
    // without being ground, such as grass or a layer of snow, is its cover.
    private static ColumnTop topOf(Hunk<PlatformBlockState> blocks, int x, int z, UnaryOperator<PlatformBlockState> seenByPlayers) {
        for (int y = blocks.getHeight() - 1; y >= 0; y--) {
            PlatformBlockState block = blocks.get(x, y, z);
            if (block == null || !isGround(seenByPlayers.apply(block))) {
                continue;
            }
            PlatformBlockState above = y + 1 < blocks.getHeight() ? blocks.get(x, y + 1, z) : null;
            return new ColumnTop(y, block.key(), isCover(above) ? above.key() : null);
        }
        return ColumnTop.NOTHING;
    }

    private static boolean isGround(PlatformBlockState block) {
        return !block.isAir() && !block.isFluid() && block.isSolid();
    }

    private static boolean isCover(PlatformBlockState block) {
        return block != null && !block.isAir() && !block.isFluid();
    }
}
