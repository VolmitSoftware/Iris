package com.volmit.iris.core.pregenerator;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.mantle.MantleFlag;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.math.RollingSequence;
import com.volmit.iris.util.scheduling.J;
import io.papermc.lib.PaperLib;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.io.File;

import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ChunkUpdater {
    private final AtomicBoolean paused = new AtomicBoolean();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final KMap<Chunk, Long> lastUse = new KMap<>();
    private final KMap<Chunk, AtomicInteger> counters = new KMap<>();
    private final RollingSequence chunksPerSecond = new RollingSequence(5);
    private final AtomicInteger totalMaxChunks = new AtomicInteger();
    private final AtomicInteger chunksProcessed = new AtomicInteger();
    private final AtomicInteger chunksProcessedLast = new AtomicInteger();
    private final AtomicInteger chunksUpdated = new AtomicInteger();
    private final AtomicLong lastCpsTime = new AtomicLong(M.ms());
    private final int coreLimit = (int) Math.max(Runtime.getRuntime().availableProcessors() * getProperty(), 1);
    private final Semaphore semaphore = new Semaphore(256);
    private final PlayerCounter playerCounter = new PlayerCounter(semaphore, 256);
    private final AtomicLong startTime = new AtomicLong();
    private final Dimensions dimensions;
    private final PregenTask task;
    private final ExecutorService executor = Executors.newFixedThreadPool(coreLimit);
    private final ExecutorService chunkExecutor = Executors.newFixedThreadPool(coreLimit);
    private final ScheduledExecutorService scheduler  = Executors.newScheduledThreadPool(1);
    private final CountDownLatch latch;
    private final Engine engine;
    private final World world;

    public ChunkUpdater(World world) {
        this.engine = IrisToolbelt.access(world).getEngine();
        this.world = world;
        this.dimensions = calculateWorldDimensions(new File(world.getWorldFolder(), "region"));
        this.task = dimensions.task();
        this.totalMaxChunks.set(dimensions.count * 1024);
        this.latch = new CountDownLatch(totalMaxChunks.get());
    }

    public int getChunks() {
        return totalMaxChunks.get();
    }

    public void start() {
        unloadAndSaveAllChunks();
        update();
    }

    public boolean pause() {
        unloadAndSaveAllChunks();
        if (paused.get()) {
            paused.set(false);
            return false;
        } else {
            paused.set(true);
            return true;
        }
    }

    public void stop() {
        unloadAndSaveAllChunks();
        cancelled.set(true);
    }


    private void update() {
        Iris.info("Updating..");
        try {
            startTime.set(System.currentTimeMillis());
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    if (!paused.get()) {
                        long eta = computeETA();
                        int processed = chunksProcessed.get();
                        double last = processed - chunksProcessedLast.getAndSet(processed);
                        double cps = last / ((M.ms() - lastCpsTime.getAndSet(M.ms())) / 1000d);
                        chunksPerSecond.put(cps);
                        double percentage = ((double) processed / (double) totalMaxChunks.get()) * 100;
                        if (!cancelled.get()) {
                            Iris.info("Updated: " + Form.f(processed) + " of " + Form.f(totalMaxChunks.get()) + " (%.0f%%) " + Form.f(chunksPerSecond.getAverage()) + "/s, ETA: " + Form.duration(eta,
                                    2), percentage);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 0, 3, TimeUnit.SECONDS);
            scheduler.scheduleAtFixedRate(this::unloadChunks, 0, 1, TimeUnit.SECONDS);
            scheduler.scheduleAtFixedRate(playerCounter::update, 0, 5, TimeUnit.SECONDS);

            var t = new Thread(() -> {
                run();
                close();
            }, "Iris Chunk Updater - " + world.getName());
            t.setPriority(Thread.MAX_PRIORITY);
            t.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            playerCounter.close();
            semaphore.acquire(256);

            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            chunkExecutor.shutdown();
            chunkExecutor.awaitTermination(5, TimeUnit.SECONDS);
            scheduler.shutdownNow();
            unloadAndSaveAllChunks();
        } catch (Exception ignored) {}
        if (cancelled.get()) {
            Iris.info("Updated: " + Form.f(chunksUpdated.get()) + " Chunks");
            Iris.info("Irritated: " + Form.f(chunksProcessed.get()) + " of " + Form.f(totalMaxChunks.get()));
            Iris.info("Stopped updater.");
        } else {
            Iris.info("Processed: " + Form.f(chunksProcessed.get()) + " Chunks");
            Iris.info("Finished Updating: " + Form.f(chunksUpdated.get()) + " Chunks");
        }
    }

    private void run() {
        task.iterateRegions((rX, rZ) -> {
            if (cancelled.get())
                return;

            while (paused.get()) {
                J.sleep(50);
            }

            if (rX < dimensions.min.getX() || rX > dimensions.max.getX() || rZ < dimensions.min.getZ() || rZ > dimensions.max.getZ()) {
                return;
            }

            PregenTask.iterateRegion(rX, rZ, (x, z) -> {
                while (paused.get() && !cancelled.get()) {
                    J.sleep(50);
                }

                try {
                    semaphore.acquire();
                } catch (InterruptedException ignored) {
                    return;
                }
                chunkExecutor.submit(() -> {
                    try {
                        if (!cancelled.get())
                            processChunk(x, z);
                    } finally {
                        latch.countDown();
                        semaphore.release();
                    }
                });
            });
        });
    }

    private void processChunk(int x, int z) {
        if (!loadChunksIfGenerated(x, z)) {
            chunksProcessed.getAndIncrement();
            return;
        }

        try {
            Chunk c = world.getChunkAt(x, z);
            engine.getMantle().getMantle().getChunk(c);
            engine.updateChunk(c);

            for (int xx = -1; xx <= 1; xx++) {
                for (int zz = -1; zz <= 1; zz++) {
                    var chunk = world.getChunkAt(x + xx, z + zz, false);
                    var counter = counters.get(chunk);
                    if (counter != null) counter.decrementAndGet();
                }
            }
        } finally {
            chunksUpdated.incrementAndGet();
            chunksProcessed.getAndIncrement();
        }
    }

    private boolean loadChunksIfGenerated(int x, int z) {
        if (engine.getMantle().getMantle().hasFlag(x, z, MantleFlag.ETCHED))
            return false;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!PaperLib.isChunkGenerated(world, x + dx, z + dz)) {
                    return false;
                }
            }
        }

        AtomicBoolean generated = new AtomicBoolean(true);
        CountDownLatch latch = new CountDownLatch(9);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int xx = x + dx;
                int zz = z + dz;
                executor.submit(() -> {
                    try {
                        Chunk c;
                        try {
                            c = PaperLib.getChunkAtAsync(world, xx, zz, false, true)
                                    .thenApply(chunk -> {
                                        chunk.addPluginChunkTicket(Iris.instance);
                                        return chunk;
                                    }).get();
                        } catch (InterruptedException | ExecutionException e) {
                            generated.set(false);
                            return;
                        }

                        if (!c.isLoaded()) {
                            var future = J.sfut(() -> c.load(false));
                            if (future != null) future.join();
                        }

                        if (!c.isGenerated())
                            generated.set(false);

                        counters.computeIfAbsent(c, k -> new AtomicInteger(-1))
                                .updateAndGet(i -> i == -1 ? 1 : ++i);
                        lastUse.put(c, M.ms());
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Iris.info("Interrupted while waiting for chunks to load");
        }
        return generated.get();
    }

    private synchronized void unloadChunks() {
        for (Chunk i : new ArrayList<>(lastUse.keySet())) {
            Long lastUseTime = lastUse.get(i);
            var counter = counters.get(i);
            if (lastUseTime != null && M.ms() - lastUseTime >= 5000 && (counter == null || counter.get() == 0)) {
                J.s(() -> {
                    i.removePluginChunkTicket(Iris.instance);
                    i.unload();
                    lastUse.remove(i);
                    counters.remove(i);
                });
            }
        }
    }

    private void unloadAndSaveAllChunks() {
        try {
            J.sfut(() -> {
                if (world == null) {
                    Iris.warn("World was null somehow...");
                    return;
                }

                unloadChunks();
                world.save();
            }).get();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private long computeETA() {
        return (long) (totalMaxChunks.get() > 1024 ? // Generated chunks exceed 1/8th of total?
                // If yes, use smooth function (which gets more accurate over time since its less sensitive to outliers)
                ((totalMaxChunks.get() - chunksProcessed.get()) * ((double) (M.ms() - startTime.get()) / (double) chunksProcessed.get())) :
                // If no, use quick function (which is less accurate over time but responds better to the initial delay)
                ((totalMaxChunks.get() - chunksProcessed.get()) / chunksPerSecond.getAverage()) * 1000
        );
    }

    private Dimensions calculateWorldDimensions(File regionDir) {
        File[] files = regionDir.listFiles((dir, name) -> name.endsWith(".mca"));

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (File file : files) {
            String[] parts = file.getName().split("\\.");
            int x = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);

            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
        int oX = minX + ((maxX - minX) / 2);
        int oZ = minZ + ((maxZ - minZ) / 2);

        int height = maxX - minX + 1;
        int width = maxZ - minZ + 1;

        return new Dimensions(new Position2(minX, minZ), new Position2(maxX, maxZ), height * width, PregenTask.builder()
                .width((int) Math.ceil(width / 2d))
                .height((int) Math.ceil(height / 2d))
                .center(new Position2(oX, oZ))
                .build());
    }

    private record Dimensions(Position2 min, Position2 max, int count, PregenTask task) { }

    @Data
    private static class PlayerCounter {
        private final Semaphore semaphore;
        private final int maxPermits;
        private int lastCount = 0;
        private int permits = 0;

        public void update() {
            double count = Bukkit.getOnlinePlayers().size();
            if (count == lastCount)
                return;
            double p = count == 0 ? 0 : count / (Bukkit.getMaxPlayers() / 2d);
            int targetPermits = (int) (maxPermits * p);

            int diff = targetPermits - permits;
            permits = targetPermits;
            lastCount = (int) count;
            try {
                if (diff > 0) semaphore.release(diff);
                else semaphore.acquire(Math.abs(diff));
            } catch (InterruptedException ignored) {}
        }

        public void close() {
            semaphore.release(permits);
        }
    }

    private static double getProperty() {
        return IrisSettings.get().getPerformance().getUpdaterThreadMultiplier();
    }
}
