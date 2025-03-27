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

package com.volmit.iris.core.pregenerator;

import com.volmit.iris.Iris;
import com.volmit.iris.core.tools.IrisPackBenchmarking;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.math.RollingSequence;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.Looper;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;


public class IrisPregenerator {
    private static final double INVALID = 9223372036854775807d;
    private final PregenTask task;
    private final PregeneratorMethod generator;
    private final PregenListener listener;
    private final Looper ticker;
    private final AtomicBoolean paused;
    private final AtomicBoolean shutdown;
    private final RollingSequence cachedPerSecond;
    private final RollingSequence chunksPerSecond;
    private final RollingSequence chunksPerMinute;
    private final RollingSequence regionsPerMinute;
    private final KList<Integer> chunksPerSecondHistory;
    private final AtomicLong generated;
    private final AtomicLong generatedLast;
    private final AtomicLong generatedLastMinute;
    private final AtomicLong cached;
    private final AtomicLong cachedLast;
    private final AtomicLong cachedLastMinute;
    private final AtomicLong totalChunks;
    private final AtomicLong startTime;
    private final ChronoLatch minuteLatch;
    private final AtomicReference<String> currentGeneratorMethod;
    private final KSet<Position2> generatedRegions;
    private final KSet<Position2> retry;
    private final KSet<Position2> net;
    private final ChronoLatch cl;
    private final ChronoLatch saveLatch = new ChronoLatch(30000);

    public IrisPregenerator(PregenTask task, PregeneratorMethod generator, PregenListener listener) {
        this.listener = listenify(listener);
        cl = new ChronoLatch(5000);
        generatedRegions = new KSet<>();
        this.shutdown = new AtomicBoolean(false);
        this.paused = new AtomicBoolean(false);
        this.task = task;
        this.generator = generator;
        retry = new KSet<>();
        net = new KSet<>();
        currentGeneratorMethod = new AtomicReference<>("Void");
        minuteLatch = new ChronoLatch(60000, false);
        cachedPerSecond = new RollingSequence(5);
        chunksPerSecond = new RollingSequence(10);
        chunksPerMinute = new RollingSequence(10);
        regionsPerMinute = new RollingSequence(10);
        chunksPerSecondHistory = new KList<>();
        generated = new AtomicLong(0);
        generatedLast = new AtomicLong(0);
        generatedLastMinute = new AtomicLong(0);
        cached = new AtomicLong();
        cachedLast = new AtomicLong(0);
        cachedLastMinute = new AtomicLong(0);
        totalChunks = new AtomicLong(0);
        task.iterateAllChunks((_a, _b) -> totalChunks.incrementAndGet());
        startTime = new AtomicLong(M.ms());
        ticker = new Looper() {
            @Override
            protected long loop() {
                long eta = computeETA();

                long secondCached = cached.get() - cachedLast.get();
                cachedLast.set(cached.get());
                cachedPerSecond.put(secondCached);

                long secondGenerated = generated.get() - generatedLast.get() - secondCached;
                generatedLast.set(generated.get());
                if (secondCached == 0 || secondGenerated != 0) {
                    chunksPerSecond.put(secondGenerated);
                    chunksPerSecondHistory.add((int) secondGenerated);
                }

                if (minuteLatch.flip()) {
                    long minuteCached = cached.get() - cachedLastMinute.get();
                    cachedLastMinute.set(cached.get());

                    long minuteGenerated = generated.get() - generatedLastMinute.get() - minuteCached;
                    generatedLastMinute.set(generated.get());
                    if (minuteCached == 0 || minuteGenerated != 0) {
                        chunksPerMinute.put(minuteGenerated);
                        regionsPerMinute.put((double) minuteGenerated / 1024D);
                    }
                }
                boolean cached = cachedPerSecond.getAverage() != 0;

                listener.onTick(
                        cached ? cachedPerSecond.getAverage() : chunksPerSecond.getAverage(),
                        chunksPerMinute.getAverage(),
                        regionsPerMinute.getAverage(),
                        (double) generated.get() / (double) totalChunks.get(), generated.get(),
                        totalChunks.get(),
                        totalChunks.get() - generated.get(), eta, M.ms() - startTime.get(), currentGeneratorMethod.get(),
                        cached);

                if (cl.flip()) {
                    double percentage = ((double) generated.get() / (double) totalChunks.get()) * 100;

                    Iris.info("%s: %s of %s (%.0f%%), %s/s ETA: %s",
                            IrisPackBenchmarking.benchmarkInProgress ? "Benchmarking" : "Pregen",
                            Form.f(generated.get()),
                            Form.f(totalChunks.get()),
                            percentage,
                            cached ?
                                    "Cached " + Form.f((int) cachedPerSecond.getAverage()) :
                                    Form.f((int) chunksPerSecond.getAverage()),
                            Form.duration(eta, 2)
                    );
                }
                return 1000;
            }
        };
    }

    private long computeETA() {
        double d = (long) (totalChunks.get() > 1024 ? // Generated chunks exceed 1/8th of total?
                // If yes, use smooth function (which gets more accurate over time since its less sensitive to outliers)
                ((totalChunks.get() - generated.get() - cached.get()) * ((double) (M.ms() - startTime.get()) / ((double) generated.get() - cached.get()))) :
                // If no, use quick function (which is less accurate over time but responds better to the initial delay)
                ((totalChunks.get() - generated.get() - cached.get()) / chunksPerSecond.getAverage()) * 1000
        );
        return Double.isFinite(d) && d != INVALID ? (long) d : 0;
    }


    public void close() {
        shutdown.set(true);
    }

    public void start() {
        init();
        ticker.start();
        checkRegions();
        task.iterateRegions((x, z) -> visitRegion(x, z, true));
        task.iterateRegions((x, z) -> visitRegion(x, z, false));
        shutdown();
        if (!IrisPackBenchmarking.benchmarkInProgress) {
            Iris.info(C.IRIS + "Pregen stopped.");
        } else {
            IrisPackBenchmarking.instance.finishedBenchmark(chunksPerSecondHistory);
        }
    }

    private void checkRegions() {
        task.iterateRegions(this::checkRegion);
    }

    private void init() {
        generator.init();
        generator.save();
    }

    private void shutdown() {
        listener.onSaving();
        generator.close();
        ticker.interrupt();
        listener.onClose();
        Mantle mantle = getMantle();
        if (mantle != null) {
            mantle.trim(0, 0);
        }
    }

    private void visitRegion(int x, int z, boolean regions) {
        while (paused.get() && !shutdown.get()) {
            J.sleep(50);
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
                while (paused.get() && !shutdown.get()) {
                    J.sleep(50);
                }

                generator.generateChunk(xx, zz, listener);
            });
        }

        if (hit) {
            listener.onRegionGenerated(x, z);

            if (saveLatch.flip()) {
                listener.onSaving();
                generator.save();
            }

            generatedRegions.add(pos);
            checkRegions();
        }
    }

    private void checkRegion(int x, int z) {
        if (generatedRegions.contains(new Position2(x, z))) {
            return;
        }

        generator.supportsRegions(x, z, listener);
    }

    public void pause() {
        paused.set(true);
    }

    public void resume() {
        paused.set(false);
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
                if (c) cached.addAndGet(1);
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
