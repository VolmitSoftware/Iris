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
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.hunk.Hunk;

import java.util.function.Supplier;

/**
 * Iris's own view of the chunk whose module stages are running: the engine, Iris's block hunk and the
 * chunk's sampled context. Iris's built-in chunk stages read them through {@link #current()}, bound on the
 * generating thread while the chunk graph runs, and work on Iris's block states directly; pack modules
 * cannot see this class.
 */
public final class IrisChunk {
    private static final ScopedValue<IrisChunk> CURRENT = ScopedValue.newInstance();

    private final Engine engine;
    private final int originX;
    private final int originZ;
    private final Hunk<PlatformBlockState> blocks;
    private final ChunkContext context;

    IrisChunk(Engine engine, int originX, int originZ, Hunk<PlatformBlockState> blocks, ChunkContext context) {
        this.engine = engine;
        this.originX = originX;
        this.originZ = originZ;
        this.blocks = blocks;
        this.context = context;
    }

    /** The chunk generating on this thread. */
    public static IrisChunk current() {
        if (!CURRENT.isBound()) {
            throw new IllegalStateException("No Iris chunk is generating on this thread");
        }
        return CURRENT.get();
    }

    void runBound(Runnable generation) {
        ScopedValue.where(CURRENT, this).run(generation);
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

    /** What the engine sampled for this chunk, such as its biomes and heights. */
    public ChunkContext context() {
        return context;
    }
}
