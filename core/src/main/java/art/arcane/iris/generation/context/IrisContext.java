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

import art.arcane.volmlib.nativelib.terrain.NativeGenerationScope;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.volmlib.util.collection.KMap;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

public final class IrisContext {
    private static final int SLOTS = 512;
    private static final AtomicReferenceArray<Binding> BINDINGS = new AtomicReferenceArray<>(SLOTS);
    private static final ThreadLocal<Binding> OVERFLOW = new ThreadLocal<>();
    private static final AtomicInteger OVERFLOW_BINDINGS = new AtomicInteger();
    private final Engine engine;
    private final ChunkContext chunkContext;
    private final long generationSessionId;

    private IrisContext(Engine engine, long generationSessionId, ChunkContext chunkContext) {
        this.engine = Objects.requireNonNull(engine);
        this.generationSessionId = generationSessionId;
        this.chunkContext = chunkContext;
    }

    public static IrisContext get() {
        Binding binding = binding(Thread.currentThread());
        return binding == null ? null : binding.context;
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
        Binding binding = bind(thread);
        IrisContext previous = binding.context;
        IrisContext installed = new IrisContext(engine, generationSessionId, chunkContext);
        binding.context = installed;
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

    private static Binding binding(Thread thread) {
        Binding binding = BINDINGS.getPlain(slot(thread));
        if (binding != null && binding.owner == thread) {
            return binding;
        }
        return OVERFLOW_BINDINGS.get() == 0 ? null : OVERFLOW.get();
    }

    private static Binding bind(Thread thread) {
        Binding binding = binding(thread);
        if (binding != null) {
            return binding;
        }
        binding = new Binding(thread);
        if (!BINDINGS.compareAndSet(slot(thread), null, binding)) {
            binding.overflow = true;
            OVERFLOW.set(binding);
            OVERFLOW_BINDINGS.incrementAndGet();
        }
        return binding;
    }

    private static void release(Binding binding) {
        binding.context = null;
        if (binding.overflow) {
            OVERFLOW.remove();
            OVERFLOW_BINDINGS.decrementAndGet();
            return;
        }
        BINDINGS.compareAndSet(slot(binding.owner), binding, null);
    }

    private static int slot(Thread thread) {
        return (int) thread.threadId() & (SLOTS - 1);
    }

    public static final class Scope implements NativeGenerationScope {
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
            Binding binding = binding(owner);
            if (binding == null || binding.context != installed) {
                throw new IllegalStateException("Iris execution context scopes must close in LIFO order.");
            }
            if (previous == null) {
                release(binding);
            } else {
                binding.context = previous;
            }
            closed = true;
        }
    }

    private static final class Binding {
        private final Thread owner;
        private boolean overflow;
        private IrisContext context;

        private Binding(Thread owner) {
            this.owner = owner;
        }
    }
}
