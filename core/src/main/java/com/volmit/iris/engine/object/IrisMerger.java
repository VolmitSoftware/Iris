package com.volmit.iris.engine.object;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.IMemoryWorld;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.core.nms.container.Pair;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.context.ChunkedDataCache;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.math.RollingSequence;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import com.volmit.iris.util.scheduling.Queue;
import com.volmit.iris.util.scheduling.ShurikenQueue;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

@AllArgsConstructor
@NoArgsConstructor
@Desc("Dimension Merging only supports 1 for now.")
@Data
public class IrisMerger {
    private transient RollingSequence mergeDuration = new RollingSequence(20);
    private transient World worldsave;
    private transient ReentrantLock lock = new ReentrantLock();
    private transient ChunkGenerator chunkGenerator;
    private static final BlockData FILLER = Material.STONE.createBlockData();

    @Desc("Selected Generator")
    private String generator;

    @Desc("Uses a world instead of a generator")
    private String world;

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

    @Desc("If it should translate iris deposits/ores to their deepslate variant")
    private boolean deepslateTranslator = true;

    /**
     * Merges underground from a selected chunk into the corresponding chunk in the outcome world.
     */
    @Deprecated
    public void generateVanillaUnderground(int cx, int cz, Engine engine) {
        if (engine.getMemoryWorld() == null)
            throw new IllegalStateException("MemoryWorld is null. Ensure that it has been initialized.");
        if (engine.getWorld().realWorld() == null)
            return;

        try {
            PrecisionStopwatch p = PrecisionStopwatch.start();

            IMemoryWorld memoryWorld;
            World bukkit;

            if (world.isBlank()) {
                throw new UnsupportedOperationException("No.");
                // memoryWorld = engine.getMemoryWorld();
                // bukkit = memoryWorld.getBukkit();
                // chunkData = memoryWorld.getChunkData(x, z);
            } else {
                bukkit = Bukkit.getWorld(world);
                if (bukkit == null) {
                    Iris.info("World " + world + " not loaded yet, cannot generate chunk at (" + cx + ", " + cz + ")");
                    return;
                }
            }

            Chunk chunk = bukkit.getChunkAt(cx, cz);
            Chunk ichunk = engine.getWorld().realWorld().getChunkAt(cx, cz);

            int totalHeight = bukkit.getMaxHeight() - bukkit.getMinHeight();
            int minHeight = Math.abs(bukkit.getMinHeight());

            var world = engine.getWorld().realWorld();
            int wX = cx << 4;
            int wZ = cz << 4;

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

            for (int xx = 0; xx < 16; xx += 4) {
                for (int zz = 0; zz < 16; zz += 4) {
                    int maxHeightInSection = 0;

                    for (int x = 0; x < 4; x++) {
                        for (int z = 0; z < 4; z++) {
                            int globalX = xx + x;
                            int globalZ = zz + z;
                            int height = (int) Math.ceil(cache.get(globalX, globalZ) - depth);
                            if (height > maxHeightInSection) {
                                maxHeightInSection = height;
                            }
                        }
                    }

                    Hunk<BlockData> vh = getHunkSlice(chunk, xx, zz, maxHeightInSection);
                    Hunk<BlockData> ih = getHunkSlice(ichunk, xx, zz, maxHeightInSection);

                    for (int x = 0; x < 4; x++) {
                        for (int z = 0; z < 4; z++) {
                            int globalX = xx + x;
                            int globalZ = zz + z;
                            int height = (int) Math.ceil(cache.get(globalX, globalZ) - depth);

                            for (int y = 0; y < totalHeight; y++) {
                                if (shouldSkip(y, height))
                                    continue;

                                BlockData blockData = vh.get(x, y, z);
                                if (!blockData.getMaterial().isAir() && deepslateTranslator) {
                                    if (ih.get(x, y, z).getMaterial() != FILLER.getMaterial() && blockData.getMaterial().isOccluding()) {
                                        try {
                                            BlockData newBlockData = ih.get(x, y, z);
                                            if (hasAround(vh, x, y, z, Material.DEEPSLATE)) {
                                                String id = newBlockData.getMaterial().getItemTranslationKey().replaceFirst("^block\\.[^.]+\\.", "").toUpperCase();
                                                id = "DEEPSLATE_" + id;
                                                Material dps = Material.getMaterial(id);
                                                if (dps != null)
                                                    blockData = dps.createBlockData();
                                            }
                                        } catch (Exception e) {
                                            // not* Handle exception
                                        }
                                    }
                                }

                                nms.setBlock(
                                        world,
                                        wX + globalX,
                                        y - minHeight,
                                        wZ + globalZ,
                                        blockData,
                                        flag,
                                        0
                                );

                                if (nms.hasTile(blockData.getMaterial())) {
                                    var tile = nms.serializeTile(new Location(bukkit, wX + globalX, y - minHeight, wZ + globalZ));
                                    if (tile != null) {
                                        nms.deserializeTile(tile, new Location(world, wX + globalX, y - minHeight, wZ + globalZ));
                                    }
                                }

                                if (globalX % 4 == 0 && globalZ % 4 == 0 && y % 4 == 0) {
                                    Biome biome;
                                    biome = bukkit.getBiome(wX + globalX, y, wZ + globalZ);
                                    if (caveBiomes.contains(biome)) {
                                        world.setBiome(wX + globalX, y - minHeight, wZ + globalZ, biome);
                                    }
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

    /**
     * Retrieves a 4x4 hunk slice starting at (sx, sz) up to the specified height.
     *
     * @param chunk  The Bukkit chunk
     * @param sx     Chunk Slice X (must be multiple of 4)
     * @param sz     Chunk Slice Z (must be multiple of 4)
     * @param height The maximum height to process
     * @return A hunk of size 4x(totalHeight)x4
     */
    private Hunk<BlockData> getHunkSlice(Chunk chunk, int sx, int sz, int height) {
        if (!chunk.isGenerated())
            throw new IllegalStateException("Chunk is not generated!");

        if (!chunk.isLoaded()) {
            chunk.load();
        }

        int minHeight = chunk.getWorld().getMinHeight();
        int maxHeight = chunk.getWorld().getMaxHeight();
        int totalHeight = Math.abs(minHeight) + maxHeight;

        Hunk<BlockData> h = Hunk.newHunk(4, totalHeight, 4);

        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                for (int y = 0; y < totalHeight; y++) {
                    if (shouldSkip(y, height))
                        continue;
                    BlockData data = chunk.getBlock(sx + x, y + minHeight, sz + z).getBlockData();
                    h.set(x, y, z, data);
                }
            }
        }

        return h;
    }

    private boolean hasAround(Hunk<BlockData> hunk, int x, int y, int z, Material material) {
        int[] d = {-1, 0, 1};

        for (int dx : d) {
            for (int dy : d) {
                for (int dz : d) {

                    if (dx == 0 && dy == 0 && dz == 0) continue;

                    int nx = x + dx;
                    int ny = y + dy;
                    int nz = z + dz;

                    if (nx >= 0 && nx < hunk.getWidth() && nz >= 0 && nz < hunk.getDepth() && ny >= 0 && ny < hunk.getHeight()) {
                        BlockData neighborBlock = hunk.get(nx, ny, nz);

                        if (neighborBlock.getMaterial() == material) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    public static <T> Hunk<T> copyHunkParallel(Hunk<T> original, Function<T, T> elementCopier) {
        Hunk<T> copy = Hunk.newHunk(original.getWidth(), original.getHeight(), original.getDepth());
        original.compute3D((ox, oy, oz, section) -> {
            Hunk<T> copySection = copy.croppedView(ox, oy, oz, ox + section.getWidth(), oy + section.getHeight(), oz + section.getDepth());
            section.iterate((x, y, z, value) -> {
                T copiedValue = value != null ? elementCopier.apply(value) : null;
                copySection.set(x, y, z, copiedValue);
            });
        });

        return copy;
    }

    public void loadWorld(Engine engine) {
        if (!engine.getDimension().isEnableExperimentalMerger())
            return;

        World bukkitWorld = Bukkit.getWorld(world);
        if (!new File(Bukkit.getWorldContainer(), world).exists())
            throw new IllegalStateException("World does not exist!");
        if (bukkitWorld == null) {
            Iris.info("World " + world + " is not loaded yet, creating it.");

            Bukkit.getPluginManager().registerEvents(new Listener() {
                @EventHandler
                public void onWorldLoad(WorldLoadEvent event) {
                    if (event.getWorld().getName().equals(world)) {
                        worldsave = event.getWorld();
                        Iris.info("World " + world + " has been loaded.");
                    }
                }
            }, Iris.instance);

            WorldCreator worldCreator = new WorldCreator(world);
            Bukkit.createWorld(worldCreator);
        } else {
            worldsave = bukkitWorld;
        }
    }
}
