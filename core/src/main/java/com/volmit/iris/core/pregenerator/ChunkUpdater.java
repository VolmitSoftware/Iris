package com.volmit.iris.core.pregenerator;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.service.PreservationSVC;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.mantle.flag.MantleFlag;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.math.RollingSequence;
import com.volmit.iris.util.plugin.chunk.TicketHolder;
import com.volmit.iris.util.profile.LoadBalancer;
import com.volmit.iris.util.scheduling.J;
import io.papermc.lib.PaperLib;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.io.File;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ChunkUpdater {
    private static final String REGION_PATH = "region" + File.separator + "r.";
    private final AtomicBoolean paused = new AtomicBoolean();
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final TicketHolder holder;
    private final RollingSequence chunksPerSecond = new RollingSequence(5);
    private final AtomicInteger totalMaxChunks = new AtomicInteger();
    private final AtomicInteger chunksProcessed = new AtomicInteger();
    private final AtomicInteger chunksProcessedLast = new AtomicInteger();
    private final AtomicInteger chunksUpdated = new AtomicInteger();
    private final AtomicBoolean serverEmpty = new AtomicBoolean(true);
    private final AtomicLong lastCpsTime = new AtomicLong(M.ms());
    private final int maxConcurrency = IrisSettings.get().getUpdater().getMaxConcurrency();
    private final int coreLimit = (int) Math.max(Runtime.getRuntime().availableProcessors() * IrisSettings.get().getUpdater().getThreadMultiplier(), 1);
    private final Semaphore semaphore = new Semaphore(maxConcurrency);
    private final LoadBalancer loadBalancer = new LoadBalancer(semaphore, maxConcurrency, IrisSettings.get().getUpdater().emptyMsRange);
    private final AtomicLong startTime = new AtomicLong();
    private final Dimensions dimensions;
    private final PregenTask task;
    private final ExecutorService chunkExecutor = IrisSettings.get().getUpdater().isNativeThreads() ? Executors.newFixedThreadPool(coreLimit) : Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService scheduler  = Executors.newScheduledThreadPool(1);
    private final CountDownLatch latch;
    private final Engine engine;
    private final World world;

    public ChunkUpdater(World world) {
        this.engine = IrisToolbelt.access(world).getEngine();
        this.world = world;
        this.holder = Iris.tickets.getHolder(world);
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

            Iris.service(PreservationSVC.class).register(t);
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

        var mc = engine.getMantle().getMantle().getChunk(x, z).use();
        try {
            Chunk c = world.getChunkAt(x, z);
            engine.updateChunk(c);

            removeTickets(x, z);
        } finally {
            chunksUpdated.incrementAndGet();
            chunksProcessed.getAndIncrement();
            mc.release();
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
                PaperLib.getChunkAtAsync(world, xx, zz, false, true)
                        .thenAccept(chunk -> {
                            if (chunk == null || !chunk.isGenerated()) {
                                latch.countDown();
                                generated.set(false);
                                return;
                            }
                            holder.addTicket(chunk);
                            latch.countDown();
                        });
            }
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Iris.info("Interrupted while waiting for chunks to load");
        }

        if (generated.get()) return true;
        removeTickets(x, z);
        return false;
    }

    private void removeTickets(int x, int z) {
        for (int xx = -1; xx <= 1; xx++) {
            for (int zz = -1; zz <= 1; zz++) {
                holder.removeTicket(x + xx, z + zz);
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
