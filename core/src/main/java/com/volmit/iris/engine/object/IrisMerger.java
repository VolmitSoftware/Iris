package com.volmit.iris.engine.object;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.util.context.ChunkedDataCache;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.hunk.view.ChunkDataHunkView;
import com.volmit.iris.util.math.RollingSequence;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bukkit.Location;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@AllArgsConstructor
@NoArgsConstructor
@Desc("Dimension Merging only supports 1 for now.")
@Data
public class IrisMerger {
    private transient RollingSequence mergeDuration = new RollingSequence(20);
    private transient Engine engine;

    @Desc("Selected Generator")
    private String generator;

    @Desc("Uses the generator as a datapack key")
    private boolean datapackMode;

    @Desc("How deep till it should use vanilla terrain")
    private int depth = 30;

    @Desc("Gets the terrain x,z height as the limit")
    private IrisMergeStrategies mode = null;

    @Desc("If it should put the selected generator above or under the split")
    private boolean splitUnder = true;

    @Desc("Splits in the engine height")
    private int split = 0;

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
            var memoryWorld = engine.getMemoryWorld();
            var bukkit = memoryWorld.getBukkit();

            var chunkData = memoryWorld.getChunkData(x, z);
            var vh = new ChunkDataHunkView(chunkData);

            int totalHeight = bukkit.getMaxHeight() - bukkit.getMinHeight();
            int minHeight = Math.abs(bukkit.getMinHeight());

            var world = engine.getWorld().realWorld();
            int wX = x << 4;
            int wZ = z << 4;

            BurstExecutor b = MultiBurst.burst.burst();
            var cache = new ChunkedDataCache<>(b, engine.getComplex().getHeightStream(), wX, wZ);
            b.complete();

            Set<Biome> caveBiomes = new HashSet<>(Arrays.asList(
                    Biome.DRIPSTONE_CAVES,
                    Biome.LUSH_CAVES,
                    Biome.DEEP_DARK
            ));

            var nms = INMS.get();
            var flag = new Flags(false, false, true, false, false).value();

            for (int xx = 0; xx < 16; xx++) {
                for (int zz = 0; zz < 16; zz++) {
                    int height = (int) Math.ceil(cache.get(xx, zz) - depth);

                    for (int y = 0; y < totalHeight; y++) {
                        if (shouldSkip(y, height)) {
                            continue;
                        }

                        BlockData blockData = vh.get(xx, y, zz);
                        nms.setBlock(
                                world,
                                wX + xx,
                                y - minHeight,
                                wZ + zz,
                                blockData,
                                flag,
                                0
                        );

                        if (nms.hasTile(blockData.getMaterial())) {
                            var tile = nms.serializeTile(new Location(bukkit, wX + xx, y - minHeight, wZ + zz));
                            if (tile != null) {
                                nms.deserializeTile(tile, new Location(world, wX + xx, y - minHeight, wZ + zz));
                            }
                        }

                        if (x % 4 == 0 && z % 4 == 0 && y % 4 == 0) {
                            var biome = chunkData.getBiome(xx, y, zz);
                            if (caveBiomes.contains(biome)) {
                                world.setBiome(wX + xx, y - minHeight, wZ + zz, biome);
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

    private boolean shouldSkip(int y, int ht) {
        int threshold;
        switch (mode) {
            case SPLIT_ENGINE_HEIGHT:
                threshold = ht;
                break;
            case SPLIT:
                threshold = split;
                break;
            default:
                return false;
        }
        return splitUnder ? y > threshold : y < threshold;
    }

    public record Flags(boolean listener, boolean flag, boolean client, boolean update, boolean physics) {
        public static Flags fromValue(int value) {
            return new Flags(
                    (value & 1024) != 0,
                    (value & 64) != 0,
                    (value & 2) != 0,
                    (value & 1) != 0,
                    (value & 16) == 0
            );
        }

        public int value() {
            int value = 0;
            if (!listener) value |= 1024;
            if (flag) value |= 64;
            if (client) value |= 2;
            if (update) value |= 1;
            if (!physics) value |= 16;
            return value;
        }
    }
}
