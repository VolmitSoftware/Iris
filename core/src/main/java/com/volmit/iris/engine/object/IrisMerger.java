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
import com.volmit.iris.util.math.RollingSequence;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator;

@AllArgsConstructor
@NoArgsConstructor
@Desc("Dimension Merging only supports 1 for now.")
@Data
public class IrisMerger {

    private transient RollingSequence mergeDuration = new RollingSequence(20);
    private transient Engine engine;

    @Desc("Selected Generator")
    private String generator;
//
    @Desc("Uses the generator as a datapack key")
    private boolean datapackMode;
//
//    @Desc("Merging strategy")
//    private IrisMergeStrategies mode;

    @Desc("How deep till it should use vanilla terrain")
    private int depth = 10;

    /**
     * Merges underground from a selected chunk into the corresponding chunk in the outcome world.
     */
    @Deprecated
    public void generateVanillaUnderground(int x, int z, Engine engine) {
        if (engine.getMemoryWorld() == null)
            throw new IllegalStateException("MemoryWorld is null. Ensure that it has been initialized.");
        if (engine.getWorld() == null)
            throw new IllegalStateException("World is null. Ensure that the world has been properly loaded.");

        PrecisionStopwatch p = PrecisionStopwatch.start();
        Hunk<BlockData> vh = memoryWorldToHunk(engine.getMemoryWorld().getChunkData(x, z), engine);
        int totalHeight = engine.getMemoryWorld().getBukkit().getMaxHeight() - engine.getMemoryWorld().getBukkit().getMinHeight();
        int minHeight = Math.abs(engine.getMemoryWorld().getBukkit().getMinHeight());

        ChunkContext context = new ChunkContext(x << 4, z << 4, engine.getComplex());
        for (int xx = 0; xx < 16; xx++) {
            for (int zz = 0; zz < 16; zz++) {
                for (int y = 0; y < totalHeight; y++) {
                    //int height = engine.getHeight(x * 16 + xx, z * 16 + zz, true) - 10;
                    int height = (int) Math.ceil(context.getHeight().get(xx,zz) - depth);
                    if (y < height) {
                        BlockData blockData = vh.get(xx, y, zz);
                        if (blockData != null) {

                            INMS.get().setBlock(engine.getWorld().realWorld(), x * 16 + xx, y - minHeight , z * 16 + zz, blockData, 1042, 0);
                        }
                    }
                }
            }
        }
        mergeDuration.put(p.getMilliseconds());
        Iris.info("Vanilla merge average in: " + Form.duration(mergeDuration.getAverage(), 8));
    }

    private Hunk<BlockData> memoryWorldToHunk(ChunkGenerator.ChunkData data, Engine engine) {
        Hunk<BlockData> h = Hunk.newArrayHunk(16, engine.getMemoryWorld().getBukkit().getMaxHeight() - engine.getMemoryWorld().getBukkit().getMinHeight(), 16);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < engine.getMemoryWorld().getBukkit().getMaxHeight() - engine.getMemoryWorld().getBukkit().getMinHeight(); y++) {
                    BlockData block = data.getBlockData(x, y, z);
                    h.set(x, y, z, block);
                }
            }
        }
        return h;
    }
}
