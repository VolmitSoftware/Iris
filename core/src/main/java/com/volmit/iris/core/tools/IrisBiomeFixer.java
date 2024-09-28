package com.volmit.iris.core.tools;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisBiomeCustom;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.math.RollingSequence;
import com.volmit.iris.util.misc.E;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.J;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.Chunk;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
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

    // File to store unregistered biome IDs
    private File unregisteredBiomesFile;

    // Reference to your plugin instance (Assuming you have one)
    private Plugin plugin;

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
        this.plugin = Iris.instance;

        // Initialize the file for storing unregistered biome IDs
        this.unregisteredBiomesFile = new File(world.getWorldFolder(), "unregistered_biomes.txt");
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
                                Biome bukkitBiome;
                                IrisBiome irisBiome = engine.getBiome(x, y, z);
                                IrisBiomeCustom custom;
                                try {
                                    custom = irisBiome.getCustomBiome(rng, x, y, z);
                                } catch (Exception e) {
                                    custom = null;
                                }

                                if (custom != null) {
                                    // Attempt to get the Biome enum constant
                                    try {
                                        bukkitBiome = Biome.valueOf(custom.getId().toUpperCase());
                                        world.setBiome(block.getX(), block.getY(), block.getZ(), bukkitBiome);
                                    } catch (IllegalArgumentException ex) {
                                        // Custom biome not found in Biome enum
                                        // Attempt to set custom biome via NMS
                                        try {
                                            setCustomBiome(block, custom.getId());
                                        } catch (Exception e) {
                                            // Log unregistered or failed to set custom biome
                                            logUnregisteredBiome(custom.getId());
                                            // Fallback to derivative biome
                                            bukkitBiome = irisBiome.getDerivative();
                                            world.setBiome(block.getX(), block.getY(), block.getZ(), bukkitBiome);
                                        }
                                    }
                                } else {
                                    // Use derivative biome if custom biome is null
                                    bukkitBiome = irisBiome.getDerivative();
                                    world.setBiome(block.getX(), block.getY(), block.getZ(), bukkitBiome);
                                }
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

    /**
     * Sets a custom biome using NMS (Minecraft's internal classes).
     *
     * @param block      The block whose biome is to be set.
     * @param biomeId    The NamespacedKey of the custom biome (e.g., "custom:my_biome").
     * @throws Exception If reflection or NMS interaction fails.
     */
    private void setCustomBiome(Block block, String biomeId) throws Exception {
        // Parse the NamespacedKey
        NamespacedKey key = NamespacedKey.fromString(biomeId);
        if (key == null) {
            throw new IllegalArgumentException("Invalid biome ID: " + biomeId);
        }

        // Access NMS classes using reflection
        // Adjust the version string as needed (e.g., "v1_20_R1")
        String nmsVersion = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
        Class<?> worldClass = Class.forName("org.bukkit.craftbukkit." + nmsVersion + ".CraftWorld");
        Object nmsWorld = worldClass.cast(world).getClass().getMethod("getHandle").invoke(world);

        Class<?> chunkClass = Class.forName("net.minecraft.world.level.chunk.Chunk");
        Object nmsChunk = chunkClass.cast(nmsWorld.getClass().getMethod("getChunk", int.class, int.class, boolean.class)
                .invoke(nmsWorld, block.getChunk().getX(), block.getChunk().getZ(), false));

        // Get the biome registry
        Class<?> registryKeyClass = Class.forName("net.minecraft.resources.ResourceKey");
        Class<?> biomeClass = Class.forName("net.minecraft.world.level.biome.Biome");
        Class<?> registryClass = Class.forName("net.minecraft.core.Registry");
        Method biomeRegistryMethod = registryClass.getMethod("a", Class.class, Object.class);
        Object biomeRegistry = biomeRegistryMethod.invoke(null, biomeClass, null); // Replace null with actual registry if needed

        // Get the biome by key
        Method getBiomeMethod = biomeClass.getMethod("a", registryKeyClass);
        Object customBiome = getBiomeMethod.invoke(null, key.getNamespace() + ":" + key.getKey());

        if (customBiome == null) {
            throw new IllegalArgumentException("Custom biome not found: " + biomeId);
        }

        // Set the biome in the chunk
        Method setBiomeMethod = chunkClass.getMethod("setBiome", int.class, int.class, biomeClass);
        setBiomeMethod.invoke(nmsChunk, block.getX() & 15, block.getZ() & 15, customBiome);
    }

    /**
     * Logs unregistered or failed to set custom biomes to a file.
     *
     * @param biomeId The ID of the biome that failed to register.
     */
    private void logUnregisteredBiome(String biomeId) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(unregisteredBiomesFile, true))) {
            writer.write(biomeId);
            writer.newLine();
        } catch (IOException e) {
            Iris.error("Failed to log unregistered biome: " + biomeId, e);
        }
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
