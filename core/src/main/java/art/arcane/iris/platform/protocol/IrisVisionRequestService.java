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
import art.arcane.iris.spi.protocol.IrisMessage;
import art.arcane.iris.spi.protocol.IrisProtocol;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class IrisVisionRequestService implements VisionTileRequestHandler {
    private static final int DEFAULT_MAX_PENDING = 8_192;
    private static final int MAX_PENDING_PER_SESSION = 8;
    private static final int MAX_DRAIN_WORKERS = 2;
    private static final long SHED_LOG_INTERVAL_MILLIS = 60_000L;
    private static final int SEQUENCE_WRAP_GUARD = Integer.MAX_VALUE - 1024;

    private final EngineResolver engineResolver;
    private final IrisSessionRegistry registry;
    private final Executor executor;
    private final int maxPending;
    private final LinkedHashMap<String, LinkedHashMap<TileKey, PendingRequest>> pendingBySession;
    private final ArrayDeque<String> sessionOrder;
    private final Set<String> scheduledSessions;
    private final AtomicInteger activeDrains;
    private int pendingCount;
    /**
     * One counter per session, not one per (session, tile, zoom). The client only ever compares sequences
     * within a single tile key, so a session-wide monotonic counter satisfies the "newer wins" contract in
     * IrisTileAssembler while keeping this map bounded by the player count instead of by how far players pan.
     */
    private final ConcurrentHashMap<String, Integer> sequences;
    private final AtomicLong nextShedLogAt;
    private final AtomicLong droppedSaturated;
    private final AtomicLong droppedNoEngine;
    private final AtomicLong droppedNoSession;
    private final AtomicLong tilesEncoded;

    IrisVisionRequestService(EngineResolver engineResolver, IrisSessionRegistry registry, Executor executor, int maxPending) {
        this.engineResolver = Objects.requireNonNull(engineResolver, "engine resolver");
        this.registry = Objects.requireNonNull(registry, "session registry");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.maxPending = Math.max(1, maxPending);
        this.pendingBySession = new LinkedHashMap<>();
        this.sessionOrder = new ArrayDeque<>();
        this.scheduledSessions = new HashSet<>();
        this.activeDrains = new AtomicInteger(0);
        this.sequences = new ConcurrentHashMap<>();
        this.nextShedLogAt = new AtomicLong(0L);
        this.droppedSaturated = new AtomicLong(0L);
        this.droppedNoEngine = new AtomicLong(0L);
        this.droppedNoSession = new AtomicLong(0L);
        this.tilesEncoded = new AtomicLong(0L);
    }

    public static IrisVisionRequestService create(EngineResolver engineResolver, IrisSessionRegistry registry) {
        AtomicInteger threadIndex = new AtomicInteger(0);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 2, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("Iris Vision Tile " + threadIndex.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
        executor.allowCoreThreadTimeOut(true);
        PreservationRegistry preservation = IrisServices.getOrNull(PreservationRegistry.class);
        if (preservation != null) {
            preservation.register(executor);
        }
        return new IrisVisionRequestService(engineResolver, registry, executor, DEFAULT_MAX_PENDING);
    }

    @Override
    public void handle(String sessionId, int tileX, int tileZ, int zoomLevel) {
        if (sessionId == null || sessionId.isBlank()) {
            droppedNoSession.incrementAndGet();
            return;
        }
        TileKey tileKey = new TileKey(tileX, tileZ, zoomLevel);
        PendingRequest request = new PendingRequest(sessionId, tileKey);
        int shed = 0;
        synchronized (pendingBySession) {
            LinkedHashMap<TileKey, PendingRequest> sessionPending = pendingBySession.get(sessionId);
            if (sessionPending != null && sessionPending.containsKey(tileKey)) {
                sessionPending.put(tileKey, request);
                scheduleDrains();
                return;
            }
            if (sessionPending != null
                    && sessionPending.size() >= Math.min(MAX_PENDING_PER_SESSION, maxPending)) {
                removeOldest(sessionPending);
                pendingCount--;
                shed++;
            }
            while (pendingCount >= maxPending) {
                shedOnePendingSession();
                shed++;
            }
            sessionPending = pendingBySession.computeIfAbsent(sessionId, ignored -> new LinkedHashMap<>());
            sessionPending.put(tileKey, request);
            pendingCount++;
            if (scheduledSessions.add(sessionId)) {
                sessionOrder.addLast(sessionId);
            }
        }
        if (shed > 0) {
            droppedSaturated.addAndGet(shed);
            logShed();
        }
        scheduleDrains();
    }

    /**
     * Drops the retained sequence counter and every queued tile request for a session. Called when a session
     * disconnects or unregisters so neither structure grows with the player count over a server's uptime.
     */
    public void clearSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        sequences.remove(sessionId);
        synchronized (pendingBySession) {
            LinkedHashMap<TileKey, PendingRequest> removed = pendingBySession.remove(sessionId);
            if (removed != null) {
                pendingCount -= removed.size();
            }
            if (scheduledSessions.remove(sessionId)) {
                sessionOrder.removeIf(sessionId::equals);
            }
        }
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

    public long tilesEncodedCount() {
        return tilesEncoded.get();
    }

    public int pendingSize() {
        synchronized (pendingBySession) {
            return pendingCount;
        }
    }

    private void scheduleDrains() {
        while (true) {
            int active = activeDrains.get();
            synchronized (pendingBySession) {
                if (pendingCount <= active || active >= MAX_DRAIN_WORKERS) {
                    return;
                }
            }
            if (!activeDrains.compareAndSet(active, active + 1)) {
                continue;
            }
            try {
                executor.execute(this::drainPending);
            } catch (RuntimeException failure) {
                activeDrains.decrementAndGet();
                IrisLogging.reportError(failure);
                return;
            }
        }
    }

    private void drainPending() {
        try {
            while (true) {
                PendingRequest request = pollNextRequest();
                if (request == null) {
                    return;
                }
                try {
                    process(request);
                } catch (Throwable failure) {
                    IrisLogging.reportError(failure);
                }
            }
        } finally {
            activeDrains.decrementAndGet();
            scheduleDrains();
        }
    }

    private PendingRequest pollNextRequest() {
        synchronized (pendingBySession) {
            while (true) {
                String sessionId = sessionOrder.pollFirst();
                if (sessionId == null) {
                    return null;
                }
                scheduledSessions.remove(sessionId);
                LinkedHashMap<TileKey, PendingRequest> sessionPending = pendingBySession.get(sessionId);
                if (sessionPending == null || sessionPending.isEmpty()) {
                    pendingBySession.remove(sessionId);
                    continue;
                }
                PendingRequest request = removeOldest(sessionPending);
                pendingCount--;
                if (sessionPending.isEmpty()) {
                    pendingBySession.remove(sessionId);
                } else if (scheduledSessions.add(sessionId)) {
                    sessionOrder.addLast(sessionId);
                }
                return request;
            }
        }
    }

    private void shedOnePendingSession() {
        String selectedSession = null;
        int selectedSize = 0;
        for (Map.Entry<String, LinkedHashMap<TileKey, PendingRequest>> entry : pendingBySession.entrySet()) {
            if (entry.getValue().size() > selectedSize) {
                selectedSession = entry.getKey();
                selectedSize = entry.getValue().size();
            }
        }
        if (selectedSession == null) {
            return;
        }
        LinkedHashMap<TileKey, PendingRequest> selected = pendingBySession.get(selectedSession);
        removeOldest(selected);
        pendingCount--;
        if (selected.isEmpty()) {
            pendingBySession.remove(selectedSession);
            if (scheduledSessions.remove(selectedSession)) {
                sessionOrder.removeIf(selectedSession::equals);
            }
        }
    }

    private static PendingRequest removeOldest(LinkedHashMap<TileKey, PendingRequest> requests) {
        Map.Entry<TileKey, PendingRequest> oldest = requests.entrySet().iterator().next();
        requests.remove(oldest.getKey());
        return oldest.getValue();
    }

    private void process(PendingRequest request) {
        IrisSession session = registry.get(request.sessionId());
        if (session == null || !session.isReady() || !session.hasCapability(IrisProtocol.CAPABILITY_VISION)) {
            droppedNoSession.incrementAndGet();
            return;
        }
        Engine engine = engineResolver.resolve(request.sessionId());
        if (engine == null) {
            droppedNoEngine.incrementAndGet();
            return;
        }
        int sequence = nextSequence(request);
        List<IrisMessage.VisionTile> chunks = IrisTileEncoder.encode(
                engine,
                request.tile().tileX(),
                request.tile().tileZ(),
                request.tile().zoomLevel(),
                sequence
        );
        for (IrisMessage.VisionTile chunk : chunks) {
            session.send(chunk);
        }
        tilesEncoded.incrementAndGet();
    }

    private int nextSequence(PendingRequest request) {
        return sequences.merge(
                request.sessionId(),
                1,
                (Integer current, Integer step) -> current >= SEQUENCE_WRAP_GUARD ? 1 : current + step);
    }

    private void logShed() {
        long now = System.currentTimeMillis();
        long due = nextShedLogAt.get();
        if (now < due || !nextShedLogAt.compareAndSet(due, now + SHED_LOG_INTERVAL_MILLIS)) {
            return;
        }
        IrisLogging.warn("vision: request queue saturated at " + maxPending + ", shed " + droppedSaturated.get() + " total");
    }

    private record TileKey(int tileX, int tileZ, int zoomLevel) {
    }

    private record PendingRequest(String sessionId, TileKey tile) {
    }
}
