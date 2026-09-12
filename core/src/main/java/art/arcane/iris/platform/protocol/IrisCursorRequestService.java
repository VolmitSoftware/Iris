/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.platform.protocol;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.PreservationRegistry;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.spi.protocol.IrisProtocol;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class IrisCursorRequestService implements CursorInfoRequestHandler {
    private static final int DEFAULT_MAX_PENDING_SESSIONS = 2048;

    private final EngineResolver engineResolver;
    private final IrisSessionRegistry registry;
    private final Executor executor;
    private final int maxPendingSessions;
    private final LinkedHashMap<String, PendingRequest> pending;
    private final AtomicBoolean drainScheduled;
    private final AtomicLong coalesced;
    private final AtomicLong droppedSaturated;
    private final AtomicLong droppedNoEngine;
    private final AtomicLong droppedNoSession;
    private final AtomicLong resolved;

    IrisCursorRequestService(
            EngineResolver engineResolver,
            IrisSessionRegistry registry,
            Executor executor,
            int maxPendingSessions
    ) {
        this.engineResolver = Objects.requireNonNull(engineResolver, "engine resolver");
        this.registry = Objects.requireNonNull(registry, "session registry");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.maxPendingSessions = Math.max(1, maxPendingSessions);
        this.pending = new LinkedHashMap<>();
        this.drainScheduled = new AtomicBoolean(false);
        this.coalesced = new AtomicLong(0L);
        this.droppedSaturated = new AtomicLong(0L);
        this.droppedNoEngine = new AtomicLong(0L);
        this.droppedNoSession = new AtomicLong(0L);
        this.resolved = new AtomicLong(0L);
    }

    public static IrisCursorRequestService create(EngineResolver engineResolver, IrisSessionRegistry registry) {
        AtomicInteger threadIndex = new AtomicInteger(0);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1,
                1,
                30L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                runnable -> {
                    Thread thread = new Thread(runnable);
                    thread.setName("Iris Cursor Lookup " + threadIndex.incrementAndGet());
                    thread.setDaemon(true);
                    thread.setPriority(Thread.MIN_PRIORITY);
                    return thread;
                });
        executor.allowCoreThreadTimeOut(true);
        PreservationRegistry preservation = IrisServices.getOrNull(PreservationRegistry.class);
        if (preservation != null) {
            preservation.register(executor);
        }
        return new IrisCursorRequestService(engineResolver, registry, executor, DEFAULT_MAX_PENDING_SESSIONS);
    }

    @Override
    public void handle(String sessionId, int blockX, int blockZ) {
        if (sessionId == null || sessionId.isBlank()) {
            droppedNoSession.incrementAndGet();
            return;
        }
        int shed = 0;
        synchronized (pending) {
            PendingRequest previous = pending.put(sessionId, new PendingRequest(sessionId, blockX, blockZ));
            if (previous != null) {
                coalesced.incrementAndGet();
            }
            while (pending.size() > maxPendingSessions) {
                Iterator<Map.Entry<String, PendingRequest>> iterator = pending.entrySet().iterator();
                iterator.next();
                iterator.remove();
                shed++;
            }
        }
        if (shed > 0) {
            droppedSaturated.addAndGet(shed);
        }
        scheduleDrain();
    }

    public void clearSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        synchronized (pending) {
            pending.remove(sessionId);
        }
    }

    public int pendingSize() {
        synchronized (pending) {
            return pending.size();
        }
    }

    public long coalescedCount() {
        return coalesced.get();
    }

    public long droppedSaturatedCount() {
        return droppedSaturated.get();
    }

    public long droppedNoEngineCount() {
        return droppedNoEngine.get();
    }

    public long droppedNoSessionCount() {
        return droppedNoSession.get();
    }

    public long resolvedCount() {
        return resolved.get();
    }

    private void scheduleDrain() {
        if (!drainScheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            executor.execute(this::drainPending);
        } catch (RuntimeException failure) {
            drainScheduled.set(false);
            IrisLogging.reportError(failure);
        }
    }

    private void drainPending() {
        while (true) {
            PendingRequest request;
            synchronized (pending) {
                Iterator<PendingRequest> iterator = pending.values().iterator();
                if (!iterator.hasNext()) {
                    drainScheduled.set(false);
                    return;
                }
                request = iterator.next();
                iterator.remove();
            }
            try {
                process(request);
            } catch (Throwable failure) {
                IrisLogging.reportError(failure);
            }
        }
    }

    private void process(PendingRequest request) {
        IrisSession session = registry.get(request.sessionId());
        if (session == null || !session.isReady() || !session.hasCapability(IrisProtocol.CAPABILITY_CURSOR)) {
            droppedNoSession.incrementAndGet();
            return;
        }
        Engine engine = engineResolver.resolve(request.sessionId());
        if (engine == null || engine.isClosed()) {
            droppedNoEngine.incrementAndGet();
            return;
        }
        session.send(IrisCursorResolver.resolve(engine, request.blockX(), request.blockZ()));
        resolved.incrementAndGet();
    }

    private record PendingRequest(String sessionId, int blockX, int blockZ) {
    }
}
