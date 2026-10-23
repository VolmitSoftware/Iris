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
import art.arcane.iris.engine.mantle.MantleWriter;
import art.arcane.iris.util.project.context.ChunkContext;

import java.util.function.Function;

/**
 * Iris's own view of the chunk whose mantle stages are running: the engine, the mantle writer prepared around
 * the chunk and the generating chunk's sampled context. Iris offers it as an extension of the mantle invocation, so
 * its built-in mantle stages and the hosts they call read it with {@code context.extension(IrisMantleChunk.class)} and
 * write Iris's own mantle data; pack modules cannot see this class.
 */
public final class IrisMantleChunk {
    private final Engine engine;
    private final MantleWriter writer;
    private final int chunkX;
    private final int chunkZ;
    private final ChunkContext context;

    IrisMantleChunk(Engine engine, MantleWriter writer, int chunkX, int chunkZ, ChunkContext context) {
        this.engine = engine;
        this.writer = writer;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.context = context;
    }

    public Engine engine() {
        return engine;
    }

    /** Iris's mantle around the chunk, as far as the mantle pipeline reaches. */
    public MantleWriter writer() {
        return writer;
    }

    public int chunkX() {
        return chunkX;
    }

    public int chunkZ() {
        return chunkZ;
    }

    /** What the engine sampled for the chunk whose generation asked for this mantle, not necessarily this chunk. */
    public ChunkContext context() {
        return context;
    }

    /** The {@code type} state of this engine runtime; see {@link art.arcane.iris.engine.framework.EngineGeneration#state}. */
    public <T> T state(Class<T> type, Function<Engine, T> create) {
        return engine.getGeneration().state(type, create);
    }
}
