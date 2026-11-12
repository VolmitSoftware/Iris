/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core.studio.map;

import art.arcane.iris.core.link.CustomBlockSource;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.engine.framework.GenerationSessionLease;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.context.IrisContext;
import art.arcane.iris.util.project.hunk.Hunk;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.IntBinaryOperator;
import java.util.function.IntToDoubleFunction;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

/**
 * The tops of chunks generated for the studio alone: each chunk runs through the world's own stages into memory, so its
 * columns hold what the world would, trees and structures included, and nothing is placed in the world. Generating
 * plans the chunk's surroundings in the world's mantle, as generating it in the world would. A chunk is generated once
 * and its top kept; asked for from several bridge threads at once. A tile's chunks are generated side by side on threads
 * of their own, which end once the studio has asked for none for a while.
 */
final class GeneratedChunks implements ColumnTops {
    private static final int CHUNK_SHIFT = 4;
    private static final int BLOCKS_ACROSS = 1 << CHUNK_SHIFT;
    private static final int CHUNK_MASK = BLOCKS_ACROSS - 1;
    // A chunk's top is about 5 KB, and each block raised over its ground adds a height and a shared key. A view with
    // blocks out to the horizon holds tens of thousands of chunks.
    private static final int CHUNK_TOP_BYTES = 5 * 1024;
    private static final int RAISED_BLOCK_BYTES = Short.BYTES + Integer.BYTES;
    private static final long MOST_BYTES_KEPT = 512L * 1024 * 1024;
    private static final String LEASE_NAME = "studio_block_preview";
    // Half the cores, as the bridge does, so a studio filling its view leaves the server room to run.
    private static final int GENERATING_THREADS = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
    private static final long IDLE_THREAD_SECONDS = 30;
    private static final long CANCEL_CHECK_MILLIS = 250;
    private static final ThreadPoolExecutor GENERATORS = idleEndingThreads();

    private final Engine engine;
    private final LoadingCache<Long, ChunkTop> tops;
    private final Map<String, PlatformBlockState> seenByKey = new ConcurrentHashMap<>();

    GeneratedChunks(Engine engine) {
        this.engine = engine;
        this.tops = Caffeine.newBuilder()
                .maximumWeight(MOST_BYTES_KEPT)
                .weigher((Long key, ChunkTop top) -> top.bytes())
                .build(this::generate);
    }

    @Override
    public ColumnTop at(int worldX, int worldZ) {
        ChunkTop chunk = tops.get(keyOf(worldX >> CHUNK_SHIFT, worldZ >> CHUNK_SHIFT));
        return chunk.columns[(worldZ & CHUNK_MASK) * BLOCKS_ACROSS + (worldX & CHUNK_MASK)];
    }

    // A chunk that fails to generate is left for at() to fail on, as it would without this.
    @Override
    public void prepare(TileGrid grid) {
        List<Future<?>> generating = new ArrayList<>();
        for (int chunkZ : chunksAlong(grid, grid::worldZ)) {
            for (int chunkX : chunksAlong(grid, grid::worldX)) {
                long key = keyOf(chunkX, chunkZ);
                if (tops.getIfPresent(key) == null) {
                    ChunkTask chunk = new ChunkTask(() -> generateUnlessCancelled(key, grid.cancelled()), grid.step());
                    GENERATORS.execute(chunk);
                    generating.add(chunk);
                }
            }
        }
        try {
            for (Future<?> chunk : generating) {
                awaitUnlessCancelled(chunk, grid.cancelled());
            }
        } catch (CancellationException cancelled) {
            generating.forEach(chunk -> chunk.cancel(false));
            throw cancelled;
        }
    }

    private void generateUnlessCancelled(long key, BooleanSupplier cancelled) {
        if (!cancelled.getAsBoolean()) {
            tops.get(key);
        }
    }

    private static void awaitUnlessCancelled(Future<?> chunk, BooleanSupplier cancelled) {
        while (true) {
            MapTileRenderer.checkCancelled(cancelled);
            try {
                chunk.get(CANCEL_CHECK_MILLIS, TimeUnit.MILLISECONDS);
                return;
            } catch (TimeoutException stillGenerating) {
                // checked for cancelling again
            } catch (ExecutionException failed) {
                return;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new CancellationException("Map tile render interrupted");
            }
        }
    }

    // The distinct chunks the grid's columns fall in along one axis, in order.
    private static int[] chunksAlong(TileGrid grid, IntToDoubleFunction worldAt) {
        return IntStream.range(0, grid.size())
                .map(cell -> (int) Math.floor(worldAt.applyAsDouble(cell)) >> CHUNK_SHIFT)
                .distinct()
                .toArray();
    }

    /**
     * One chunk to generate for a tile. The chunks of finer tiles go first, then those asked for first: a tile far out
     * spreads over thousands of chunks, and the ground near the camera, in finer tiles, is not kept waiting behind it.
     */
    private static final class ChunkTask extends FutureTask<Void> implements Comparable<ChunkTask> {
        private static final AtomicLong ASKED = new AtomicLong();
        private final double blocksPerColumn;
        private final long asked = ASKED.getAndIncrement();

        ChunkTask(Runnable generation, double blocksPerColumn) {
            super(generation, null);
            this.blocksPerColumn = blocksPerColumn;
        }

        @Override
        public int compareTo(ChunkTask other) {
            int byFineness = Double.compare(blocksPerColumn, other.blocksPerColumn);
            return byFineness != 0 ? byFineness : Long.compare(asked, other.asked);
        }
    }

