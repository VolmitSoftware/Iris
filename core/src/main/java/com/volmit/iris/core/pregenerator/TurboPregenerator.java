package com.volmit.iris.core.pregenerator;

import com.google.gson.Gson;
import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TurboPregenerator extends Thread implements Listener {
    @Getter
    private static TurboPregenerator instance;
    private final TurboPregenJob job;
    private final File destination;
    private final int maxPosition;
    private World world;
    private final ChronoLatch latch;
    private static AtomicInteger turboGeneratedChunks;
    private final AtomicInteger generatedLast;
    private final AtomicInteger turboTotalChunks;
    private final AtomicLong startTime;
    private final RollingSequence chunksPerSecond;
    private final RollingSequence chunksPerMinute;
    private KList<Position2> queue = new KList<>();
    private AtomicInteger maxWaiting;
    private static final Map<String, TurboPregenJob> jobs = new HashMap<>();

    public TurboPregenerator(TurboPregenJob job, File destination) {
        this.job = job;
        queue = new KList<>(512);
        this.maxWaiting = new AtomicInteger(128);
        this.destination = destination;
        this.maxPosition = new Spiraler(job.getRadiusBlocks() * 2, job.getRadiusBlocks() * 2, (x, z) -> {
        }).count();
        this.world = Bukkit.getWorld(job.getWorld());
        this.latch = new ChronoLatch(3000);
        this.startTime = new AtomicLong(M.ms());
        this.chunksPerSecond = new RollingSequence(10);
        this.chunksPerMinute = new RollingSequence(10);
        turboGeneratedChunks = new AtomicInteger(0);
        this.generatedLast = new AtomicInteger(0);
        this.turboTotalChunks = new AtomicInteger((int) Math.ceil(Math.pow((2.0 * job.getRadiusBlocks()) / 16, 2)));
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
        if (latch.flip() && !job.paused) {
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
            int pos = job.getPosition() + 1;
            job.setPosition(pos);
            if (!job.paused) {
                if (queue.size() < maxWaiting.get()) {
                    Position2 chunk = getChunk(pos);
                    queue.add(chunk);
                }
                waitForChunksPartial();
            }
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

    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    private void tickGenerate(Position2 chunk) {
        executorService.submit(() -> {
            CountDownLatch latch = new CountDownLatch(1);
            PaperLib.getChunkAtAsync(world, chunk.getX(), chunk.getZ(), true)
                    .thenAccept((i) -> {
                        Iris.verbose("Generated Async " + chunk);
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
        private String world;
        @Builder.Default
        private int radiusBlocks = 5000;
        @Builder.Default
        private int position = 0;
        @Builder.Default
        boolean paused = false;
    }
}

