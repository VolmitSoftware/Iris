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

package art.arcane.iris.generation.context;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.volmlib.util.collection.KMap;

import java.util.Objects;

public final class IrisContext {
    private static final ThreadLocal<IrisContext> CONTEXT = new ThreadLocal<>();
    private final Engine engine;
    private final ChunkContext chunkContext;
    private final long generationSessionId;

    private IrisContext(Engine engine, long generationSessionId, ChunkContext chunkContext) {
        this.engine = Objects.requireNonNull(engine);
        this.generationSessionId = generationSessionId;
        this.chunkContext = chunkContext;
    }

    public static IrisContext get() {
        return CONTEXT.get();
    }

    public static IrisContext require() {
        IrisContext current = get();
        if (current == null) {
            throw new IllegalStateException("No Iris execution context is bound to thread " + Thread.currentThread().getName() + ".");
        }
        return current;
    }

    public static Scope open(Engine engine, long generationSessionId, ChunkContext chunkContext) {
        Thread thread = Thread.currentThread();
        IrisContext previous = CONTEXT.get();
        IrisContext installed = new IrisContext(engine, generationSessionId, chunkContext);
        CONTEXT.set(installed);
        return new Scope(thread, previous, installed);
    }

    public Engine getEngine() {
        return engine;
    }

    public ChunkContext getChunkContext() {
        return chunkContext;
    }

    public long getGenerationSessionId() {
        return generationSessionId;
    }

    public IrisData getData() {
        return engine.getData();
    }

    public IrisComplex getComplex() {
        return engine.getComplex();
    }

    public KMap<String, Object> asContext() {
        Long hash32 = engine.getHash32().getNow(null);
        IrisDimension dimension = engine.getDimension();
        EngineMantle mantle = engine.getMantle();
        return new KMap<String, Object>()
                .qput("studio", engine.isStudio())
                .qput("closed", engine.isClosed())
                .qput("generationSessionId", generationSessionId)
                .qput("pack", new KMap<>()
                        .qput("key", dimension == null ? "" : dimension.getLoadKey())
                        .qput("version", dimension == null ? "" : dimension.getVersion())
                        .qput("hash", hash32 == null ? "" : Long.toHexString(hash32)))
                .qput("mantle", new KMap<>()
                        .qput("idle", mantle.getAdjustedIdleDuration())
                        .qput("loaded", mantle.getLoadedRegionCount())
                        .qput("queued", mantle.getUnloadRegionCount()));
    }

    public static final class Scope implements AutoCloseable {
        private final Thread owner;
        private final IrisContext previous;
        private final IrisContext installed;
        private boolean closed;

        private Scope(Thread owner, IrisContext previous, IrisContext installed) {
            this.owner = owner;
            this.previous = previous;
            this.installed = installed;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("Iris execution context scope closed from a different thread.");
            }
            IrisContext current = CONTEXT.get();
            if (current != installed) {
                throw new IllegalStateException("Iris execution context scopes must close in LIFO order.");
            }
            if (previous == null) {
                CONTEXT.remove();
            } else {
                CONTEXT.set(previous);
            }
            closed = true;
        }
    }
}
