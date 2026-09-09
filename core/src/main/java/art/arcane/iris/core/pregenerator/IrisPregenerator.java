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

package art.arcane.iris.core.pregenerator;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.spi.protocol.IrisMessage;
import art.arcane.iris.core.protocol.IrisProtocolServer;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.tools.IrisPackBenchmarking;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.Position2;
import art.arcane.volmlib.util.scheduling.ChronoLatch;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.scheduling.Looper;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;


public class IrisPregenerator {
    private static final double INVALID = 9223372036854775807d;
    private static final AtomicLong JOB_SEQUENCE = new AtomicLong(0L);
    private static final long GATE_WAIT_MS = 50L;
    private static final long RECLAIM_WAIT_MS = 150L;
    private final long jobId;
    private final PregenTask task;
    private final PregeneratorMethod generator;
    private final PregenListener listener;
    private final Looper ticker;
    private final AtomicBoolean paused;
    private final AtomicBoolean shutdown;
    private final ReentrantLock stateLock = new ReentrantLock();
    private final Condition stateChanged = stateLock.newCondition();
    private final PregenRateTracker rateTracker;
    private final KList<Integer> chunksPerSecondHistory;
    private final AtomicLong generated;
    private final AtomicLong cached;
    private final AtomicLong lastCachedAtMillis;
    private final AtomicLong historyLastGenerated;
    private final AtomicLong historyLastCached;
    private final AtomicLong historyLastMillis;
    private final AtomicLong totalChunks;
    private final AtomicLong startTime;
    private final AtomicReference<String> currentGeneratorMethod;
    private final KSet<Position2> generatedRegions;
    private final KSet<Position2> retry;
    private final KSet<Position2> net;
    private final ChronoLatch cl;
    private final ChronoLatch saveLatch;
    private final ChronoLatch heapReclaimLatch;
    private final AtomicLong failed;
    private final IrisPackBenchmarking benchmarking;
    private volatile PregenRates rates;
    private boolean progressClosed;

    public IrisPregenerator(PregenTask task, PregeneratorMethod generator, PregenListener listener) {
        this.jobId = JOB_SEQUENCE.incrementAndGet();
        benchmarking = IrisPackBenchmarking.getInstance();
        this.listener = listenify(listener);
        cl = new ChronoLatch(30000, false);
        saveLatch = new ChronoLatch(IrisSettings.get().getPregen().getSaveIntervalMs());
        heapReclaimLatch = new ChronoLatch(1000);
        failed = new AtomicLong(0);
        generatedRegions = new KSet<>();
        this.shutdown = new AtomicBoolean(false);
        this.paused = new AtomicBoolean(false);
        this.task = task;
        this.generator = generator;
        retry = new KSet<>();
        net = new KSet<>();
        currentGeneratorMethod = new AtomicReference<>("Void");
        chunksPerSecondHistory = new KList<>();
        generated = new AtomicLong(0);
        cached = new AtomicLong();
        lastCachedAtMillis = new AtomicLong(0L);
        historyLastGenerated = new AtomicLong(0L);
        historyLastCached = new AtomicLong(0L);
        historyLastMillis = new AtomicLong(M.ms());
        totalChunks = new AtomicLong(0);
        startTime = new AtomicLong(M.ms());
        rateTracker = new PregenRateTracker(startTime.get(), 0L);
        rates = PregenRates.ZERO;
        ticker = new Looper() {
            @Override
            protected long loop() {
                publishProgress(M.ms(), cl.flip());
                return 1000;
            }
        };
    }

    private long computeETA() {
        long gen = generated.get();
        long total = totalChunks.get();
        long remaining = total - gen;
        double cps = gen > 1024 ? rates.overall() : rates.tenSecond();
        double d = cps > 0D ? (remaining / cps) * 1000D : 0D;
        return Double.isFinite(d) && d != INVALID ? (long) d : 0;
    }

