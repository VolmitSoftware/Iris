package com.volmit.iris.core.pregenerator;

import com.google.gson.Gson;
import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.math.RollingSequence;
import com.volmit.iris.util.math.Spiraler;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.J;
import io.papermc.lib.PaperLib;
import lombok.Builder;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class LazyPregenerator extends Thread implements Listener {
    private final LazyPregenJob job;
    private final File destination;
    private final int maxPosition;
    private final World world;
    private final long rate;
    private final ChronoLatch latch;
    private static AtomicInteger lazyGeneratedChunks;
    private final AtomicInteger generatedLast;
    private final AtomicInteger lazyTotalChunks;
    private final AtomicLong startTime;
    private final RollingSequence chunksPerSecond;

    public LazyPregenerator(LazyPregenJob job, File destination) {
        this.job = job;
        this.destination = destination;
        this.maxPosition = new Spiraler(job.getRadiusBlocks() * 2, job.getRadiusBlocks() * 2, (x, z) -> {
        }).count();
        this.world = Bukkit.getWorld(job.getWorld());
        this.rate = Math.round((1D / (job.chunksPerMinute / 60D)) * 1000D);
        this.latch = new ChronoLatch(6000);
        startTime = new AtomicLong(M.ms());
        chunksPerSecond = new RollingSequence(10);
        lazyGeneratedChunks = new AtomicInteger(0);
        generatedLast = new AtomicInteger(0);
        lazyTotalChunks = new AtomicInteger();

        int radius = job.getRadiusBlocks();
        lazyTotalChunks.set((int) Math.ceil(Math.pow((2.0 * radius) / 16, 2)));
    }

    public LazyPregenerator(File file) throws IOException {
        this(new Gson().fromJson(IO.readAll(file), LazyPregenJob.class), file);
    }

    public static void loadLazyGenerators() {
        for (World i : Bukkit.getWorlds()) {
            File lazygen = new File(i.getWorldFolder(), "lazygen.json");

            if (lazygen.exists()) {
                try {
                    LazyPregenerator p = new LazyPregenerator(lazygen);
                    p.start();
                    Iris.info("Started Lazy Pregenerator: " + p.job);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
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
            J.sleep(rate);
            tick();
        }

        try {
            saveNow();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void tick() {
        if (latch.flip()) {
            long eta = computeETA();
            save();
            int secondGenerated = lazyGeneratedChunks.get() - generatedLast.get();
            generatedLast.set(lazyGeneratedChunks.get());
            secondGenerated = secondGenerated / 6;
            chunksPerSecond.put(secondGenerated);
            Iris.info("LazyGen: " + C.IRIS + world.getName() + C.RESET + " RTT: " + Form.f(lazyGeneratedChunks.get()) + " of " + Form.f(lazyTotalChunks.get()) + " " + Form.f((int) chunksPerSecond.getAverage()) + "/s ETA: " + Form.duration((double) eta, 2));
            //Iris.info("Debug: " + maxPosition);
            //Iris.info("Debug1: " + job.getPosition());

            // todo: Maxpos borked
        }

        if (lazyGeneratedChunks.get() >= lazyTotalChunks.get()) {
            if (job.isHealing()) {
                int pos = (job.getHealingPosition() + 1) % maxPosition;
                job.setHealingPosition(pos);
                tickRegenerate(getChunk(pos));
            } else {
                Iris.info("Completed Lazy Gen!");
                interrupt();
            }
        } else {
            int pos = job.getPosition() + 1;
            job.setPosition(pos);
            tickGenerate(getChunk(pos));
        }
    }

    private long computeETA() {
        return (long) (lazyTotalChunks.get() > 1024 ? // Generated chunks exceed 1/8th of total?
                // If yes, use smooth function (which gets more accurate over time since its less sensitive to outliers)
                ((lazyTotalChunks.get() - lazyGeneratedChunks.get()) * ((double) (M.ms() - startTime.get()) / (double) lazyGeneratedChunks.get())) :
                // If no, use quick function (which is less accurate over time but responds better to the initial delay)
                ((lazyTotalChunks.get() - lazyGeneratedChunks.get()) / chunksPerSecond.getAverage()) * 1000 //
        );
    }

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private void tickGenerate(Position2 chunk) {
        executorService.submit(() -> {
            if (PaperLib.isPaper()) {
                PaperLib.getChunkAtAsync(world, chunk.getX(), chunk.getZ(), true).thenAccept((i) -> Iris.verbose("Generated Async " + chunk));
            } else {
                J.s(() -> world.getChunkAt(chunk.getX(), chunk.getZ()));
                Iris.verbose("Generated " + chunk);
            }
            lazyGeneratedChunks.addAndGet(1);
        });
    }

    private void tickRegenerate(Position2 chunk) {
        J.s(() -> world.regenerateChunk(chunk.getX(), chunk.getZ()));
        Iris.verbose("Regenerated " + chunk);
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

    public void saveNow() throws IOException {
        IO.writeAll(this.destination, new Gson().toJson(job));
    }

    @Data
    @Builder
    public static class LazyPregenJob {
        private String world;
        @Builder.Default
        private int healingPosition = 0;
        @Builder.Default
        private boolean healing = false;
        @Builder.Default
        private int chunksPerMinute = 32; // 48 hours is roughly 5000 radius
        @Builder.Default
        private int radiusBlocks = 5000;
        @Builder.Default
        private int position = 0;
    }
}
