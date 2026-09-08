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

package art.arcane.iris.core.pregenerator.methods;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.core.pregenerator.PregenDiagnostics;
import art.arcane.iris.core.pregenerator.PregenListener;
import art.arcane.iris.core.pregenerator.PregeneratorMethod;
import art.arcane.iris.core.tools.IrisToolbelt;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.PreservationRegistry;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.math.M;
import art.arcane.iris.util.common.scheduling.J;
import io.papermc.lib.PaperLib;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class MedievalPregenMethod implements PregeneratorMethod {
    private static final long CHUNK_WAIT_TIMEOUT_SECONDS = 60L;
    private static final long UNLOAD_TIMEOUT_SECONDS = 120L;
    private final World world;
    private final KList<CompletableFuture<?>> futures;
    private final Map<Chunk, Long> lastUse;
    private final int maxFutures;
    private final AtomicBoolean directAsyncDisabled;
    private final AtomicBoolean prefetchDisabled;
    private final ExecutorService prefetchPool;
    private volatile Engine cachedEngine;
    private volatile boolean engineResolutionAttempted;

    public MedievalPregenMethod(World world) {
        this.world = world;
        futures = new KList<>();
        this.lastUse = new ConcurrentHashMap<>();
        int configuredThreads = IrisSettings.getThreadCount(IrisSettings.get().getConcurrency().getParallelism());
        this.maxFutures = J.isFolia() ? Math.max(2, Math.min(64, configuredThreads)) : Math.max(16, Math.min(128, configuredThreads * 4));
        this.directAsyncDisabled = new AtomicBoolean(false);
        this.prefetchDisabled = new AtomicBoolean(J.isFolia());
        int prefetchThreads = J.isFolia() ? 0 : Math.max(2, Math.min(16, configuredThreads));
        this.prefetchPool = prefetchThreads > 0 ? newPrefetchPool(prefetchThreads) : null;
        PreservationRegistry preservation = IrisServices.getOrNull(PreservationRegistry.class);
        if (preservation != null && prefetchPool != null) {
            preservation.register(prefetchPool);
        }
    }

    private static ExecutorService newPrefetchPool(int threads) {
        AtomicInteger counter = new AtomicInteger();
        return Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "Iris Medieval Prefetch " + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    private Engine resolveEngine() {
        Engine cached = cachedEngine;
        if (cached != null) {
            return cached;
        }
        if (engineResolutionAttempted) {
            return null;
        }
        engineResolutionAttempted = true;
        try {
            if (!IrisToolbelt.isIrisWorld(world)) {
                return null;
            }
            cached = IrisToolbelt.access(world).getEngine();
            if (cached != null) {
                cachedEngine = cached;
            }
        } catch (Throwable e) {
            PregenDiagnostics.probeFailed("engine access for world " + world.getName(), e);
        }
        return cached;
    }

    private void waitForChunks() {
        for (CompletableFuture<?> i : futures) {
            try {
                i.get(CHUNK_WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (TimeoutException e) {
                IrisLogging.warn("Medieval pregen chunk did not finish in " + CHUNK_WAIT_TIMEOUT_SECONDS + "s, abandoning it.");
            } catch (Throwable e) {
                IrisLogging.reportError(e);
            }
        }

        futures.clear();
    }

    private void unloadAndSaveAllChunks(boolean flushChunkIo) {
        if (J.isFolia()) {
            lastUse.clear();
            return;
        }

        CompletableFuture<Void> unload = J.sfut(() -> {
            if (world == null) {
                IrisLogging.warn("World was null somehow...");
                return;
            }

            for (Chunk i : new ArrayList<>(lastUse.keySet())) {
                Long lastUseTime = lastUse.get(i);
                if (lastUseTime != null && M.ms() - lastUseTime >= 10) {
                    i.unload(true);
                    lastUse.remove(i);
                }
            }
            if (flushChunkIo) {
                INMS.get().flushChunkIO(world);
            }
        });

        try {
            unload.get(UNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (TimeoutException e) {
            IrisLogging.warn("Medieval pregen chunk unload did not finish in " + UNLOAD_TIMEOUT_SECONDS + "s, continuing.");
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }
    }

    @Override
    public void init() {
        unloadAndSaveAllChunks(false);
    }

    @Override
    public void close() {
        // A stop request interrupts the pregen worker; shield the drain and save so chunks still hit disk.
        boolean interrupted = Thread.interrupted();
        try {
            waitForChunks();
            if (prefetchPool != null) {
                prefetchPool.shutdownNow();
            }
            unloadAndSaveAllChunks(true);
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void save() {
        unloadAndSaveAllChunks(false);
    }

    @Override
    public boolean supportsRegions(int x, int z, PregenListener listener) {
        return false;
    }

    @Override
    public void generateRegion(int x, int z, PregenListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getMethod(int x, int z) {
        return "Medieval";
    }

    @Override
    public void generateChunk(int x, int z, PregenListener listener) {
        if (futures.size() >= maxFutures) {
            waitForChunks();
        }

        listener.onChunkGenerating(x, z);
        // Single choke point for failure accounting: without onChunkFailed a lossy run can
        // never satisfy allVisitsComplete, so a finished pregen reports as aborted forever.
        CompletableFuture<?> chunkFuture = J.isFolia()
                ? PaperLib.getChunkAtAsync(world, x, z, true).thenAccept(c -> {
                    if (c != null) {
                        lastUse.put(c, M.ms());
                    }
                    listener.onChunkGenerated(x, z);
                    try {
                        listener.onChunkCleaned(x, z);
                    } catch (Throwable e) {
                        // Already counted as generated; a throw here must not also count the
                        // chunk failed through the whenComplete choke point below.
                        IrisLogging.reportError(e);
                    }
                })
                : scheduleChunkLoad(x, z, listener);
        futures.add(chunkFuture.whenComplete((r, err) -> {
            if (err != null) {
                listener.onChunkFailed(x, z);
            }
        }));
    }

    private CompletableFuture<?> scheduleChunkLoad(int x, int z, PregenListener listener) {
        if (prefetchDisabled.get() || prefetchPool == null) {
            return runChunkLoad(x, z, listener);
        }

        Engine engine = resolveEngine();
        if (engine == null) {
            return runChunkLoad(x, z, listener);
        }

        CompletableFuture<Void> aggregate = new CompletableFuture<>();
        try {
            prefetchPool.submit(() -> {
                try {
                    try {
                        prefetchMantle(engine, x, z);
                    } catch (Throwable e) {
                        if (prefetchDisabled.compareAndSet(false, true)) {
                            IrisLogging.warn("Mantle prefetch failed at chunk " + x + "," + z + "; disabling prefetch for this pregen.");
                            IrisLogging.reportError(e);
                        }
                    }

                    CompletableFuture<?> chunkFuture = runChunkLoad(x, z, listener);
                    chunkFuture.whenComplete((r, err) -> {
                        if (err != null) {
                            aggregate.completeExceptionally(err);
                        } else {
                            aggregate.complete(null);
                        }
                    });
                } catch (Throwable e) {
                    aggregate.completeExceptionally(e);
                }
            });
        } catch (Throwable rejected) {
            if (prefetchDisabled.compareAndSet(false, true)) {
                IrisLogging.warn("Mantle prefetch pool rejected work; disabling prefetch.");
            }
            return runChunkLoad(x, z, listener);
        }
        return aggregate;
    }

    private void prefetchMantle(Engine engine, int chunkX, int chunkZ) {
        if (engine == null || engine.isClosing()) {
            return;
        }
        if (!engine.getDimension().isUseMantle()) {
            return;
        }

        ChunkContext context = new ChunkContext(chunkX, chunkZ, engine.getComplex());
        engine.generateMatter(chunkX, chunkZ, true, context);
    }

    private CompletableFuture<?> runChunkLoad(int x, int z, PregenListener listener) {
        return directAsyncDisabled.get() ? generateChunkSync(x, z, listener) : generateChunkDirectAsync(x, z, listener);
    }

    private CompletableFuture<?> generateChunkDirectAsync(int x, int z, PregenListener listener) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        J.a(() -> {
            try {
                loadChunk(x, z, listener);
                future.complete(null);
            } catch (Throwable error) {
                if (directAsyncDisabled.compareAndSet(false, true)) {
                    IrisLogging.warn("Direct async Spigot pregen chunk load failed at " + x + "," + z + "; falling back to sync chunk loads.");
                    IrisLogging.reportError(error);
                }

                // Chain instead of blocking: parking this MultiBurst worker on a 60s get()
                // pinned a shared pool thread per failing chunk.
                generateChunkSync(x, z, listener).whenComplete((r, fallbackError) -> {
                    if (fallbackError != null) {
                        future.completeExceptionally(fallbackError);
                    } else {
                        future.complete(null);
                    }
                });
            }
        });

        return future;
    }

    private CompletableFuture<?> generateChunkSync(int x, int z, PregenListener listener) {
        return J.sfut(() -> loadChunk(x, z, listener));
    }

    private void loadChunk(int x, int z, PregenListener listener) {
        Chunk chunk = world.getChunkAt(x, z);
        lastUse.put(chunk, M.ms());
        listener.onChunkGenerated(x, z);
        try {
            listener.onChunkCleaned(x, z);
        } catch (Throwable e) {
            // The chunk already counted as generated; a throw here must not also count it
            // as failed through the whenComplete choke point.
            IrisLogging.reportError(e);
        }
    }

    @Override
    public Mantle getMantle() {
        if (IrisToolbelt.isIrisWorld(world)) {
            return IrisToolbelt.access(world).getEngine().getMantle().getMantle();
        }

        return null;
    }
}