    private synchronized void publishProgress(long now, boolean logProgress) {
        if (progressClosed) {
            return;
        }
        long generatedCount = generated.get();
        long total = totalChunks.get();
        rates = rateTracker.sample(generatedCount, now);
        sampleHistory(now, generatedCount, cached.get());
        long eta = computeETA();
        long lastCachedAt = lastCachedAtMillis.get();
        boolean cachedProgress = lastCachedAt > 0L && now - lastCachedAt <= 10_000L;
        double chunksPerMinute = rates.sixtySecond() * 60D;
        double percentage = total > 0L ? (double) generatedCount / (double) total : 0D;

        listener.onTick(
                rates.tenSecond(),
                chunksPerMinute,
                chunksPerMinute / 1024D,
                percentage,
                generatedCount,
                total,
                Math.max(0L, total - generatedCount),
                eta,
                now - startTime.get(),
                currentGeneratorMethod.get(),
                cachedProgress);

        IrisProtocolServer protocolServer = IrisServices.getOrNull(IrisProtocolServer.class);
        if (protocolServer != null) {
            protocolServer.broadcastPregenProgress(
                    jobId,
                    generatedCount,
                    total,
                    rates.tenSecond(),
                    eta,
                    paused.get() ? IrisMessage.PregenProgress.STATE_PAUSED : IrisMessage.PregenProgress.STATE_RUNNING);
        }

        if (logProgress) {
            IrisLogging.info("%s: %s of %s (%.0f%%), rates overall=%s 10s=%s 30s=%s 60s=%s chunks/s%s, ETA: %s",
                    benchmarking != null ? "Benchmarking" : "Pregen",
                    Form.f(generatedCount),
                    Form.f(total),
                    percentage * 100D,
                    formatRate(rates.overall()),
                    formatRate(rates.tenSecond()),
                    formatRate(rates.thirtySecond()),
                    formatRate(rates.sixtySecond()),
                    cachedProgress ? " (cached)" : "",
                    Form.duration(eta, 2));
        }
    }

    private synchronized void publishFinalProgress() {
        try {
            publishProgress(M.ms(), false);
        } finally {
            progressClosed = true;
        }
    }

    private void sampleHistory(long now, long generatedCount, long cachedCount) {
        long previousTime = historyLastMillis.getAndSet(now);
        long previousGenerated = historyLastGenerated.getAndSet(generatedCount);
        long previousCached = historyLastCached.getAndSet(cachedCount);
        long elapsed = now - previousTime;
        long completed = generatedCount - previousGenerated - (cachedCount - previousCached);
        if (elapsed <= 0L || completed <= 0L) {
            return;
        }

        int chunksPerSecond = (int) Math.round((double) completed * 1_000D / (double) elapsed);
        synchronized (chunksPerSecondHistory) {
            chunksPerSecondHistory.add(chunksPerSecond);
        }
    }

    private static String formatRate(double rate) {
        return Form.f(rate, 1);
    }


    public long getJobId() {
        return jobId;
    }

    public void close() {
        shutdown.set(true);
        signalState();
    }

