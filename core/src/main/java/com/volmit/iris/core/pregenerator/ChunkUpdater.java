package com.volmit.iris.core.pregenerator;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.nms.container.Pair;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.mantle.flag.MantleFlag;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.math.RollingSequence;
import com.volmit.iris.util.profile.LoadBalancer;
import com.volmit.iris.util.scheduling.J;
import io.papermc.lib.PaperLib;
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
    private static final String REGION_PATH = "region" + File.separator + "r.";
    private final AtomicBoolean paused = new AtomicBoolean();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final KMap<Long, Pair<Long, AtomicInteger>> lastUse = new KMap<>();
    private final RollingSequence chunksPerSecond = new RollingSequence(5);
    private final AtomicInteger totalMaxChunks = new AtomicInteger();
    private final AtomicInteger chunksProcessed = new AtomicInteger();
    private final AtomicInteger chunksProcessedLast = new AtomicInteger();
    private final AtomicInteger chunksUpdated = new AtomicInteger();
    private final AtomicBoolean serverEmpty = new AtomicBoolean(true);
    private final AtomicLong lastCpsTime = new AtomicLong(M.ms());
    private final int maxConcurrency = IrisSettings.get().getUpdater().getMaxConcurrency();
    private final Semaphore semaphore = new Semaphore(maxConcurrency);
    private final LoadBalancer loadBalancer = new LoadBalancer(semaphore, maxConcurrency, IrisSettings.get().getUpdater().emptyMsRange);
    private final AtomicLong startTime = new AtomicLong();
    private final Dimensions dimensions;
    private final PregenTask task;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ExecutorService chunkExecutor = Executors.newVirtualThreadPerTaskExecutor();
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

    public String getName() {
        return world.getName();
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
                    Iris.reportError(e);
                    e.printStackTrace();
                }
            }, 0, 3, TimeUnit.SECONDS);
            scheduler.scheduleAtFixedRate(this::unloadChunks, 0, 1, TimeUnit.SECONDS);
            scheduler.scheduleAtFixedRate(() -> {
                boolean empty = Bukkit.getOnlinePlayers().isEmpty();
                if (serverEmpty.getAndSet(empty) == empty)
                    return;
                loadBalancer.setRange(empty ? IrisSettings.get().getUpdater().emptyMsRange : IrisSettings.get().getUpdater().defaultMsRange);
            }, 0, 10, TimeUnit.SECONDS);

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
            loadBalancer.close();
            semaphore.acquire(256);

            chunkExecutor.shutdown();
            chunkExecutor.awaitTermination(5, TimeUnit.SECONDS);
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
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

            if (rX < dimensions.min.getX() ||
                    rX > dimensions.max.getX() ||
                    rZ < dimensions.min.getZ() ||
                    rZ > dimensions.max.getZ() ||
                    !new File(world.getWorldFolder(), REGION_PATH + rX + "." + rZ + ".mca").exists()
            ) return;

            task.iterateChunks(rX, rZ, (x, z) -> {
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
                    var counter = lastUse.get(Cache.key(x + xx, z + zz));
                    if (counter != null) counter.getB().decrementAndGet();
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
                                        if (chunk != null)
                                            chunk.addPluginChunkTicket(Iris.instance);
                                        return chunk;
                                    }).get();
                        } catch (InterruptedException | ExecutionException e) {
                            generated.set(false);
                            return;
                        }

                        if (c == null) {
                            generated.set(false);
                            return;
                        }

                        if (!c.isLoaded()) {
                            var future = J.sfut(() -> c.load(false));
                            if (future != null) future.join();
                        }

                        if (!PaperLib.isChunkGenerated(c.getWorld(), xx, zz))
                            generated.set(false);

                        var pair = lastUse.computeIfAbsent(Cache.key(c), k -> new Pair<>(0L, new AtomicInteger(-1)));
                        pair.setA(M.ms());
                        pair.getB().updateAndGet(i -> i == -1 ? 1 : ++i);
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
        for (var key : new ArrayList<>(lastUse.keySet())) {
            if (key == null) continue;
            var pair = lastUse.get(key);
            if (pair == null) continue;
            var lastUseTime = pair.getA();
            var counter = pair.getB();
            if (lastUseTime == null || counter == null)
                continue;

            if (M.ms() - lastUseTime >= 5000 && counter.get() == 0) {
                int x = Cache.keyX(key);
                int z = Cache.keyZ(key);
                J.s(() -> {
                    world.removePluginChunkTicket(x, z, Iris.instance);
                    world.unloadChunk(x, z);
                    lastUse.remove(key);
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
            Iris.reportError(e);
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
                .radiusZ((int) Math.ceil(width / 2d * 512))
                .radiusX((int) Math.ceil(height / 2d * 512))
                .center(new Position2(oX, oZ))
                .build());
    }

    private record Dimensions(Position2 min, Position2 max, int count, PregenTask task) { }
}
