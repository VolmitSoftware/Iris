package com.volmit.iris.core.pregenerator;

import com.volmit.iris.Iris;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.RollingSequence;
import com.volmit.iris.util.math.Spiraler;
import com.volmit.iris.util.nbt.mca.MCAFile;
import com.volmit.iris.util.nbt.mca.MCAUtil;
import org.bukkit.World;


import java.io.File;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ChunkUpdater {
    private AtomicBoolean cancelled;
    private KList<int[]> chunkMap;
    private final RollingSequence chunksPerSecond;
    private final RollingSequence mcaregionsPerSecond;
    private final AtomicInteger worldheightsize;
    private final AtomicInteger worldwidthsize;
    private final AtomicInteger totalChunks;
    private final AtomicInteger totalMaxChunks;
    private final AtomicInteger totalMcaregions;
    private final AtomicInteger position;
    private final File[] McaFiles;
    private final World world;

    public ChunkUpdater(World world) {
        File cacheDir = new File("plugins" + File.separator + "iris" + File.separator + "cache");
        File chunkCacheDir = new File("plugins" + File.separator + "iris" + File.separator + "cache" + File.separator + "spiral");
        this.chunksPerSecond = new RollingSequence(10);
        this.mcaregionsPerSecond = new RollingSequence(10);
        this.world = world;
        this.chunkMap = new KList<>();
        this.McaFiles = new File(world.getWorldFolder(), "region").listFiles((dir, name) -> name.endsWith(".mca"));
        this.worldheightsize = new AtomicInteger(calculateWorldDimensions(new File(world.getWorldFolder(), "region"), 1));
        this.worldwidthsize = new AtomicInteger(calculateWorldDimensions(new File(world.getWorldFolder(), "region"), 0));
        this.totalMaxChunks = new AtomicInteger((worldheightsize.get() / 16 ) * (worldwidthsize.get() / 16));
        this.position = new AtomicInteger(0);
        this.cancelled = new AtomicBoolean(false);
        this.totalChunks = new AtomicInteger(0);
        this.totalMcaregions = new AtomicInteger(0);
    }

    public void start() {
        Initialize();
    }

    public void Initialize() {
        Iris.info("Initializing..");
        try {
            for (File mca : McaFiles) {
                MCAFile MCARegion = MCAUtil.read(mca);
                for (int pos = 0; pos != totalMaxChunks.get(); pos++) {
                    int[] coords = getChunk(pos);
                    if(MCARegion.hasChunk(coords[0], coords[1])) chunkMap.add(coords);
                }
            }
            Iris.info("Finished Initializing..");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int calculateWorldDimensions(File regionDir, Integer o) {
        File[] files = regionDir.listFiles((dir, name) -> name.endsWith(".mca"));

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (File file : files) {
            String[] parts = file.getName().split("\\.");
            int x = Integer.parseInt(parts[1]);
            int z = Integer.parseInt(parts[2]);

            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (z < minZ) minZ = z;
            if (z > maxZ) maxZ = z;
        }

        int height = (maxX - minX + 1) * 32 * 16;
        int width = (maxZ - minZ + 1) * 32 * 16;

        if (o == 1) {
            return height;
        }
        if (o == 0) {
            return width;
        }
        return 0;
    }

    public int[] getChunk(int position) {
        int p = -1;
        AtomicInteger xx = new AtomicInteger();
        AtomicInteger zz = new AtomicInteger();
        Spiraler s = new Spiraler(worldheightsize.get() * 2, worldwidthsize.get() * 2, (x, z) -> {
            xx.set(x);
            zz.set(z);
        });

        while (s.hasNext() && p++ < position) {
            s.next();
        }
        int[] coords = new int[2];
        coords[0] = xx.get();
        coords[1] = zz.get();

        return coords;
    }
}