    public void start() {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        boolean completed = false;
        // Everything runs inside the try: an early throw from init/checkRegions must still
        // reach shutdown(), or the ticker/monitor threads leak and listener.onClose never
        // fires — leaving a phantom job that suppresses spawns and blocks future pregens.
        try {
            init();
            totalChunks.set(task.chunkCount());
            long startedAt = M.ms();
            startTime.set(startedAt);
            rateTracker.reset(startedAt, generated.get());
            historyLastGenerated.set(generated.get());
            historyLastCached.set(cached.get());
            historyLastMillis.set(startedAt);
            ticker.start();
            int[] regionBounds = task.regionBounds();
            generator.onRegionBounds(regionBounds[0], regionBounds[1], regionBounds[2], regionBounds[3]);
            generator.onPregenStart(task.getCenter().getX(), task.getCenter().getZ());
            task.iterateRegions((x, z) -> visitRegion(x, z, true));
            task.iterateRegions((x, z) -> visitRegion(x, z, false));
            completed = true;
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            IrisLogging.error("Pregen aborted after " + Form.duration((long) p.getMilliseconds()) + " due to " + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            publishProgress(M.ms(), false);
            shutdown();
        }
        if (completed && allVisitsComplete()) {
            logSuccessfulCompletion(p);
        } else {
            logIncompleteCompletion(p);
        }
        if (benchmarking != null) {
            benchmarking.finishedBenchmark(snapshotChunksPerSecondHistory(), rates.overall());
        }
    }

    private KList<Integer> snapshotChunksPerSecondHistory() {
        synchronized (chunksPerSecondHistory) {
            return new KList<>(chunksPerSecondHistory);
        }
    }

    private boolean allVisitsComplete() {
        long total = totalChunks.get();
        return total > 0 && generated.get() + failed.get() >= total;
    }

    private void logSuccessfulCompletion(PrecisionStopwatch stopwatch) {
        long failedCount = failed.get();
        if (failedCount > 0) {
            IrisLogging.warn("Pregen finished with " + Form.f(failedCount) + " failed chunk(s); failures are not cached, rerun to fill them");
        }
        IrisLogging.notice("Pregen finished: generated=" + Form.f(generated.get())
                + " total=" + Form.f(totalChunks.get())
                + " failed=" + Form.f(failedCount)
                + " duration=" + Form.duration((long) stopwatch.getMilliseconds())
                + formatRateSummary());
    }

    private void logIncompleteCompletion(PrecisionStopwatch stopwatch) {
        long generatedCount = generated.get();
        long failedCount = failed.get();
        long total = totalChunks.get();
        long remaining = Math.max(0L, total - generatedCount - failedCount);
        String status = shutdown.get() ? "Pregen cancelled" : "Pregen stopped before completion";
        IrisLogging.info(status + ": generated=" + Form.f(generatedCount)
                + " total=" + Form.f(total)
                + " failed=" + Form.f(failedCount)
                + " remaining=" + Form.f(remaining)
                + " duration=" + Form.duration((long) stopwatch.getMilliseconds())
                + formatRateSummary());
    }

    private String formatRateSummary() {
        return " rates[overall=" + formatRate(rates.overall())
                + ", 10s=" + formatRate(rates.tenSecond())
                + ", 30s=" + formatRate(rates.thirtySecond())
                + ", 60s=" + formatRate(rates.sixtySecond())
                + "] chunks/s";
    }

    private void init() {
        generator.init();
    }

    private void shutdown() {
        Thread.interrupted();
        shutdownStep("saving", listener::onSaving);
        shutdownStep("generator", generator::close);
        Thread.interrupted();
        shutdownStep("ticker", ticker::interrupt);
        shutdownStep("progress", this::publishFinalProgress);
        shutdownStep("mantle", () -> {
            Mantle mantle = getMantle();
            if (mantle != null) {
                reclaimTectonicPlates(mantle);
            }
        });
        shutdownStep("parallelism", PregenPerformanceProfile::restore);
        shutdownStep("protocol", () -> {
            IrisProtocolServer protocolServer = IrisServices.getOrNull(IrisProtocolServer.class);
            if (protocolServer != null) {
                long total = totalChunks.get();
                protocolServer.pregenEnd(jobId, total > 0 && generated.get() >= total);
            }
        });
        shutdownStep("listener", listener::onClose);
    }

    private void shutdownStep(String step, Runnable action) {
        try {
            action.run();
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            IrisLogging.warn("Pregen shutdown step " + step + " failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void reclaimTectonicPlates(Mantle mantle) {
        int loadedBefore = mantle.getLoadedRegionCount();
        if (loadedBefore <= 0) {
            return;
        }

        int previousLoaded = loadedBefore;
        int stagnantRounds = 0;
        for (int attempt = 0; attempt < 24 && stagnantRounds < 4; attempt++) {
            mantle.trim(0, 0);
            mantle.unloadTectonicPlate(0);
            int loaded = mantle.getLoadedRegionCount();
            if (loaded <= 0) {
                break;
            }

            if (loaded >= previousLoaded) {
                stagnantRounds++;
            } else {
                stagnantRounds = 0;
            }

            previousLoaded = loaded;
            awaitStateChange(RECLAIM_WAIT_MS);
        }

        int loadedAfter = mantle.getLoadedRegionCount();
        if (loadedAfter < loadedBefore) {
            IrisLogging.info("Pregen reclaimed " + (loadedBefore - loadedAfter) + " tectonic plate(s) from the mantle");
        }
    }

    private void visitRegion(int x, int z, boolean regions) {
        while (paused.get() && !shutdown.get()) {
            awaitStateChange(GATE_WAIT_MS);
        }

        if (shutdown.get()) {
            listener.onRegionSkipped(x, z);
            return;
        }

        Position2 pos = new Position2(x, z);

        if (generatedRegions.contains(pos)) {
            return;
        }

        currentGeneratorMethod.set(generator.getMethod(x, z));
        boolean hit = false;
        if (generator.supportsRegions(x, z, listener) && regions) {
            hit = true;
            listener.onRegionGenerating(x, z);
            generator.generateRegion(x, z, listener);
        } else if (!regions) {
            hit = true;
            listener.onRegionGenerating(x, z);
            task.iterateChunks(x, z, (xx, zz) -> {
                while ((paused.get() || MantleHeapPressure.overHighWater()) && !shutdown.get()) {
                    if (!paused.get()) {
                        reclaimHeapPressure();
                    }
                    awaitStateChange(GATE_WAIT_MS);
                }

                if (shutdown.get()) {
                    return;
                }

                generator.generateChunk(xx, zz, listener);
            });
        }

        if (hit) {
            // Exactly once per visited region, on BOTH branches: a cache-completed region that
            // never releases its sentinel wedges neighbor eviction for the whole resume
            // frontier (allNeighborsDrained can never pass around it).
            generator.onRegionSubmitted(x, z);
            listener.onRegionGenerated(x, z);

            if (saveLatch.flip()) {
                listener.onSaving();
                generator.save();
                try {
                    Mantle mantle = getMantle();
                    if (mantle != null) {
                        mantle.trim(0, 0);
                    }
                } catch (Throwable e) {
                    IrisLogging.reportError(e);
                }
            }

            generatedRegions.add(pos);
        }
    }

    private void reclaimHeapPressure() {
        if (!heapReclaimLatch.flip()) {
            return;
        }

        try {
            Mantle mantle = getMantle();
            if (mantle != null) {
                mantle.trim(0, 0);
                mantle.unloadTectonicPlate(0);
            }
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }

        MantleHeapPressure.requestPanicReclaim();
    }

    public long getFailedChunks() {
        return failed.get();
    }

    public PregenRates getRates() {
        return rates;
    }

    public void pause() {
        paused.set(true);
        signalState();
    }

    public void resume() {
        paused.set(false);
        signalState();
    }

    private void signalState() {
        stateLock.lock();
        try {
            stateChanged.signalAll();
        } finally {
            stateLock.unlock();
        }
    }

    private void awaitStateChange(long millis) {
        stateLock.lock();
        try {
            stateChanged.await(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            stateLock.unlock();
        }
    }

    private PregenListener listenify(PregenListener listener) {
        return new PregenListener() {
            @Override
            public void onTick(double chunksPerSecond, double chunksPerMinute, double regionsPerMinute, double percent, long generated, long totalChunks, long chunksRemaining, long eta, long elapsed, String method, boolean cached) {
                listener.onTick(chunksPerSecond, chunksPerMinute, regionsPerMinute, percent, generated, totalChunks, chunksRemaining, eta, elapsed, method, cached);
            }

            @Override
            public void onChunkGenerating(int x, int z) {
                listener.onChunkGenerating(x, z);
            }

            @Override
            public void onChunkGenerated(int x, int z, boolean c) {
                listener.onChunkGenerated(x, z, c);
                generated.addAndGet(1);
                if (c) {
                    cached.addAndGet(1);
                    lastCachedAtMillis.set(M.ms());
                }
            }

            @Override
            public void onChunkFailed(int x, int z) {
                failed.addAndGet(1);
                listener.onChunkFailed(x, z);
            }

            @Override
            public void onRegionGenerated(int x, int z) {
                listener.onRegionGenerated(x, z);
            }

            @Override
            public void onRegionGenerating(int x, int z) {
                listener.onRegionGenerating(x, z);
            }

            @Override
            public void onChunkCleaned(int x, int z) {
                listener.onChunkCleaned(x, z);
            }

            @Override
            public void onRegionSkipped(int x, int z) {
                listener.onRegionSkipped(x, z);
            }

            @Override
            public void onNetworkStarted(int x, int z) {
                net.add(new Position2(x, z));
            }

            @Override
            public void onNetworkFailed(int x, int z) {
                retry.add(new Position2(x, z));
            }

            @Override
            public void onNetworkReclaim(int revert) {
                generated.addAndGet(-revert);
            }

            @Override
            public void onNetworkGeneratedChunk(int x, int z) {
                generated.addAndGet(1);
            }

            @Override
            public void onNetworkDownloaded(int x, int z) {
                net.remove(new Position2(x, z));
            }

            @Override
            public void onClose() {
                listener.onClose();
            }

            @Override
            public void onSaving() {
                listener.onSaving();
            }

            @Override
            public void onChunkExistsInRegionGen(int x, int z) {
                listener.onChunkExistsInRegionGen(x, z);
            }
        };
    }

    public boolean paused() {
        return paused.get();
    }

    public Mantle getMantle() {
        return generator.getMantle();
    }
}
