package com.volmit.iris.core.tools;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisBiomeCustom;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.math.RollingSequence;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.J;
import org.bukkit.World;

import java.io.File;

import org.bukkit.Chunk;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class IrisBiomeFixer {
    /**
     * Do a pregen style approach iterate across a certain region and set everything to the correct biome again.
     * Have 2 modes ( all, surface-only ) surface-only gets the underground caves from a different world
     */

    private World world;
    private Engine engine;
    private int radius;
    private ChronoLatch latch;

    private RollingSequence chunksPerSecond;
    private AtomicLong startTime;
    private AtomicLong lastLogTime;

    private AtomicInteger generated = new AtomicInteger(0);
    private AtomicInteger lastGenerated = new AtomicInteger(0);
    private AtomicInteger totalChunks = new AtomicInteger(0);
    private ChronoLatch progressLatch = new ChronoLatch(5000); // Update every 5 seconds

    public IrisBiomeFixer(World world) {
        if (!IrisToolbelt.isIrisWorld(world)) {
            Iris.info("This is not an Iris world!");
            return;
        }

        this.chunksPerSecond = new RollingSequence(32);
        this.startTime = new AtomicLong(M.ms());
        this.lastLogTime = new AtomicLong(M.ms());
        this.world = world;
        this.latch = new ChronoLatch(3000);
        this.engine = IrisToolbelt.access(world).getEngine();
    }

    public void fixBiomes() {
        File regionFolder = new File(world.getWorldFolder(), "region");
        File[] regionFiles = regionFolder.listFiles((dir, name) -> name.endsWith(".mca"));

        if (regionFiles == null || regionFiles.length == 0) {
            Iris.info("No region files found in " + regionFolder.getAbsolutePath());
            return;
        }

        RNG rng = new RNG(engine.getSeedManager().getBiome());

        // Calculate total chunks
        for (File regionFile : regionFiles) {
            String filename = regionFile.getName(); // e.g., "r.0.0.mca"
            String[] parts = filename.split("\\.");

            if (parts.length != 4) {
                continue;
            }

            totalChunks.addAndGet(1024); // Each region has 32x32 chunks = 1024
        }

        for (File regionFile : regionFiles) {
            String filename = regionFile.getName(); // e.g., "r.0.0.mca"
            String[] parts = filename.split("\\.");

            if (parts.length != 4) {
                continue;
            }

            int regionX = Integer.parseInt(parts[1]);
            int regionZ = Integer.parseInt(parts[2]);

            for (int cx = 0; cx < 32; cx++) {
                for (int cz = 0; cz < 32; cz++) {
                    int chunkX = regionX * 32 + cx;
                    int chunkZ = regionZ * 32 + cz;

                    if (!world.isChunkGenerated(chunkX, chunkZ)) {
                        continue;
                    }

                    J.s(() -> {
                        world.loadChunk(chunkX, chunkZ);
                    });
                    Chunk chunk = world.getChunkAt(chunkX, chunkZ);

                    int minY = world.getMinHeight();
                    int maxY = world.getMaxHeight();

                    for (int x = 0; x < 16; x++) {
                        for (int y = minY; y < maxY; y++) {
                            for (int z = 0; z < 16; z++) {
                                Block block = chunk.getBlock(x, y, z);
                                Biome bukkitBiome = null;
                                IrisBiome irisBiome = engine.getBiome(x, y, z);
                                IrisBiomeCustom custom = irisBiome.getCustomBiome(rng, x, y, z);
                                if (custom != null) {
                                    bukkitBiome = Biome.valueOf(custom.getId().toUpperCase());
                                } else {


                                }

                                world.setBiome(block.getX(), block.getY(), block.getZ(), bukkitBiome);
                            }
                        }
                    }

                    generated.incrementAndGet();

                    // Progress Logging
                    if (progressLatch.flip()) {
                        long currentTime = M.ms();
                        long elapsedTime = currentTime - lastLogTime.get();
                        int currentGenerated = generated.get();
                        int last = lastGenerated.getAndSet(currentGenerated);
                        int chunksProcessed = currentGenerated - last;

                        double seconds = elapsedTime / 1000.0;
                        int cps = seconds > 0 ? (int) (chunksProcessed / seconds) : 0;
                        chunksPerSecond.put(cps);

                        long eta = computeETA(cps);
                        double percentage = ((double) currentGenerated / totalChunks.get()) * 100;

                        Iris.info(String.format("Biome Fixer Progress: %d/%d chunks (%.2f%%) - %d chunks/s ETA: %s",
                                currentGenerated, totalChunks.get(), percentage, cps, formatETA(eta)));

                        lastLogTime.set(currentTime);
                    }

                    world.unloadChunk(chunkX, chunkZ);
                }
            }
        }

        // Final Progress Update
        Iris.info(String.format("Biome Fixing Completed: %d/%d chunks processed.", generated.get(), totalChunks.get()));
    }

    private long computeETA(int cps) {
        if (chunksPerSecond.size() < chunksPerSecond.getMax()) {
            if (cps == 0) return Long.MAX_VALUE;
            int remaining = totalChunks.get() - generated.get();
            return (long) ((double) remaining / cps) * 1000;
        } else {
            double averageCps = chunksPerSecond.getAverage();
            if (averageCps == 0) return Long.MAX_VALUE;
            int remaining = totalChunks.get() - generated.get();
            return (long) ((double) remaining / averageCps) * 1000;
        }
    }

    private String formatETA(long millis) {
        if (millis == Long.MAX_VALUE) {
            return "Unknown";
        }
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds %= 60;
        long hours = minutes / 60;
        minutes %= 60;
        return String.format("%02dh:%02dm:%02ds", hours, minutes, seconds);
    }
}

