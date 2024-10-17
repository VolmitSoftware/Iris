package com.volmit.iris.engine.object;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.annotations.ArrayType;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.platform.BukkitChunkGenerator;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.context.ChunkContext;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.math.*;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import io.papermc.lib.PaperLib;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.IntStream;

@AllArgsConstructor
@NoArgsConstructor
@Desc("Dimension Merging only supports 1 for now.")
@Data
public class IrisMerger {
    // todo
    /**
     * Filler approach:
     * - To see and detect ravines and such use a method to see the dimensions of the 2d plate
     * - If there is air on the border of the chunk generate the next one as well
     *
     */

    private transient RollingSequence mergeDuration = new RollingSequence(20);
    private transient Engine engine;


    @Desc("Selected Generator")
    private String generator;

    @Desc("Uses the generator as a datapack key")
    private boolean datapackMode;

//    @Desc("Merging strategy")
//    private IrisMergeStrategies mode;

    @Desc("How deep till it should use vanilla terrain")
    private int depth = 30;

    /**
     * Merges underground from a selected chunk into the corresponding chunk in the outcome world.
     */
    @Deprecated
    public void generateVanillaUnderground(int x, int z, Engine engine) {
        if (engine.getMemoryWorld() == null)
            throw new IllegalStateException("MemoryWorld is null. Ensure that it has been initialized.");
        if (engine.getWorld() == null)
            throw new IllegalStateException("World is null. Ensure that the world has been properly loaded.");

        try {

            PrecisionStopwatch p = PrecisionStopwatch.start();
            Hunk<BlockData> vh = Hunk.newArrayHunk(16, engine.getMemoryWorld().getBukkit().getMaxHeight() - engine.getMemoryWorld().getBukkit().getMinHeight(), 16);
            Hunk<Biome> vbh = Hunk.newArrayHunk(16, engine.getMemoryWorld().getBukkit().getMaxHeight() - engine.getMemoryWorld().getBukkit().getMinHeight(), 16);

//            PaperLib.getChunkAtAsync(engine.getMemoryWorld().getBukkit(), x, z, true).thenAccept((i) -> {
//                memoryWorldToHunk(engine.getMemoryWorld().getChunkData(x, z), vh, vbh, engine);
//            }).get();
            memoryWorldToHunk(engine.getMemoryWorld().getChunkData(x, z), vh, vbh, engine);

            //removeSurface(vh, x, z, engine);

            int totalHeight = engine.getMemoryWorld().getBukkit().getMaxHeight() - engine.getMemoryWorld().getBukkit().getMinHeight();
            int minHeight = Math.abs(engine.getMemoryWorld().getBukkit().getMinHeight());

            ChunkContext context = new ChunkContext(x << 4, z << 4, engine.getComplex());
            boolean vanillaMode = false;
            List<Biome> caveBiomes = Arrays.asList(
                    Biome.DRIPSTONE_CAVES,
                    Biome.LUSH_CAVES,
                    Biome.DEEP_DARK
            );
            for (int xx = 0; xx < 16; xx++) {
                for (int zz = 0; zz < 16; zz++) {
                    for (int y = 0; y < totalHeight; y++) {
                        int height = (int) Math.ceil(context.getHeight().get(xx, zz) - depth);
                        if (y < height || vanillaMode) {
                            BlockData blockData = vh.get(xx, y, zz);
                            Biome biome = vbh.get(xx, y, zz);
                            if (blockData != null) {
                                INMS.get().setBlock(engine.getWorld().realWorld(), x * 16 + xx, y - minHeight, z * 16 + zz, blockData, 1042, 0);
                                if (biome != null && caveBiomes.contains(biome)) {
                                    engine.getWorld().realWorld().setBiome(x * 16 + xx, y - minHeight, z * 16 + zz, biome);
                                }
                            }
                        }
                    }
                }
            }

            mergeDuration.put(p.getMilliseconds());
            Iris.info("Vanilla merge average in: " + Form.duration(mergeDuration.getAverage(), 8));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void memoryWorldToHunk(ChunkGenerator.ChunkData data, Hunk<BlockData> h, Hunk<Biome> b, Engine engine) {
        int minHeight = engine.getMemoryWorld().getBukkit().getMinHeight();
        int minHeightAbs = Math.abs(minHeight);
        int height = engine.getMemoryWorld().getBukkit().getMaxHeight() - minHeight;

        IntStream.range(0, height).parallel().forEach(y -> {
            int dataY = y - minHeightAbs;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    BlockData block = data.getBlockData(x, dataY, z);
                    Biome biome = data.getBiome(x, dataY, z);
                    h.set(x, y, z, block);
                    b.set(x, y, z, biome);
                }
            }
        });
    }

}
