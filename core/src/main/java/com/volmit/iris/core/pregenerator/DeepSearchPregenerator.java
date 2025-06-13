package com.volmit.iris.core.pregenerator;

import com.google.gson.Gson;
import com.volmit.iris.Iris;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.math.RollingSequence;
import com.volmit.iris.util.math.Spiraler;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.J;
import lombok.Data;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private int pos;
    private final AtomicInteger foundCacheLast;
    private final AtomicInteger foundCache;
    private LinkedHashMap<Integer, Position2> chunkCache;
    private KList<Position2> chunkQueue;
    private final ReentrantLock cacheLock;

    private static final Map<String, DeepSearchJob> jobs = new HashMap<>();

    public DeepSearchPregenerator(DeepSearchJob job, File destination) {
        this.job = job;
        this.chunkCacheSize = new AtomicInteger(); // todo
        this.chunkCachePos = new AtomicInteger(1000);
        this.foundCacheLast = new AtomicInteger();
        this.foundCache = new AtomicInteger();
        this.cacheLock = new ReentrantLock();
        this.destination = destination;
        this.chunkCache = new LinkedHashMap<>();
        this.maxPosition = new Spiraler(job.getRadiusBlocks() * 2, job.getRadiusBlocks() * 2, (x, z) -> {
        }).count();
        this.world = Bukkit.getWorld(job.getWorld().getUID());
        this.chunkQueue = new KList<>();
        this.latch = new ChronoLatch(3000);
        this.startTime = new AtomicLong(M.ms());
        this.chunksPerSecond = new RollingSequence(10);
        this.chunksPerMinute = new RollingSequence(10);
        foundChunks = new AtomicInteger(0);
        this.foundLast = new AtomicInteger(0);
        this.foundTotalChunks = new AtomicInteger((int) Math.ceil(Math.pow((2.0 * job.getRadiusBlocks()) / 16, 2)));

        this.pos = 0;
        jobs.put(job.getWorld().getName(), job);
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
            } else {
                long eta = computeETA();
                save();
                int secondGenerated = foundChunks.get() - foundLast.get();
                foundLast.set(foundChunks.get());
                secondGenerated = secondGenerated / 3;
                chunksPerSecond.put(secondGenerated);
                chunksPerMinute.put(secondGenerated * 60);
                Iris.info("DeepFinder: " + C.IRIS + world.getName() + C.RESET + " Searching: " + Form.f(foundChunks.get()) + " of " + Form.f(foundTotalChunks.get()) + " " + Form.f((int) chunksPerSecond.getAverage()) + "/s ETA: " + Form.duration((double) eta, 2));
            }

        }
        if (foundChunks.get() >= foundTotalChunks.get()) {
            Iris.info("Completed DeepSearch!");
            interrupt();
        }
    }

    private long computeETA() {
        return (long) ((foundTotalChunks.get() - foundChunks.get()) / chunksPerSecond.getAverage()) * 1000;
        // todo broken
    }

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private void queueSystem(Position2 chunk) {
        if (chunkQueue.isEmpty()) {
            for (int limit = 512; limit != 0; limit--) {
                pos = job.getPosition() + 1;
                chunkQueue.add(getChunk(pos));
            }
        } else {
            //MCAUtil.read();

        }


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
                    IrisBiome biome = engine.getBiome(xx, engine.getHeight(), zz);
                    Iris.info("Found at! " + xx + ", " + zz + "Biome ID: " + biome.getName() + ", ");
                    writer.write("Biome at: X: " + xx + " Z: " + zz + "Biome ID: " + biome.getName() +  ", ");
                    return;
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
            J.a(() -> {
                while (deepFile.exists()) {
                    deepFile.delete();
                    J.sleep(1000);
                }
                Iris.info("DeepSearch: " + C.IRIS + world.getName() + C.BLUE + " File deleted and instance closed.");
            }, 10);
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
    @lombok.Builder
    public static class DeepSearchJob {
        private World world;
        @lombok.Builder.Default
        private int radiusBlocks = 5000;
        @lombok.Builder.Default
        private int position = 0;
        @lombok.Builder.Default
        boolean paused = false;
    }
}

