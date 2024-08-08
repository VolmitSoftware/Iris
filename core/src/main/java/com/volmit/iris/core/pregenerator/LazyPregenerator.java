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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class LazyPregenerator extends Thread implements Listener {
    private static final Map<String, LazyPregenJob> jobs = new HashMap<>();
    @Getter
    private static LazyPregenerator instance;
    private static AtomicInteger lazyGeneratedChunks;
    private final LazyPregenJob job;
    private final File destination;
    private final int maxPosition;
    private final long rate;
    private final ChronoLatch latch;
    private final AtomicInteger generatedLast;
    private final AtomicInteger lazyTotalChunks;
    private final AtomicLong startTime;
    private final RollingSequence chunksPerSecond;
    private final RollingSequence chunksPerMinute;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private World world;

    public LazyPregenerator(LazyPregenJob job, File destination) {
        this.job = job;
        this.destination = destination;
        this.maxPosition = new Spiraler(job.getRadiusBlocks() * 2, job.getRadiusBlocks() * 2, (x, z) -> {
        }).count();
        this.world = Bukkit.getWorld(job.getWorld());
        this.rate = Math.round((1D / (job.getChunksPerMinute() / 60D)) * 1000D);
        this.latch = new ChronoLatch(15000);
        this.startTime = new AtomicLong(M.ms());
        this.chunksPerSecond = new RollingSequence(10);
        this.chunksPerMinute = new RollingSequence(10);
        lazyGeneratedChunks = new AtomicInteger(0);
        this.generatedLast = new AtomicInteger(0);
        this.lazyTotalChunks = new AtomicInteger((int) Math.ceil(Math.pow((2.0 * job.getRadiusBlocks()) / 16, 2)));
        jobs.put(job.getWorld(), job);
        LazyPregenerator.instance = this;
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

    public static void setPausedLazy(World world) {
        LazyPregenJob job = jobs.get(world.getName());
        if (isPausedLazy(world)) {
            job.paused = false;
        } else {
            job.paused = true;
        }

        if (job.paused) {
            Iris.info(C.BLUE + "LazyGen: " + C.IRIS + world.getName() + C.BLUE + " Paused");
        } else {
            Iris.info(C.BLUE + "LazyGen: " + C.IRIS + world.getName() + C.BLUE + " Resumed");
        }
    }

    public static boolean isPausedLazy(World world) {
        LazyPregenJob job = jobs.get(world.getName());
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
        LazyPregenJob job = jobs.get(world.getName());
        if (latch.flip() && !job.paused) {
            long eta = computeETA();
            save();
            int secondGenerated = lazyGeneratedChunks.get() - generatedLast.get();
            generatedLast.set(lazyGeneratedChunks.get());
            secondGenerated = secondGenerated / 15;
            chunksPerSecond.put(secondGenerated);
            chunksPerMinute.put(secondGenerated * 60);
            if (!job.isSilent()) {
                Iris.info("LazyGen: " + C.IRIS + world.getName() + C.RESET + " RTT: " + Form.f(lazyGeneratedChunks.get()) + " of " + Form.f(lazyTotalChunks.get()) + " " + Form.f((int) chunksPerMinute.getAverage()) + "/m ETA: " + Form.duration((double) eta, 2));
            }
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
            if (!job.paused) {
                tickGenerate(getChunk(pos));
            }
        }
    }

    private long computeETA() {
        return (long) ((lazyTotalChunks.get() - lazyGeneratedChunks.get()) / chunksPerMinute.getAverage()) * 1000;
        // todo broken
    }

    private void tickGenerate(Position2 chunk) {
        executorService.submit(() -> {
            CountDownLatch latch = new CountDownLatch(1);
            if (PaperLib.isPaper()) {
                PaperLib.getChunkAtAsync(world, chunk.getX(), chunk.getZ(), true)
                        .thenAccept((i) -> {
                            Iris.verbose("Generated Async " + chunk);
                            latch.countDown();
                        });
            } else {
                J.s(() -> {
                    world.getChunkAt(chunk.getX(), chunk.getZ());
                    Iris.verbose("Generated " + chunk);
                    latch.countDown();
                });
            }
            try {
                latch.await();
            } catch (InterruptedException ignored) {
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

    public void shutdownInstance(World world) throws IOException {
        Iris.info("LazyGen: " + C.IRIS + world.getName() + C.BLUE + " Shutting down..");
        LazyPregenJob job = jobs.get(world.getName());
        File worldDirectory = new File(Bukkit.getWorldContainer(), world.getName());
        File lazyFile = new File(worldDirectory, "lazygen.json");

        if (job == null) {
            Iris.error("No Lazygen job found for world: " + world.getName());
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
                    while (lazyFile.exists()) {
                        lazyFile.delete();
                        J.sleep(1000);
                    }
                    Iris.info("LazyGen: " + C.IRIS + world.getName() + C.BLUE + " File deleted and instance closed.");
                }
            }.runTaskLater(Iris.instance, 20L);
        } catch (Exception e) {
            Iris.error("Failed to shutdown Lazygen for " + world.getName());
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
    public static class LazyPregenJob {
        @Builder.Default
        boolean silent = false;
        @Builder.Default
        boolean paused = false;
        private String world;
        @Builder.Default
        private int healingPosition = 0;
        @Builder.Default
        private boolean healing = false;
        @Builder.Default
        private int chunksPerMinute = 32;
        @Builder.Default
        private int radiusBlocks = 5000;
        @Builder.Default
        private int position = 0;
    }
}

