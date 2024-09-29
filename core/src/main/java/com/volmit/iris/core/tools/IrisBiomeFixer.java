package com.volmit.iris.core.tools;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.math.RollingSequence;
import com.volmit.iris.util.scheduling.ChronoLatch;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class IrisBiomeFixer {
    private World world;
    private Engine engine;
    private ChronoLatch latch;

    private RollingSequence chunksPerSecond;
    private RollingSequence chunksPerMinute;
    private AtomicLong startTime;
    private AtomicLong lastLogTime;

    private AtomicInteger generated = new AtomicInteger(0);
    private AtomicInteger generatedLast = new AtomicInteger(0);
    private AtomicInteger generatedLastMinute = new AtomicInteger(0);
    private AtomicInteger totalChunks = new AtomicInteger(0);
    private ChronoLatch progressLatch = new ChronoLatch(5000); // Update every 5 seconds
    private ChronoLatch minuteLatch = new ChronoLatch(60000, false);

    private File unregisteredBiomesFile;

    private Plugin plugin;

    private ScheduledExecutorService progressUpdater;

    public IrisBiomeFixer(World world) {
        if (!IrisToolbelt.isIrisWorld(world)) {
            Iris.info("This is not an Iris world!");
            return;
        }

        this.chunksPerSecond = new RollingSequence(10);
        this.chunksPerMinute = new RollingSequence(10);
        this.startTime = new AtomicLong(M.ms());
        this.lastLogTime = new AtomicLong(M.ms());
        this.world = world;
        this.latch = new ChronoLatch(3000);
        this.engine = IrisToolbelt.access(world).getEngine();
        this.plugin = Iris.instance;

        // Initialize the file for storing unregistered biome IDs
        this.unregisteredBiomesFile = new File(world.getWorldFolder(), "unregistered_biomes.txt");

        // Initialize the progress updater executor
        this.progressUpdater = Executors.newSingleThreadScheduledExecutor();
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

            totalChunks.addAndGet(1024);
        }

        // Start the progress updater
        progressUpdater.scheduleAtFixedRate(this::updateProgress, 1, 1, TimeUnit.SECONDS);

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

                    int minY = world.getMinHeight();
                    int maxY = world.getMaxHeight();
                    int height = maxY - minY; // Correct height calculation

                    Hunk<Object> biomes = Hunk.newHunk(16, height, 16);

                    for (int x = 0; x < 16; x += 4) {
                        for (int z = 0; z < 16; z += 4) {
                            for (int y = minY; y < maxY; y += 4) {
                                // Calculate the biome once per 4x4x4 block
                                int realX = chunkX * 16 + x;
                                int realZ = chunkZ * 16 + z;
                                int realY = y;

                                IrisBiome biome = engine.getBiome(realX, realY, realZ);
                                Object biomeHolder = null;

                                if (biome.isCustom()) {
                                    biomeHolder = INMS.get().getCustomBiomeBaseHolderFor(
                                            engine.getDimension().getLoadKey() + ":" + biome.getCustomBiome(rng, realX, realY, realZ).getId());
                                } else {
                                    // Handle non-custom biome if necessary
                                    // biomeHolder = INMS.get().getCustomBiomeBaseHolderFor(biome.getDerivative().getKey().getKey());
                                }

                                // Now fill the 4x4x4 block in the hunk
                                for (int subX = x; subX < x + 4 && subX < 16; subX++) {
                                    for (int subZ = z; subZ < z + 4 && subZ < 16; subZ++) {
                                        for (int subY = y; subY < y + 4 && subY < maxY; subY++) {
                                            int relativeY = subY - minY; // Offset Y-coordinate

                                            biomes.set(subX, relativeY, subZ, biomeHolder);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    INMS.get().setBiomes(cx, cz, engine.getWorld().realWorld(), biomes);

                    generated.incrementAndGet();
                }
            }
        }

        // Shut down the progress updater
        progressUpdater.shutdown();

        try {
            // Wait for the progress updater to finish
            if (!progressUpdater.awaitTermination(1, TimeUnit.MINUTES)) {
                Iris.warn("Progress updater did not terminate in time.");
                progressUpdater.shutdownNow();
            }
        } catch (InterruptedException e) {
            Iris.warn("Progress updater interrupted during shutdown.");
            progressUpdater.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Final Progress Update
        Iris.info("Biome Fixing Completed: " + generated.get() + "/" + totalChunks.get() + " chunks processed.");
    }

    private void updateProgress() {
        long currentTime = M.ms();
        int currentGenerated = generated.get();
        int last = generatedLast.getAndSet(currentGenerated);
        int chunksProcessed = currentGenerated - last;

        chunksPerSecond.put(chunksProcessed);

        // Update chunks per minute
        if (minuteLatch.flip()) {
            int lastMinuteGenerated = generatedLastMinute.getAndSet(currentGenerated);
            int minuteProcessed = currentGenerated - lastMinuteGenerated;
            chunksPerMinute.put(minuteProcessed);
        }

        long eta = computeETA();
        double percentage = ((double) currentGenerated / totalChunks.get()) * 100;

        if (progressLatch.flip()) {
            Iris.info("Biome Fixer Progress: " + currentGenerated + "/" + totalChunks.get() +
                    " chunks (" + percentage + "%%) - " +
                    chunksPerSecond.getAverage() + " chunks/s ETA: " + formatETA(eta));
        }
    }

    private long computeETA() {
        if (generated.get() > totalChunks.get() / 8) {
            // Use smooth function
            double elapsedTime = (double) (M.ms() - startTime.get());
            double rate = generated.get() / elapsedTime; // chunks per millisecond
            int remaining = totalChunks.get() - generated.get();
            return (long) (remaining / rate);
        } else {
            // Use quick function
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
        return hours + "h:" + minutes + "m:" + seconds + "s";
    }
}