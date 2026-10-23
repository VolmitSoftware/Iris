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
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.hunk.Hunk;

import java.util.function.Function;

/**
 * Iris's own view of the chunk whose module stages are running: the engine, Iris's block and biome hunks and
 * the chunk's sampled context. Iris offers it as an extension of the chunk invocation, so its built-in chunk stages
 * read it with {@code context.extension(IrisChunk.class)} and work on Iris's types directly; pack modules cannot see
 * this class.
 */
public final class IrisChunk {
    private final Engine engine;
    private final int originX;
    private final int originZ;
    private final Hunk<PlatformBlockState> blocks;
    private final Hunk<PlatformBiome> biomes;
    private final boolean multicore;
    private final ChunkContext context;

    IrisChunk(Engine engine, int originX, int originZ, Hunk<PlatformBlockState> blocks, Hunk<PlatformBiome> biomes,
              boolean multicore, ChunkContext context) {
        this.engine = engine;
        this.originX = originX;
        this.originZ = originZ;
        this.blocks = blocks;
        this.biomes = biomes;
        this.multicore = multicore;
        this.context = context;
    }

    public Engine engine() {
        return engine;
    }

    /** The chunk's first block along X. */
    public int originX() {
        return originX;
    }

    /** The chunk's first block along Z. */
    public int originZ() {
        return originZ;
    }

    /** The chunk's blocks, indexed from its first block; Iris leaves unset blocks null. */
    public Hunk<PlatformBlockState> blocks() {
        return blocks;
    }

    /** The chunk's biomes, indexed like {@link #blocks()}. */
    public Hunk<PlatformBiome> biomes() {
        return biomes;
    }

    /** Whether stages may spread their work over threads. */
    public boolean multicore() {
        return multicore;
    }

    /** What the engine sampled for this chunk, such as its biomes and heights. */
    public ChunkContext context() {
        return context;
    }

    /** The {@code type} state of this engine runtime, such as an actuator; see {@link art.arcane.iris.engine.framework.EngineGeneration#state}. */
    public <T> T state(Class<T> type, Function<Engine, T> create) {
        return engine.getGeneration().state(type, create);
    }

    /** Whether stages backed by the mantle skip this chunk, as maintenance regeneration on Folia requires. */
    public boolean bypassesMantleStages() {
        return engine.getGeneration().bypassesMantleStages();
    }
}
