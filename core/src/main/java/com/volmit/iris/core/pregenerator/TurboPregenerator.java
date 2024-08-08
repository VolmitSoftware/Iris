/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

import com.google.gson.Gson;
import com.volmit.iris.Iris;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.math.RollingSequence;
import com.volmit.iris.util.math.Spiraler;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.HyperLock;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import io.papermc.lib.PaperLib;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;

public class TurboPregenerator extends Thread implements Listener {
    private static final Map<String, TurboPregenJob> jobs = new HashMap<>();
    @Getter
    private static TurboPregenerator instance;
    private static AtomicInteger turboGeneratedChunks;
    private final TurboPregenJob job;
    private final File destination;
    private final int maxPosition;
    private final ChronoLatch latch;
    private final AtomicInteger generatedLast;
    private final AtomicLong cachedLast;
    private final RollingSequence cachePerSecond;
    private final AtomicInteger turboTotalChunks;
    private final AtomicLong startTime;
    private final RollingSequence chunksPerSecond;
    private final RollingSequence chunksPerMinute;
    private final HyperLock hyperLock;
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private World world;
    private KList<Position2> queue;
    private ConcurrentHashMap<Integer, Position2> cache;
    private AtomicInteger maxWaiting;
    private ReentrantLock cachinglock;
    private AtomicBoolean caching;
    private MultiBurst burst;

    public TurboPregenerator(TurboPregenJob job, File destination) {
        this.job = job;
        queue = new KList<>(512);
        this.maxWaiting = new AtomicInteger(128);
        this.destination = destination;
        this.maxPosition = new Spiraler(job.getRadiusBlocks() * 2, job.getRadiusBlocks() * 2, (x, z) -> {
        }).count();
        this.world = Bukkit.getWorld(job.getWorld());
        this.latch = new ChronoLatch(3000);
        this.burst = MultiBurst.burst;
        this.hyperLock = new HyperLock();
        this.startTime = new AtomicLong(M.ms());
        this.cachePerSecond = new RollingSequence(10);
        this.chunksPerSecond = new RollingSequence(10);
        this.chunksPerMinute = new RollingSequence(10);
        turboGeneratedChunks = new AtomicInteger(0);
        this.generatedLast = new AtomicInteger(0);
        this.cachedLast = new AtomicLong(0);
        this.caching = new AtomicBoolean(false);
        this.turboTotalChunks = new AtomicInteger((int) Math.ceil(Math.pow((2.0 * job.getRadiusBlocks()) / 16, 2)));
        cache = new ConcurrentHashMap<>(turboTotalChunks.get());
        this.cachinglock = new ReentrantLock();
        jobs.put(job.getWorld(), job);
        TurboPregenerator.instance = this;
    }

    public TurboPregenerator(File file) throws IOException {
        this(new Gson().fromJson(IO.readAll(file), TurboPregenerator.TurboPregenJob.class), file);
    }

