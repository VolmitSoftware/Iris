package com.volmit.iris.engine.object;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.IMemoryWorld;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.core.nms.container.Pair;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.context.ChunkedDataCache;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.hunk.view.ChunkDataHunkView;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.math.RollingSequence;
import com.volmit.iris.util.misc.E;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import com.volmit.iris.util.scheduling.Queue;
import com.volmit.iris.util.scheduling.ShurikenQueue;
import it.unimi.dsi.fastutil.Hash;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import static org.bukkit.Bukkit.createChunkData;

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
    private transient KMap<Pair<Position2, Hunk<BlockData>>, Boolean> chunks = new KMap<>();
    private transient Queue<Pair<Position2, Hunk<BlockData>>> queue = new ShurikenQueue<>();

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


//    /**
//     * @param position x,z of the chunk
//     * @param h copies the exact current state of the hunk.
//     */
//    public void queueChunk(Position2 position, Hunk<BlockData> h) {
//        chunks.put(new Pair<>(position, copyHunkParallel(Hunk.newHunk(h.getWidth(), h.getHeight(), h.getDepth()), Function.identity())), false);
//    }
//
//    /**
//     * Register the chunk as completed.
//     * @param position x,z of the chunk
//     */
//    public void registerChunk(Position2 position, Engine engine) {
//        for (Pair<Position2, Hunk<BlockData>> pair : chunks.keySet()) {
//            if (!pair.getA().equals(position))
//                return;
//            if (chunks.get(pair))
//                throw new IllegalStateException("Chunk " + pair.getA() + " is already registered");
//            chunks.put(pair, true);
//            queue.queue(pair);
//            chunks.remove(pair);
//        }
//        executor(engine);
//    }
//
//    private void executor(Engine engine) {
//        if (!lock.isLocked()) {
//            lock.lock();
//            while (queue.hasNext()) {
//                Pair<Position2, Hunk<BlockData>> chunk = queue.next();
//                generateVanillaUnderground(chunk.getA().getX(), chunk.getA().getZ(), chunk.getB(), engine);
//            }
//            lock.unlock();
//        }
//    }

    /**
     * Merges underground from a selected chunk into the corresponding chunk in the outcome world.
     */
    @Deprecated
    public void generateVanillaUnderground(int x, int z, Engine engine) {
        if (engine.getMemoryWorld() == null)
            throw new IllegalStateException("MemoryWorld is null. Ensure that it has been initialized.");
        if (engine.getWorld().realWorld() == null)
            return;

        try {
            PrecisionStopwatch p = PrecisionStopwatch.start();

            IMemoryWorld memoryWorld;
            World bukkit;

            ChunkGenerator.ChunkData chunkData;
            ChunkGenerator.ChunkData chunkDataIris = getChunkDataAt(engine.getWorld().realWorld(), x, z);
            if (world.isBlank()) {
                throw new UnsupportedOperationException("No.");
//                memoryWorld = engine.getMemoryWorld();
//                bukkit = memoryWorld.getBukkit();
//                chunkData = memoryWorld.getChunkData(x, z);
            } else {
                bukkit = Bukkit.getWorld(world);
                if (bukkit == null)
                    throw new IllegalStateException("Somehow world is null? Initialization Failed!");
                chunkData = getChunkDataAt(bukkit, x, z);
            }

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

                        if (chunkDataIris.getBlockData(xx, y - minHeight, zz).getMaterial() != FILLER.getMaterial() && blockData.getMaterial().isOccluding()) {
                            blockData = chunkDataIris.getBlockData(xx, y - minHeight, zz);
                        }

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
                            Biome biome;
                            try {
                                biome = chunkData.getBiome(xx, y, zz);
                            } catch (UnsupportedOperationException e) {
                                biome = bukkit.getBiome(wX + xx, y, wZ + zz);
                            }

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

    public ChunkGenerator.ChunkData getChunkDataAt(World world, int chunkX, int chunkZ) {
        ChunkGenerator.ChunkData chunkData = createChunkData(world);
        Chunk chunk = world.getChunkAt(chunkX, chunkZ);

        if (!chunk.isGenerated())
            throw new IllegalStateException("Chunk is not generated!");

        if (!chunk.isLoaded()) {
            chunk.load();
        }

        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();

        for (int y = minY; y < maxY; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    BlockData blockData = chunk.getBlock(x, y, z).getBlockData();
                    chunkData.setBlock(x, y, z, blockData);
                }
            }
        }
        return chunkData;
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
        J.sfut(() -> {
            worldsave = Bukkit.getWorld(world);
            if (worldsave == null) {
                WorldCreator worldCreator = new WorldCreator(world);
                worldsave = Bukkit.createWorld(worldCreator);
            }
        });
    }
}