    private static ThreadPoolExecutor idleEndingThreads() {
        ThreadPoolExecutor threads = new ThreadPoolExecutor(GENERATING_THREADS, GENERATING_THREADS, IDLE_THREAD_SECONDS, TimeUnit.SECONDS,
                new PriorityBlockingQueue<>(), runnable -> {
                    Thread thread = new Thread(runnable, "Iris Studio Chunks");
                    thread.setDaemon(true);
                    return thread;
                });
        threads.allowCoreThreadTimeOut(true);
        return threads;
    }

    private ChunkTop generate(long key) throws Exception {
        int chunkX = (int) (key >> Integer.SIZE);
        int chunkZ = (int) key;
        int height = engine.getHeight();
        Hunk<PlatformBlockState> blocks = Hunk.newArrayHunk(BLOCKS_ACROSS, height, BLOCKS_ACROSS);
        Hunk<PlatformBiome> biomes = Hunk.newArrayHunk(BLOCKS_ACROSS, height, BLOCKS_ACROSS);
        try (GenerationSessionLease lease = engine.acquireGenerationLease(LEASE_NAME);
             IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            engine.getGeneration().generate(chunkX << CHUNK_SHIFT, chunkZ << CHUNK_SHIFT, blocks, biomes, false, lease.sessionId());
        }
        IntBinaryOperator terrainY = (x, z) -> engine.getHeight((chunkX << CHUNK_SHIFT) + x, (chunkZ << CHUNK_SHIFT) + z, true);
        return new ChunkTop(topsOf(blocks, terrainY, this::seenByPlayers));
    }

    // A plugin's block can be solid on the server where players see grass they walk through.
    private PlatformBlockState seenByPlayers(PlatformBlockState block) {
        return seenByKey.computeIfAbsent(block.key(), key -> {
            CustomBlockSource plugins = IrisServices.getOrNull(CustomBlockSource.class);
            Optional<PlatformBlockState> shown = plugins == null
                    ? Optional.empty()
                    : plugins.lookOf(key).map(look -> B.getStateOrNull(look.shownAs(), false));
            return shown.orElse(block);
        });
    }

    private static long keyOf(int chunkX, int chunkZ) {
        return ((long) chunkX << Integer.SIZE) | (chunkZ & 0xFFFFFFFFL);
    }

    private record ChunkTop(ColumnTop[] columns) {
        int bytes() {
            int raisedBlocks = 0;
            for (ColumnTop column : columns) {
                raisedBlocks += column.raised().count();
            }
            return CHUNK_TOP_BYTES + raisedBlocks * RAISED_BLOCK_BYTES;
        }
    }

    /**
     * The top of each column of a chunk's blocks, row by row from the north west.
     *
     * @param terrainY      the height of the terrain's top block in each column, by its x and z in the chunk, which
     *                      the blocks above stand on
     * @param seenByPlayers the state players see in place of a block, which decides whether it is solid; the block
     *                      itself for all but a plugin's
     */
    static ColumnTop[] topsOf(Hunk<PlatformBlockState> blocks, IntBinaryOperator terrainY, UnaryOperator<PlatformBlockState> seenByPlayers) {
        ColumnTop[] columns = new ColumnTop[BLOCKS_ACROSS * BLOCKS_ACROSS];
        for (int z = 0; z < BLOCKS_ACROSS; z++) {
            for (int x = 0; x < BLOCKS_ACROSS; x++) {
                columns[z * BLOCKS_ACROSS + x] = topOf(blocks, x, z, terrainY.applyAsInt(x, z), seenByPlayers);
            }
        }
        return columns;
    }

    // The ground is the highest solid block at or under the terrain's top, so a cave open to the sky lowers it. Solid
    // blocks above it, such as a tree, are raised; what stands on the ground without being solid, such as grass or a
    // layer of snow, is its cover. A column with nothing solid under its raised blocks takes the highest as its ground.
    private static ColumnTop topOf(Hunk<PlatformBlockState> blocks, int x, int z, int terrainY, UnaryOperator<PlatformBlockState> seenByPlayers) {
        RaisedBlocks raised = new RaisedBlocks();
        for (int y = blocks.getHeight() - 1; y >= 0; y--) {
            PlatformBlockState block = blocks.get(x, y, z);
            if (block == null || !isSolid(seenByPlayers.apply(block))) {
                continue;
            }
            if (y > terrainY) {
                raised.add(y, block.key());
                continue;
            }
            return new ColumnTop(y, block.key(), coverOn(blocks, x, y, z, seenByPlayers), raised.toRaised());
        }
        return raised.isEmpty() ? ColumnTop.NOTHING : topOf(blocks, x, z, blocks.getHeight(), seenByPlayers);
    }

    private static String coverOn(Hunk<PlatformBlockState> blocks, int x, int groundY, int z, UnaryOperator<PlatformBlockState> seenByPlayers) {
        PlatformBlockState above = groundY + 1 < blocks.getHeight() ? blocks.get(x, groundY + 1, z) : null;
        boolean isCover = above != null && !above.isAir() && !above.isFluid() && !isSolid(seenByPlayers.apply(above));
        return isCover ? above.key() : null;
    }

    private static boolean isSolid(PlatformBlockState block) {
        return !block.isAir() && !block.isFluid() && block.isSolid();
    }

    private static final class RaisedBlocks {
        private final ShortArrayList ys = new ShortArrayList();
        private final List<String> blocks = new ArrayList<>();

        void add(int y, String block) {
            ys.add((short) y);
            blocks.add(block);
        }

        boolean isEmpty() {
            return blocks.isEmpty();
        }

        Raised toRaised() {
            return isEmpty() ? Raised.NONE : new Raised(ys.toShortArray(), blocks.toArray(String[]::new));
        }
    }
}
