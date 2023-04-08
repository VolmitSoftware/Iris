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
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.math.RollingSequence;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.Looper;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class IrisPregenerator {
    private final PregenTask task;
    private final PregeneratorMethod generator;
    private final PregenListener listener;
    private final Looper ticker;
    private final AtomicBoolean paused;
    private final AtomicBoolean shutdown;
    private final RollingSequence chunksPerSecond;
    private final RollingSequence chunksPerMinute;
    private final RollingSequence regionsPerMinute;
    private final AtomicInteger generated;
    private final AtomicInteger generatedLast;
    private final AtomicInteger generatedLastMinute;
    private final AtomicInteger totalChunks;
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
        chunksPerSecond = new RollingSequence(10);
        chunksPerMinute = new RollingSequence(10);
        regionsPerMinute = new RollingSequence(10);
        generated = new AtomicInteger(0);
        generatedLast = new AtomicInteger(0);
        generatedLastMinute = new AtomicInteger(0);
        totalChunks = new AtomicInteger(0);
        task.iterateRegions((_a, _b) -> totalChunks.addAndGet(1024));
        startTime = new AtomicLong(M.ms());
        ticker = new Looper() {
            @Override
            protected long loop() {
                long eta = computeETA();
                int secondGenerated = generated.get() - generatedLast.get();
                generatedLast.set(generated.get());
                chunksPerSecond.put(secondGenerated);

                if (minuteLatch.flip()) {
                    int minuteGenerated = generated.get() - generatedLastMinute.get();
                    generatedLastMinute.set(generated.get());
                    chunksPerMinute.put(minuteGenerated);
                    regionsPerMinute.put((double) minuteGenerated / 1024D);
                }

                listener.onTick(chunksPerSecond.getAverage(), chunksPerMinute.getAverage(),
                        regionsPerMinute.getAverage(),
                        (double) generated.get() / (double) totalChunks.get(),
                        generated.get(), totalChunks.get(),
                        totalChunks.get() - generated.get(),
                        eta, M.ms() - startTime.get(), currentGeneratorMethod.get());

                if (cl.flip()) {
                    double percentage = ((double) generated.get() / (double) totalChunks.get()) * 100;
                    Iris.info("Pregen: " + Form.f(generated.get()) + " of " + Form.f(totalChunks.get()) + " (%.0f%%) " + Form.f((int) chunksPerSecond.getAverage()) + "/s ETA: " + Form.duration((double) eta, 2), percentage);
                }

                return 1000;
            }
        };
    }

    private long computeETA() {
        return (long) ((totalChunks.get() - generated.get()) *
                ((double) (M.ms() - startTime.get()) / (double) generated.get()));
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
        getMantle().trim(0);
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
            PregenTask.iterateRegion(x, z, (xx, zz) -> {
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
            public void onTick(double chunksPerSecond, double chunksPerMinute, double regionsPerMinute, double percent, int generated, int totalChunks, int chunksRemaining, long eta, long elapsed, String method) {
                listener.onTick(chunksPerSecond, chunksPerMinute, regionsPerMinute, percent, generated, totalChunks, chunksRemaining, eta, elapsed, method);
            }

            @Override
            public void onChunkGenerating(int x, int z) {
                listener.onChunkGenerating(x, z);
            }

            @Override
            public void onChunkGenerated(int x, int z) {
                listener.onChunkGenerated(x, z);
                generated.addAndGet(1);
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
