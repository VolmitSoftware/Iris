package com.volmit.iris.engine.object;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.util.context.ChunkContext;
import com.volmit.iris.util.data.palette.QuartPos;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.math.RollingSequence;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

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
            Hunk<BlockData> vh = Hunk.newArrayHunk(
                    16,
                    engine.getMemoryWorld().getBukkit().getMaxHeight() - engine.getMemoryWorld().getBukkit().getMinHeight(),
                    16
            );
            Hunk<Biome> vbh = Hunk.newArrayHunk(
                    16,
                    engine.getMemoryWorld().getBukkit().getMaxHeight() - engine.getMemoryWorld().getBukkit().getMinHeight(),
                    16
            );

            memoryWorldToHunk(engine.getMemoryWorld().getChunkData(x, z), vh, vbh, engine);

            int totalHeight = engine.getMemoryWorld().getBukkit().getMaxHeight() - engine.getMemoryWorld().getBukkit().getMinHeight();
            int minHeight = Math.abs(engine.getMemoryWorld().getBukkit().getMinHeight());

            ChunkContext context = new ChunkContext(x << 4, z << 4, engine.getComplex());
            boolean vanillaMode = false;

            Set<Biome> caveBiomes = new HashSet<>(Arrays.asList(
                    Biome.DRIPSTONE_CAVES,
                    Biome.LUSH_CAVES,
                    Biome.DEEP_DARK
            ));

            for (int xx = 0; xx < 16; xx++) {
                for (int zz = 0; zz < 16; zz++) {
                    for (int y = 0; y < totalHeight; y++) {
                        int height = (int) Math.ceil(context.getHeight().get(xx, zz) - depth);
                        if (y < height || vanillaMode) {
                            BlockData blockData = vh.get(xx, y, zz);
                            Biome biome = vbh.get(xx, y, zz);
                            if (blockData != null) {
                                INMS.get().setBlock(
                                        engine.getWorld().realWorld(),
                                        x * 16 + xx,
                                        y - minHeight,
                                        z * 16 + zz,
                                        blockData,
                                        new Flags(false, false, true, false, false).value(),
                                        0
                                );

                                if (biome != null && caveBiomes.contains(biome)) {
                                    engine.getWorld().realWorld().setBiome(
                                            x * 16 + xx,
                                            y - minHeight,
                                            z * 16 + zz,
                                            biome
                                    );
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
        int maxHeight = engine.getMemoryWorld().getBukkit().getMaxHeight();
        int minHeightAbs = Math.abs(minHeight);
        int height = maxHeight - minHeight;
        int minSection = minHeight >> 4;
        int maxSection = (maxHeight - 1) >> 4;

        for (int sectionY = minSection; sectionY <= maxSection; sectionY++) {
            int qY = QuartPos.fromSection(sectionY);
            for (int sX = 0; sX < 4; sX++) {
                for (int sZ = 0; sZ < 4; sZ++) {
                    for (int sY = 0; sY < 4; sY++) {
                        int localX = sX << 2;
                        int localY = (qY << 2) + (sY << 2) - minHeight;
                        int adjustedY = localY + minHeightAbs;
                        int localZ = sZ << 2;
                        if (localY < 0 || adjustedY >= height) {
                            continue;
                        }

                        h.set(localX, adjustedY, localZ, data.getBlockData(localX, localY, localZ));
                        b.set(localX, adjustedY, localZ, data.getBiome(localX, localY, localZ));
                    }
                }
            }
        }
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
