package com.volmit.iris.core.pregenerator;

import com.google.gson.Gson;
import com.volmit.iris.Iris;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.mantle.MantleFlag;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.math.RollingSequence;
import com.volmit.iris.util.math.Spiraler;
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
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class DeepSearchPregenerator extends Thread implements Listener {
    @Getter
    private static DeepSearchPregenerator instance;
    private final DeepSearchJob job;
    private final File destination;
    private final int maxPosition;
    private World world;
    private final ChronoLatch latch;
    private static AtomicInteger foundChunks;
    private final AtomicInteger foundLast;
    private final AtomicInteger foundTotalChunks;
    private final AtomicLong startTime;
    private final RollingSequence chunksPerSecond;
    private final RollingSequence chunksPerMinute;
    private final AtomicInteger chunkCachePos;
    private final AtomicInteger chunkCacheSize;
    private final AtomicInteger foundCacheLast;
    private final AtomicInteger foundCache;
    private LinkedHashMap<Integer, Position2> chunkCache;
    private final ReentrantLock cacheLock = new ReentrantLock();

    private static final Map<String, DeepSearchJob> jobs = new HashMap<>();

    public DeepSearchPregenerator(DeepSearchJob job, File destination) {
        this.job = job;
        this.chunkCacheSize = new AtomicInteger(); // todo
        this.chunkCachePos = new AtomicInteger(1000);
        this.foundCacheLast = new AtomicInteger();
        this.foundCache = new AtomicInteger();
        this.destination = destination;
        this.chunkCache = new LinkedHashMap();
        this.maxPosition = new Spiraler(job.getRadiusBlocks() * 2, job.getRadiusBlocks() * 2, (x, z) -> {
        }).count();
        this.world = Bukkit.getWorld(job.getWorld());
        this.latch = new ChronoLatch(3000);
        this.startTime = new AtomicLong(M.ms());
        this.chunksPerSecond = new RollingSequence(10);
        this.chunksPerMinute = new RollingSequence(10);
        foundChunks = new AtomicInteger(0);
        this.foundLast = new AtomicInteger(0);
        this.foundTotalChunks = new AtomicInteger((int) Math.ceil(Math.pow((2.0 * job.getRadiusBlocks()) / 16, 2)));
        jobs.put(job.getWorld(), job);
        DeepSearchPregenerator.instance = this;
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
        DeepSearchJob job = jobs.get(world.getName());
       // chunkCache(); //todo finish this
        if (latch.flip() && !job.paused) {
            if (cacheLock.isLocked()) {

                Iris.info("DeepFinder: Caching: " + chunkCachePos.get() + " Of " + chunkCacheSize.get());
            }
            long eta = computeETA();
            save();
            int secondGenerated = foundChunks.get() - foundLast.get();
            foundLast.set(foundChunks.get());
            secondGenerated = secondGenerated / 3;
            chunksPerSecond.put(secondGenerated);
            chunksPerMinute.put(secondGenerated * 60);
            Iris.info("deepFinder: " + C.IRIS + world.getName() + C.RESET + " RTT: " + Form.f(foundChunks.get()) + " of " + Form.f(foundTotalChunks.get()) + " " + Form.f((int) chunksPerSecond.getAverage()) + "/s ETA: " + Form.duration((double) eta, 2));

        }

        if (foundChunks.get() >= foundTotalChunks.get()) {
            Iris.info("Completed DeepSearch!");
            interrupt();
        } else {
            int pos = job.getPosition() + 1;
            job.setPosition(pos);
            if (!job.paused) {
                tickSearch(getChunk(pos));
            }
        }
    }

    private long computeETA() {
        return (long) ((foundTotalChunks.get() - foundChunks.get()) / chunksPerSecond.getAverage()) * 1000;
        // todo broken
    }

    private void chunkCache() {
        if (chunkCache.isEmpty()) {
            cacheLock.lock();
            PrecisionStopwatch p = PrecisionStopwatch.start();
            executorService.submit(() -> {
                for (; chunkCacheSize.get() > chunkCachePos.get(); chunkCacheSize.getAndAdd(-1)) {
                    chunkCache.put(chunkCachePos.get(), getChunk(chunkCachePos.get()));
                    chunkCachePos.getAndAdd(1);
                }
                Iris.info("Total Time: " + p.getMinutes());
            });
        }
        if (cacheLock.isLocked()) {
            cacheLock.unlock();
        }
    }

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private void tickSearch(Position2 chunk) {
        executorService.submit(() -> {
            CountDownLatch latch = new CountDownLatch(1);
            try {
                findInChunk(world, chunk.getX(), chunk.getZ());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            Iris.verbose("Generated Async " + chunk);
            latch.countDown();

            try {
                latch.await();
            } catch (InterruptedException ignored) {}
            foundChunks.addAndGet(1);
        });
    }

    private void findInChunk(World world, int x, int z) throws IOException {
        int xx = x * 16;
        int zz = z * 16;
        Engine engine = IrisToolbelt.access(world).getEngine();
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                int height = engine.getHeight(xx + i, zz + j);
                if (height > 300) {
                    File found = new File("plugins" + "iris" + "found.txt");
                    FileWriter writer = new FileWriter(found);
                    if (!found.exists()) {
                        found.createNewFile();
                    }
                   Iris.info("Found at! " + x + ", " + z);
                    writer.write("Found at: X: " + xx + " Z: " + zz + ", ");
               }
            }
        }
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

    public static void setPausedDeep(World world) {
        DeepSearchJob job = jobs.get(world.getName());
        if (isPausedDeep(world)){
            job.paused = false;
        } else {
            job.paused = true;
        }

        if ( job.paused) {
            Iris.info(C.BLUE + "DeepSearch: " + C.IRIS + world.getName() + C.BLUE + " Paused");
        } else {
            Iris.info(C.BLUE + "DeepSearch: " + C.IRIS + world.getName() + C.BLUE + " Resumed");
        }
    }

    public static boolean isPausedDeep(World world) {
        DeepSearchJob job = jobs.get(world.getName());
        return job != null && job.isPaused();
    }

    public void shutdownInstance(World world) throws IOException {
        Iris.info("DeepSearch: " + C.IRIS + world.getName() + C.BLUE + " Shutting down..");
        DeepSearchJob job = jobs.get(world.getName());
        File worldDirectory = new File(Bukkit.getWorldContainer(), world.getName());
        File deepFile = new File(worldDirectory, "DeepSearch.json");

        if (job == null) {
            Iris.error("No DeepSearch job found for world: " + world.getName());
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
                    while (deepFile.exists()){
                        deepFile.delete();
                        J.sleep(1000);
                    }
                    Iris.info("DeepSearch: " + C.IRIS + world.getName() + C.BLUE + " File deleted and instance closed.");
                }
            }.runTaskLater(Iris.instance, 20L);
        } catch (Exception e) {
            Iris.error("Failed to shutdown DeepSearch for " + world.getName());
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
    public static class DeepSearchJob {
        private String world;
        @Builder.Default
        private int radiusBlocks = 5000;
        @Builder.Default
        private int position = 0;
        @Builder.Default
        boolean paused = false;
    }
}

