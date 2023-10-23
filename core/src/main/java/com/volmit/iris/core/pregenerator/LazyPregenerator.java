package com.volmit.iris.core.pregenerator;

import com.google.gson.Gson;
import com.volmit.iris.Iris;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.math.Spiraler;
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
import java.util.concurrent.atomic.AtomicInteger;

public class LazyPregenerator extends Thread implements Listener {
    private final LazyPregenJob job;
    private final File destination;
    private final int maxPosition;
    private final World world;
    private final long rate;
    private final ChronoLatch latch;

    public LazyPregenerator(LazyPregenJob job, File destination) {
        this.job = job;
        this.destination = destination;
        this.maxPosition = new Spiraler(job.getRadiusBlocks() * 2, job.getRadiusBlocks() * 2, (x, z) -> {
        }).count();
        this.world = Bukkit.getWorld(job.getWorld());
        this.rate = Math.round((1D / (job.chunksPerMinute / 60D)) * 1000D);
        this.latch = new ChronoLatch(60000);
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
            save();
            Iris.info("LazyGen: " + world.getName() + " RTT: " + Form.duration((Math.pow((job.radiusBlocks / 16D), 2) / job.chunksPerMinute) * 60 * 1000, 2));
        }

        if (job.getPosition() >= maxPosition) {
            if (job.isHealing()) {
                int pos = (job.getHealingPosition() + 1) % maxPosition;
                job.setHealingPosition(pos);
                tickRegenerate(getChunk(pos));
            } else {
                Iris.verbose("Completed Lazy Gen!");
                interrupt();
            }
        } else {
            int pos = job.getPosition() + 1;
            job.setPosition(pos);
            tickGenerate(getChunk(pos));
        }
    }

    private void tickGenerate(Position2 chunk) {
        if (PaperLib.isPaper()) {
            PaperLib.getChunkAtAsync(world, chunk.getX(), chunk.getZ(), true).thenAccept((i) -> Iris.verbose("Generated Async " + chunk));
        } else {
            J.s(() -> world.getChunkAt(chunk.getX(), chunk.getZ()));
            Iris.verbose("Generated " + chunk);
        }
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
