package com.volmit.iris.core.pregenerator;

import com.volmit.iris.Iris;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RollingSequence;
import com.volmit.iris.util.math.Spiraler;
import com.volmit.iris.util.scheduling.J;
import io.papermc.lib.PaperLib;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.io.File;

import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ChunkUpdater {
    private AtomicBoolean paused;
    private AtomicBoolean cancelled;
    private KMap<Chunk, Long> lastUse;
    private KMap<Chunk, AtomicInteger> counters;
    private final RollingSequence chunksPerSecond;
    private final AtomicInteger worldheightsize;
    private final AtomicInteger worldwidthsize;
    private final AtomicInteger totalChunks;
    private final AtomicInteger totalMaxChunks;
    private final AtomicInteger totalMcaregions;
    private final AtomicInteger position;
    private AtomicInteger chunksProcessed;
    private AtomicInteger chunksUpdated;
    private AtomicLong startTime;
    private ExecutorService executor;
    private ExecutorService chunkExecutor;
    private ScheduledExecutorService scheduler;
    private CompletableFuture future;
    private  CountDownLatch latch;
    private final Object pauseLock;
    private final Engine engine;
    private final World world;

    public ChunkUpdater(World world) {
        this.engine = IrisToolbelt.access(world).getEngine();
        this.chunksPerSecond = new RollingSequence(5);
        this.world = world;
        this.lastUse = new KMap<>();
        this.counters = new KMap<>();
        this.worldheightsize = new AtomicInteger(calculateWorldDimensions(new File(world.getWorldFolder(), "region"), 1));
        this.worldwidthsize = new AtomicInteger(calculateWorldDimensions(new File(world.getWorldFolder(), "region"), 0));
        int m = Math.max(worldheightsize.get(), worldwidthsize.get());
        this.executor = Executors.newFixedThreadPool(Math.max(Runtime.getRuntime().availableProcessors() / (System.getProperty("iris.updater") != null ? 1 : 3), 1));
        this.chunkExecutor = Executors.newFixedThreadPool(Math.max(Runtime.getRuntime().availableProcessors() / (System.getProperty("iris.updater") != null ? 1 : 3), 1));
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.future = new CompletableFuture<>();
        this.startTime = new AtomicLong();
        this.worldheightsize.set(m);
        this.worldwidthsize.set(m);
        this.totalMaxChunks = new AtomicInteger((worldheightsize.get() / 16) * (worldwidthsize.get() / 16));
        this.chunksProcessed = new AtomicInteger();
        this.chunksUpdated = new AtomicInteger();
        this.position = new AtomicInteger(0);
        this.latch = new CountDownLatch(totalMaxChunks.get());
        this.paused = new AtomicBoolean(false);
        this.pauseLock = new Object();
        this.cancelled = new AtomicBoolean(false);
        this.totalChunks = new AtomicInteger(0);
        this.totalMcaregions = new AtomicInteger(0);
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
                        long elapsedSeconds = (System.currentTimeMillis() - startTime.get()) / 1000;
                        int processed = chunksProcessed.get();
                        double cps = elapsedSeconds > 0 ? processed / (double) elapsedSeconds : 0;
                        chunksPerSecond.put(cps);
                        double percentage = ((double) chunksProcessed.get() / (double) totalMaxChunks.get()) * 100;
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

            CompletableFuture.runAsync(() -> {
                for (int i = 0; i < totalMaxChunks.get(); i++) {
                    if (paused.get()) {
                        synchronized (pauseLock) {
                            try {
                                pauseLock.wait();
                            } catch (InterruptedException e) {
                                Iris.error("Interrupted while waiting for executor: ");
                                e.printStackTrace();
                                break;
                            }
                        }
                    }
                    executor.submit(() -> {
                        if (!cancelled.get()) {
                            processNextChunk();   
                        }
                        latch.countDown();
                    });
                }
            }).thenRun(() -> {
                try {
                    latch.await();
                    close();
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            chunkExecutor.shutdown();
            chunkExecutor.awaitTermination(5, TimeUnit.SECONDS);
            scheduler.shutdownNow();
            unloadAndSaveAllChunks();
        } catch (Exception ignored) {
        }
        if (cancelled.get()) {
            Iris.info("Updated: " + Form.f(chunksUpdated.get()) + " Chunks");
            Iris.info("Irritated: " + Form.f(chunksProcessed.get()) + " of " + Form.f(totalMaxChunks.get()));
            Iris.info("Stopped updater.");
        } else {
            Iris.info("Processed: " + Form.f(chunksProcessed.get()) + " Chunks");
            Iris.info("Finished Updating: " + Form.f(chunksUpdated.get()) + " Chunks");
        }
    }

    private void processNextChunk() {
        int pos = position.getAndIncrement();
        int[] coords = getChunk(pos);
        if (loadChunksIfGenerated(coords[0], coords[1])) {
            Chunk c = world.getChunkAt(coords[0], coords[1]);
            engine.getMantle().getMantle().getChunk(c);
            engine.updateChunk(c);

            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    var chunk = world.getChunkAt(coords[0] + x, coords[1] + z, false);
                    var counter = counters.get(chunk);
                    if (counter != null) counter.decrementAndGet();
                }
            }
            chunksUpdated.incrementAndGet();
        }
        chunksProcessed.getAndIncrement();
    }

    private boolean loadChunksIfGenerated(int x, int z) {
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
                chunkExecutor.submit(() -> {
                    try {
                        Chunk c;
                        try {
                            c = PaperLib.getChunkAtAsync(world, xx, zz, false)
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

    public int calculateWorldDimensions(File regionDir, Integer o) {
        File[] files = regionDir.listFiles((dir, name) -> name.endsWith(".mca"));

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (File file : files) {
            String[] parts = file.getName().split("\\.");
            int x = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);

            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (z < minZ) minZ = z;
            if (z > maxZ) maxZ = z;
        }

        int height = (maxX - minX + 1) * 32 * 16;
        int width = (maxZ - minZ + 1) * 32 * 16;

        if (o == 1) {
            return height;
        }
        if (o == 0) {
            return width;
        }
        return 0;
    }

    public int[] getChunk(int position) {
        int p = -1;
        AtomicInteger xx = new AtomicInteger();
        AtomicInteger zz = new AtomicInteger();
        Spiraler s = new Spiraler(worldheightsize.get() * 2, worldwidthsize.get() * 2, (x, z) -> {
            xx.set(x);
            zz.set(z);
        });

        while (s.hasNext() && p++ < position) {
            s.next();
        }
        int[] coords = new int[2];
        coords[0] = xx.get();
        coords[1] = zz.get();

        return coords;
    }
}