    public static void loadTurboGenerator(String i) {
        World x = Bukkit.getWorld(i);
        File turbogen = new File(x.getWorldFolder(), "turbogen.json");
        if (turbogen.exists()) {
            try {
                TurboPregenerator p = new TurboPregenerator(turbogen);
                p.start();
                Iris.info("Started Turbo Pregenerator: " + p.job);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

    }

    public static void setPausedTurbo(World world) {
        TurboPregenJob job = jobs.get(world.getName());
        if (isPausedTurbo(world)) {
            job.paused = false;
        } else {
            job.paused = true;
        }

        if (job.paused) {
            Iris.info(C.BLUE + "TurboGen: " + C.IRIS + world.getName() + C.BLUE + " Paused");
        } else {
            Iris.info(C.BLUE + "TurboGen: " + C.IRIS + world.getName() + C.BLUE + " Resumed");
        }
    }

    public static boolean isPausedTurbo(World world) {
        TurboPregenJob job = jobs.get(world.getName());
        return job != null && job.isPaused();
    }

    @EventHandler
    public void on(WorldUnloadEvent e) {
        if (e.getWorld().equals(world)) {
            interrupt();
        }
    }

    public void run() {
        while (!interrupted()) {
            tick();
        }

        try {
            saveNow();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void tick() {
        TurboPregenJob job = jobs.get(world.getName());
        if (!cachinglock.isLocked() && cache.isEmpty() && !caching.get()) {
            ExecutorService cache = Executors.newFixedThreadPool(1);
            cache.submit(this::cache);
        }

        if (latch.flip() && caching.get()) {
            long secondCached = cache.mappingCount() - cachedLast.get();
            cachedLast.set(cache.mappingCount());
            secondCached = secondCached / 3;
            cachePerSecond.put(secondCached);
            Iris.info("TurboGen: " + C.IRIS + world.getName() + C.RESET + C.BLUE + " Caching: " + Form.f(cache.mappingCount()) + " of " + Form.f(turboTotalChunks.get()) + " " + Form.f((int) cachePerSecond.getAverage()) + "/s");
        }

        if (latch.flip() && !job.paused && !cachinglock.isLocked()) {
            long eta = computeETA();
            save();
            int secondGenerated = turboGeneratedChunks.get() - generatedLast.get();
            generatedLast.set(turboGeneratedChunks.get());
            secondGenerated = secondGenerated / 3;
            chunksPerSecond.put(secondGenerated);
            chunksPerMinute.put(secondGenerated * 60);
            Iris.info("TurboGen: " + C.IRIS + world.getName() + C.RESET + " RTT: " + Form.f(turboGeneratedChunks.get()) + " of " + Form.f(turboTotalChunks.get()) + " " + Form.f((int) chunksPerSecond.getAverage()) + "/s ETA: " + Form.duration((double) eta, 2));

        }
        if (turboGeneratedChunks.get() >= turboTotalChunks.get()) {
            Iris.info("Completed Turbo Gen!");
            interrupt();
        } else {
            if (!cachinglock.isLocked()) {
                int pos = job.getPosition() + 1;
                job.setPosition(pos);
                if (!job.paused) {
                    if (queue.size() < maxWaiting.get()) {
                        Position2 chunk = cache.get(pos);
                        queue.add(chunk);
                    }
                    waitForChunksPartial();
                }
            }
        }
    }

    private void cache() {
        if (!cachinglock.isLocked()) {
            cachinglock.lock();
            caching.set(true);
            PrecisionStopwatch p = PrecisionStopwatch.start();
            BurstExecutor b = MultiBurst.burst.burst(turboTotalChunks.get());
            b.setMulticore(true);
            int[] list = IntStream.rangeClosed(0, turboTotalChunks.get()).toArray();
            AtomicInteger order = new AtomicInteger(turboTotalChunks.get());

            int threads = Runtime.getRuntime().availableProcessors();
            if (threads > 1) threads--;
            ExecutorService process = Executors.newFixedThreadPool(threads);

            for (int id : list) {
                b.queue(() -> {
                    cache.put(id, getChunk(id));
                    order.addAndGet(-1);
                });
            }
            b.complete();

            if (order.get() < 0) {
                cachinglock.unlock();
                caching.set(false);
                Iris.info("Completed Caching in: " + Form.duration(p.getMilliseconds(), 2));
            }
        } else {
            Iris.error("TurboCache is locked!");
        }
    }

    private void waitForChunksPartial() {
        while (!queue.isEmpty() && maxWaiting.get() > queue.size()) {
            try {
                for (Position2 c : new KList<>(queue)) {
                    tickGenerate(c);
                    queue.remove(c);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private long computeETA() {
        return (long) ((turboTotalChunks.get() - turboGeneratedChunks.get()) / chunksPerMinute.getAverage()) * 1000;
        // todo broken
    }

    private void tickGenerate(Position2 chunk) {
        executorService.submit(() -> {
            CountDownLatch latch = new CountDownLatch(1);
            PaperLib.getChunkAtAsync(world, chunk.getX(), chunk.getZ(), true)
                    .thenAccept((i) -> {
                        latch.countDown();
                    });
            try {
                latch.await();
            } catch (InterruptedException ignored) {
            }
            turboGeneratedChunks.addAndGet(1);
        });
    }

    public Position2 getChunk(int position) {
        int p = -1;
        AtomicInteger xx = new AtomicInteger();
        AtomicInteger zz = new AtomicInteger();
        Spiraler s = new Spiraler(job.getRadiusBlocks() * 2, job.getRadiusBlocks() * 2, (x, z) -> {
            xx.set(x);
            zz.set(z);
        });

        while (s.hasNext() && p++ < position) {
            s.next();
        }

        return new Position2(xx.get(), zz.get());
    }

    public void save() {
        J.a(() -> {
            try {
                saveNow();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        });
    }

    public void shutdownInstance(World world) throws IOException {
        Iris.info("turboGen: " + C.IRIS + world.getName() + C.BLUE + " Shutting down..");
        TurboPregenJob job = jobs.get(world.getName());
        File worldDirectory = new File(Bukkit.getWorldContainer(), world.getName());
        File turboFile = new File(worldDirectory, "turbogen.json");

        if (job == null) {
            Iris.error("No turbogen job found for world: " + world.getName());
            return;
        }

        try {
            if (!job.isPaused()) {
                job.setPaused(true);
            }
            save();
            jobs.remove(world.getName());
            new BukkitRunnable() {
                @Override
                public void run() {
                    while (turboFile.exists()) {
                        turboFile.delete();
                        J.sleep(1000);
                    }
                    Iris.info("turboGen: " + C.IRIS + world.getName() + C.BLUE + " File deleted and instance closed.");
                }
            }.runTaskLater(Iris.instance, 20L);
        } catch (Exception e) {
            Iris.error("Failed to shutdown turbogen for " + world.getName());
            e.printStackTrace();
        } finally {
            saveNow();
            interrupt();
        }
    }


    public void saveNow() throws IOException {
        IO.writeAll(this.destination, new Gson().toJson(job));
    }

    @Data
    @Builder
    public static class TurboPregenJob {
        @Builder.Default
        boolean paused = false;
        private String world;
        @Builder.Default
        private int radiusBlocks = 5000;
        @Builder.Default
        private int position = 0;
    }
}

