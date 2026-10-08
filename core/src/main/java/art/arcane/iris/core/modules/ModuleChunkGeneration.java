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

package art.arcane.iris.core.modules;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.hunk.Hunk;
import com.volmit.host.ChunkRunner;
import com.volmit.host.LoadedPack;
import com.volmit.host.ResolvedGraph;

import java.util.function.Function;

/**
 * Runs the pack's resolved chunk graph on a chunk, Iris's built-in stages included: this is how every chunk
 * generates. A stage is active where any column of the chunk has its
 * settings at the dimension, the column's region or its biome (see {@link ChunkModuleSettings}). A failing
 * stage fails the chunk.
 * <p>
 * Stages write the chunk's own block hunk, so a block written beyond the chunk fails instead of being
 * clipped, and read the mantle the mantle pipeline prepared.
 */
public final class ModuleChunkGeneration {
    private static final int CHUNK_SHIFT = 4;

    private ModuleChunkGeneration() {
    }

    /** {@code x} and {@code z} are the chunk's first block. */
    public static void generate(Engine engine, int x, int z, Hunk<PlatformBlockState> blocks, Hunk<PlatformBiome> biomes,
                                boolean multicore, ChunkContext context) {
        generate(engine.getData().getModules().pack(), engine, x, z, blocks, biomes, multicore, context, B::getState);
    }

    static void generate(LoadedPack pack, Engine engine, int x, int z, Hunk<PlatformBlockState> blocks, Hunk<PlatformBiome> biomes,
                         boolean multicore, ChunkContext context, Function<String, PlatformBlockState> platformStates) {
        ResolvedGraph chunk = pack.chunk();
        if (chunk.stages().isEmpty()) {
            return;
        }
        int chunkX = x >> CHUNK_SHIFT;
        int chunkZ = z >> CHUNK_SHIFT;
        long seed = ModuleSeeds.chunk(engine.getSeedManager().getSeed(), chunkX, chunkZ);
        try (PinnedMantleChunk mantle = new PinnedMantleChunk(engine, chunkX, chunkZ)) {
            IrisGenerationContext inputs = new IrisGenerationContext(engine, x, z, new BlockStateHunk(blocks, platformStates), mantle,
                    pack.catalog().mantle());
            new IrisChunk(engine, x, z, blocks, biomes, multicore, context).runBound(() -> new ChunkRunner(chunk).generate(inputs, seed));
        }
    }
}
