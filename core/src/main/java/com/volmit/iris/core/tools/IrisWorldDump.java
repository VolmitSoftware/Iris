package com.volmit.iris.core.tools;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RollingSequence;
import com.volmit.iris.util.nbt.mca.Chunk;
import com.volmit.iris.util.nbt.mca.MCAFile;
import com.volmit.iris.util.nbt.mca.MCAUtil;
import com.volmit.iris.util.nbt.tag.CompoundTag;
import com.volmit.iris.util.nbt.tag.StringTag;
import com.volmit.iris.util.plugin.VolmitSender;
import org.bukkit.World;

import java.io.File;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class IrisWorldDump {
    private KList<MCAFile> mcaList;
    private KMap<String, Long> storage;
    private AtomicLong airStorage;
    private World world;
    private File MCADirectory;
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
    private mode mode;
    private File dumps;
    private File worldDump;
    private int mcaCacheSize;
    private File temp;
    private File blocks;
    private File structures;

    public IrisWorldDump(World world, VolmitSender sender, mode mode) {
        sender.sendMessage("Initializing IrisWorldDump...");
        this.world = world;
        this.sender = sender;
        this.MCADirectory = new File(world.getWorldFolder(), "region");
        this.totalMCAFiles = new AtomicInteger(MCACount());
        this.dumps = new File("plugins"  + File.separator + "iris", "dumps");
        this.worldDump = new File(dumps, world.getName());
        this.mcaCacheSize = IrisSettings.get().getWorldDump().mcaCacheSize;
        this.regionsProcessed = new AtomicInteger(0);
        this.chunksProcessed = new AtomicInteger(0);
        this.totalToProcess = new AtomicInteger(0);
        this.totalMaxChunks = new AtomicInteger(totalMCAFiles.get() * 1024);
        this.chunksPerSecond = new RollingSequence(10);
        this.temp = new File(worldDump, "temp");
        this.executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() - 1);
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.startTime = new AtomicLong();
        this.storage = new KMap<>();
        this.airStorage = new AtomicLong(0);
        this.blocks = new File(worldDump, "blocks");
        this.structures = new File(worldDump, "structures");
        initialize();
        try {
            this.engine = IrisToolbelt.access(world).getEngine();
            this.IrisWorld = true;
        } catch (Exception e) {
            this.IrisWorld = false;
        }
    }

    private void initialize() {
        if (!dumps.exists()) {
            if (!dumps.mkdirs()) {
                System.err.println("Failed to create dump directory.");
                return;
            }
        }

        if (worldDump.exists() && !worldDump.delete()) {
            System.err.println("Failed to delete existing world dump directory.");
            return;
        }

        if (!worldDump.mkdir()) {
            System.err.println("Failed to create world dump directory.");
            return;
        }

        if (!blocks.mkdir()) {
            System.err.println("Failed to create blocks directory.");
            return;
        }

        if (!structures.mkdir()) {
            System.err.println("Failed to create structures directory.");
            return;
        }
        for (File mcaFile : MCADirectory.listFiles()) {
            if (mcaFile.getName().endsWith(".mca")) {
                totalToProcess.getAndIncrement();
            }
        }
    }

    public void start() {
        dump();
        updater();
    }

    public enum mode {
        RAW {
            @Override
            public void methodDump() {

            }
        },
        DISK {
            @Override
          public void methodDump() {

            }
        },
        PACKED {
            @Override
            public void methodDump() {

            }
        };
        public abstract void methodDump();
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
                        processMCARegion( MCAUtil.read(mcaFile));
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
                            if (tag == null) {
                                String blockName = "minecraft:air";
                                //storage.compute(blockName, (key, count) -> (count == null) ? 1 : count + 1);
                                airStorage.getAndIncrement();
                                int ii = 0;
                            } else {
                                StringTag nameTag = tag.getStringTag("Name");
                                String blockName = nameTag.getValue();
                                storage.compute(blockName, (key, count) -> (count == null) ? 1 : count + 1);
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

    private int MCACount() {
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

}
