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

package com.volmit.iris.core.tools;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RollingSequence;
import com.volmit.iris.util.nbt.mca.Chunk;
import com.volmit.iris.util.nbt.mca.MCAFile;
import com.volmit.iris.util.nbt.mca.MCAUtil;
import com.volmit.iris.util.nbt.tag.CompoundTag;
import com.volmit.iris.util.nbt.tag.StringTag;
import com.volmit.iris.util.plugin.VolmitSender;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.World;

import java.io.File;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class IrisWorldDump {
    private KMap<blockData, Long> storage;
    private AtomicLong airStorage;
    private World world;
    private File MCADirectory;
    private AtomicInteger threads;
    private AtomicInteger regionsProcessed;
    private AtomicInteger chunksProcessed;
    private AtomicInteger totalToProcess;
    private AtomicInteger totalMaxChunks;
    private AtomicInteger totalMCAFiles;
    private RollingSequence chunksPerSecond;
    private Engine engine = null;
    private Boolean IrisWorld;
    private VolmitSender sender;
    private ExecutorService executor;
    private ScheduledExecutorService scheduler;
    private AtomicLong startTime;
    private File dumps;
    private File worldDump;
    private int mcaCacheSize;
    private File temp;
    private File blocks;
    private File structures;

    public IrisWorldDump(World world, VolmitSender sender) {
        sender.sendMessage("Initializing IrisWorldDump...");
        this.world = world;
        this.sender = sender;
        this.MCADirectory = new File(world.getWorldFolder(), "region");
        this.dumps = new File("plugins" + File.separator + "iris", "dumps");
        this.worldDump = new File(dumps, world.getName());
        this.mcaCacheSize = IrisSettings.get().getWorldDump().mcaCacheSize;
        this.regionsProcessed = new AtomicInteger(0);
        this.chunksProcessed = new AtomicInteger(0);
        this.totalToProcess = new AtomicInteger(0);
        this.chunksPerSecond = new RollingSequence(10);
        this.temp = new File(worldDump, "temp");
        this.executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() - 1);
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.startTime = new AtomicLong();
        this.storage = new KMap<>();
        this.airStorage = new AtomicLong(0);

        this.blocks = new File(worldDump, "blocks");
        this.structures = new File(worldDump, "structures");

        try {
            this.engine = IrisToolbelt.access(world).getEngine();
            this.IrisWorld = true;
        } catch (Exception e) {
            this.IrisWorld = false;
        }
    }


    public void start() {

        if (!dumps.exists()) {
            if (!dumps.mkdirs()) {
                System.err.println("Failed to create dump directory.");
                return;
            }
        }

        try {
            CompletableFuture<Integer> mcaCount = CompletableFuture.supplyAsync(this::totalMcaFiles);
            CompletableFuture<Integer> chunkCount = CompletableFuture.supplyAsync(this::totalMCAChunks);
            this.totalMCAFiles = new AtomicInteger(mcaCount.get());
            this.totalMaxChunks = new AtomicInteger(chunkCount.get());
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        dump();
        updater();
    }

    private void updater() {
        startTime.set(System.currentTimeMillis());
        scheduler.scheduleAtFixedRate(() -> {
            long eta = computeETA();
            long elapsedSeconds = (System.currentTimeMillis() - startTime.get()) / 3000;
            int processed = chunksProcessed.get();
            double cps = elapsedSeconds > 0 ? processed / (double) elapsedSeconds : 0;
            chunksPerSecond.put(cps);
            double percentage = ((double) chunksProcessed.get() / (double) totalMaxChunks.get()) * 100;
            Iris.info("Processed: " + Form.f(processed) + " of " + Form.f(totalMaxChunks.get()) + " (%.0f%%) " + Form.f(chunksPerSecond.getAverage()) + "/s, ETA: " + Form.duration(eta, 2), percentage);

        }, 1, 3, TimeUnit.SECONDS);

    }

    private void dump() {
        Iris.info("Starting the dump process.");
        int threads = Runtime.getRuntime().availableProcessors();
        AtomicInteger f = new AtomicInteger();
        for (File mcaFile : MCADirectory.listFiles()) {
            if (mcaFile.getName().endsWith(".mca")) {
                executor.submit(() -> {
                    try {
                        processMCARegion(MCAUtil.read(mcaFile));
                    } catch (Exception e) {
                        f.getAndIncrement();
                        Iris.error("Failed to read mca file");
                        e.printStackTrace();
                    }
                });
            }
        }
    }

    private void processMCARegion(MCAFile mca) {
        AtomicReferenceArray<Chunk> chunks = new AtomicReferenceArray<>(1024);
        for (int i = 0; i < chunks.length(); i++) {
            chunks.set(i, mca.getChunks().get(i));
        }
        for (int i = 0; i < chunks.length(); i++) {
            Chunk chunk = chunks.get(i);
            if (chunk != null) {
                int CHUNK_HEIGHT = (world.getMaxHeight() - world.getMinHeight());
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = 0; y < CHUNK_HEIGHT; y++) {
                            CompoundTag tag = chunk.getBlockStateAt(x, y, z);
                            int biome = chunk.getBiomeAt(x, y, z);
                            if (tag == null) {
                                String blockName = "minecraft:air";
                                //storage.compute(blockName, (key, count) -> (count == null) ? 1 : count + 1);
                                airStorage.getAndIncrement();
                                int ii = 0;
                            } else {
                                StringTag nameTag = tag.getStringTag("Name");
                                String blockName = nameTag.getValue();
                                blockData data = new blockData(blockName, biome, y);
                                storage.compute(data, (key, count) -> (count == null) ? 1 : count + 1);
                                int ii = 0;
                            }
                        }
                    }
                }
                chunksProcessed.getAndIncrement();
            }
        }
        regionsProcessed.getAndIncrement();
    }

    private int totalMCAChunks() {
        AtomicInteger chunks = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(totalMcaFiles() * 1024);
        for (File mcafile : MCADirectory.listFiles()) {
            executor.submit(() -> {
                try {
                    if (mcafile.getName().endsWith(".mca")) {
                        MCAFile mca = MCAUtil.read(mcafile);
                        for (int width = 0; width < 32; width++) {
                            for (int depth = 0; depth < 32; depth++) {
                                Chunk chunk = mca.getChunk(width, depth);
                                if (chunk != null) {
                                    chunks.getAndIncrement();
                                }
                                latch.countDown();
                            }
                        }
                    }
                } catch (Exception e) {
                    Iris.error("Failed to read mca file");
                    e.printStackTrace();
                }
            });
        }

        try {
            latch.await();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return chunks.get();
    }

    private int totalMcaFiles() {
        int size = 0;
        for (File mca : MCADirectory.listFiles()) {
            if (mca.getName().endsWith(".mca")) {
                size++;
            }
        }
        return size;
    }

    private long computeETA() {
        return (long) (totalMaxChunks.get() > 1024 ? // Generated chunks exceed 1/8th of total?
                // If yes, use smooth function (which gets more accurate over time since its less sensitive to outliers)
                ((totalMaxChunks.get() - chunksProcessed.get()) * ((double) (M.ms() - startTime.get()) / (double) chunksProcessed.get())) :
                // If no, use quick function (which is less accurate over time but responds better to the initial delay)
                ((totalMaxChunks.get() - chunksProcessed.get()) / chunksPerSecond.getAverage()) * 1000
        );
    }

    public class blockData {
        @Getter
        @Setter
        private String block;
        private int biome;
        private int height;

        public blockData(String b, int bm, int h) {
            this.block = b;
            this.height = h;
            this.biome = bm;
        }
    }

}
