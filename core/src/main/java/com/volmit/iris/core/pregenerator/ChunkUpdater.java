package com.volmit.iris.core.pregenerator;

import com.volmit.iris.Iris;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RollingSequence;
import com.volmit.iris.util.math.Spiraler;
import io.papermc.lib.PaperLib;
import org.bukkit.Chunk;
import org.bukkit.World;


import java.io.File;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ChunkUpdater {
    private AtomicBoolean cancelled;
    private KList<int[]> chunkMap;
    private final RollingSequence chunksPerSecond;
    private final RollingSequence mcaregionsPerSecond;
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
    private ScheduledExecutorService scheduler;
    private final File[] McaFiles;
    private final Engine engine;
    private final World world;

    public ChunkUpdater(World world) {
        File cacheDir = new File("plugins" + File.separator + "iris" + File.separator + "cache");
        File chunkCacheDir = new File("plugins" + File.separator + "iris" + File.separator + "cache" + File.separator + "spiral");
        this.engine = IrisToolbelt.access(world).getEngine();
        this.chunksPerSecond = new RollingSequence(10);
        this.mcaregionsPerSecond = new RollingSequence(10);
        this.world = world;
        this.chunkMap = new KList<>();
        this.McaFiles = new File(world.getWorldFolder(), "region").listFiles((dir, name) -> name.endsWith(".mca"));
        this.worldheightsize = new AtomicInteger(calculateWorldDimensions(new File(world.getWorldFolder(), "region"), 1));
        this.worldwidthsize = new AtomicInteger(calculateWorldDimensions(new File(world.getWorldFolder(), "region"), 0));
        int m = Math.max(worldheightsize.get(), worldwidthsize.get());
        this.executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() - 2);
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.startTime = new AtomicLong();
        this.worldheightsize.set(m);
        this.worldwidthsize.set(m);
        this.totalMaxChunks = new AtomicInteger((worldheightsize.get() / 16) * (worldwidthsize.get() / 16));
        this.chunksProcessed = new AtomicInteger();
        this.chunksUpdated = new AtomicInteger();
        this.position = new AtomicInteger(0);
        this.cancelled = new AtomicBoolean(false);
        this.totalChunks = new AtomicInteger(0);
        this.totalMcaregions = new AtomicInteger(0);
    }

    public int getChunks() {
        return totalMaxChunks.get();
    }

    public void start() {
        update();
    }


    private void update() {
        Iris.info("Updating..");
        try {
            startTime.set(System.currentTimeMillis());
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    long eta = computeETA();
                    long elapsedSeconds = (System.currentTimeMillis() - startTime.get()) / 1000;
                    int processed = chunksProcessed.get();
                    double cps = elapsedSeconds > 0 ? processed / (double) elapsedSeconds : 0;
                    chunksPerSecond.put(cps);
                    double percentage = ((double) chunksProcessed.get() / (double) totalMaxChunks.get()) * 100;
                    Iris.info("Updated: " + Form.f(processed) + " of : " + Form.f(totalMaxChunks.get()) + " (%.0f%%) " + Form.f(chunksPerSecond.getAverage()) + "/s, ETA: " + Form.duration(eta, 2), percentage);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 0, 3, TimeUnit.SECONDS);

            for (int i = 0; i < totalMaxChunks.get(); i++) {
                executor.submit(this::processNextChunk);
            }

            executor.shutdown();
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            scheduler.shutdownNow();
            Iris.info("Processed: " + Form.f(chunksProcessed.get()) + " Chunks");
            Iris.info("Finished Updating: " + Form.f(chunksUpdated.get()) + " Chunks");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processNextChunk() {
        int pos = position.getAndIncrement();
        int[] coords = getChunk(pos);
        if (PaperLib.isChunkGenerated(world, coords[0], coords[1])) {
            Chunk chunk = world.getChunkAt(coords[0], coords[1]);
            engine.updateChunk(chunk);
            chunksUpdated.getAndIncrement();
        }
        chunksProcessed.getAndIncrement();
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
