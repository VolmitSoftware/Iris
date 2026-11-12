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
import it.unimi.dsi.fastutil.bytes.ByteArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.shorts.ShortArrayList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
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
import java.util.function.UnaryOperator;

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
    // Its place, a shared key and the sides it shows.
    private static final int VISIBLE_BLOCK_BYTES = Integer.BYTES + Integer.BYTES + Byte.BYTES;
    // Its bottom, its top and a shared key.
    private static final int RUN_BYTES = Short.BYTES + Short.BYTES + Integer.BYTES;
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

    // The tile reads each of its columns as soon as its chunk is generated, so it holds them whatever the cache lets go
    // of meanwhile. A tile with several columns to a chunk keeps the chunk for the finer tiles that follow; a coarser one
    // reads one column of each chunk and keeps none, as thousands of them would only push the near ground out. A chunk
    // that fails to generate is left for at() to fail on.
    @Override
    public ColumnTops prepared(TileGrid grid) {
        boolean keepsChunks = grid.step() < BLOCKS_ACROSS;
        int runLevel = runLevelOf(grid.step());
        Long2ObjectMap<LongList> columnsByChunk = columnsByChunkOf(grid);
        Map<Long, ColumnSample> found = new ConcurrentHashMap<>();
        Map<Long, ChunkTop> chunks = new ConcurrentHashMap<>();
        List<Future<?>> generating = new ArrayList<>();
        for (Long2ObjectMap.Entry<LongList> chunk : columnsByChunk.long2ObjectEntrySet()) {
            long key = chunk.getLongKey();
            ChunkTop known = tops.getIfPresent(key);
            if (known != null) {
                read(key, known, keepsChunks, runLevel, chunk.getValue(), found, chunks);
                continue;
            }
            Runnable generation = () -> {
                if (!grid.cancelled().getAsBoolean()) {
                    read(key, keepsChunks ? tops.get(key) : topWithoutKeeping(key), keepsChunks, runLevel, chunk.getValue(), found, chunks);
                }
            };
            ChunkTask task = new ChunkTask(generation, grid.step());
            GENERATORS.execute(task);
            generating.add(task);
        }
        try {
            for (Future<?> task : generating) {
                awaitUnlessCancelled(task, grid.cancelled());
            }
        } catch (CancellationException cancelled) {
            generating.forEach(task -> task.cancel(false));
            throw cancelled;
        }
        return new PreparedTile(found, chunks);
    }

    // A tile a block a column has no runs; a coarser one has those of its columns' areas, or of the largest a chunk has.
    private static int runLevelOf(double blocksPerColumn) {
        int level = (int) Math.round(Math.log(blocksPerColumn) / Math.log(2));
        return blocksPerColumn < 2D ? 0 : Math.min(level, ColumnRuns.MOST_LEVEL);
    }

    /** One column of a tile as read from its chunk: its top, and the runs of the area it stands for, if any. */
    private record ColumnSample(ColumnTop top, AreaRuns runs) {
    }

    /** A tile's columns, and the chunks it keeps, read as its chunks were generated. */
    private final class PreparedTile implements ColumnTops {
        private final Map<Long, ColumnSample> columns;
        private final Map<Long, ChunkTop> chunks;

        PreparedTile(Map<Long, ColumnSample> columns, Map<Long, ChunkTop> chunks) {
            this.columns = columns;
            this.chunks = chunks;
        }

        @Override
        public ColumnTop at(int worldX, int worldZ) {
            ColumnSample column = columns.get(keyOf(worldX, worldZ));
            return column != null ? column.top() : GeneratedChunks.this.at(worldX, worldZ);
        }

        @Override
        public AreaRuns runsAt(int worldX, int worldZ) {
            ColumnSample column = columns.get(keyOf(worldX, worldZ));
            return column == null ? null : column.runs();
        }

        @Override
        public TileVoxels voxelsIn(int minX, int minZ, int size) {
            TileVoxelsBuilder voxels = new TileVoxelsBuilder();
            for (int chunkZ = minZ >> CHUNK_SHIFT; chunkZ <= (minZ + size - 1) >> CHUNK_SHIFT; chunkZ++) {
                for (int chunkX = minX >> CHUNK_SHIFT; chunkX <= (minX + size - 1) >> CHUNK_SHIFT; chunkX++) {
                    long key = keyOf(chunkX, chunkZ);
                    ChunkTop chunk = chunks.get(key);
                    voxels.addInside(chunk != null ? chunk : tops.get(key), chunkX << CHUNK_SHIFT, chunkZ << CHUNK_SHIFT, minX, minZ, size);
                }
            }
            return voxels.build();
        }
    }

    private static final class TileVoxelsBuilder {
        private final ByteArrayList x = new ByteArrayList();
        private final ByteArrayList z = new ByteArrayList();
        private final ShortArrayList y = new ShortArrayList();
        private final List<String> blocks = new ArrayList<>();
        private final ByteArrayList faces = new ByteArrayList();

        void addInside(ChunkTop chunk, int chunkMinX, int chunkMinZ, int minX, int minZ, int size) {
            VisibleBlocks visible = chunk.visible();
            for (int at = 0; at < visible.count(); at++) {
                int position = visible.positions()[at];
                int squareX = chunkMinX + (position & CHUNK_MASK) - minX;
                int squareZ = chunkMinZ + ((position >> CHUNK_SHIFT) & CHUNK_MASK) - minZ;
                if (squareX < 0 || squareZ < 0 || squareX >= size || squareZ >= size) {
                    continue;
                }
                x.add((byte) squareX);
                z.add((byte) squareZ);
                y.add((short) (position >> VisibleBlocks.POSITION_Y_SHIFT));
                blocks.add(visible.blocks()[at]);
                faces.add(visible.faces()[at]);
            }
        }

        TileVoxels build() {
            return new TileVoxels(x.toByteArray(), z.toByteArray(), y.toShortArray(), blocks.toArray(String[]::new), faces.toByteArray());
        }
    }

    private static Long2ObjectMap<LongList> columnsByChunkOf(TileGrid grid) {
        Long2ObjectMap<LongList> columnsByChunk = new Long2ObjectOpenHashMap<>();
        for (int gridZ = 0; gridZ < grid.size(); gridZ++) {
            int worldZ = (int) Math.floor(grid.worldZ(gridZ));
            for (int gridX = 0; gridX < grid.size(); gridX++) {
                int worldX = (int) Math.floor(grid.worldX(gridX));
                long chunk = keyOf(worldX >> CHUNK_SHIFT, worldZ >> CHUNK_SHIFT);
                columnsByChunk.computeIfAbsent(chunk, ignored -> new LongArrayList()).add(keyOf(worldX, worldZ));
            }
        }
        return columnsByChunk;
    }

    private static void read(long key, ChunkTop chunk, boolean keepsChunk, int runLevel, LongList columns, Map<Long, ColumnSample> found,
                             Map<Long, ChunkTop> chunks) {
        readColumns(chunk, runLevel, columns, found);
        if (keepsChunk) {
            chunks.put(key, chunk);
        }
    }

    private ChunkTop topWithoutKeeping(long key) {
        ChunkTop known = tops.getIfPresent(key);
        if (known != null) {
            return known;
        }
        try {
            return generate(key);
        } catch (Exception failed) {
            throw new CompletionException(failed);
        }
    }

    private static void readColumns(ChunkTop chunk, int runLevel, LongList columns, Map<Long, ColumnSample> found) {
        for (long column : columns) {
            int worldX = (int) (column >> Integer.SIZE);
            int worldZ = (int) column;
            ColumnTop top = chunk.columns[(worldZ & CHUNK_MASK) * BLOCKS_ACROSS + (worldX & CHUNK_MASK)];
            AreaRuns runs = runLevel == 0 ? null : runsOf(chunk.runs[runLevel - 1], worldX & CHUNK_MASK, worldZ & CHUNK_MASK, runLevel);
            found.put(column, new ColumnSample(top, runs));
        }
    }

    private static AreaRuns runsOf(ColumnRuns runs, int x, int z, int level) {
        int areasAcross = BLOCKS_ACROSS >> level;
        int area = (z >> level) * areasAcross + (x >> level);
        int first = runs.firstRunOf(area);
        int last = first + runs.counts()[area];
        return new AreaRuns(Arrays.copyOfRange(runs.bottoms(), first, last), Arrays.copyOfRange(runs.tops(), first, last),
                Arrays.copyOfRange(runs.blocks(), first, last), Arrays.copyOfRange(runs.covers(), first, last),
                Arrays.copyOfRange(runs.coverShares(), first, last));
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
        int fluidHeight = engine.getDimension().getFluidHeight();
        ColumnRuns[] runs = new ColumnRuns[ColumnRuns.MOST_LEVEL];
        for (int level = 1; level <= ColumnRuns.MOST_LEVEL; level++) {
            runs[level - 1] = ColumnRuns.of(blocks, level, terrainY, this::seenByPlayers);
        }
        return new ChunkTop(topsOf(blocks, terrainY, this::seenByPlayers), VisibleBlocks.of(blocks, terrainY, fluidHeight, this::seenByPlayers), runs);
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

    /**
     * @param runs the chunk's runs of solid blocks in areas 2, 4, 8 and 16 blocks across, for coarser tiles
     */
    private record ChunkTop(ColumnTop[] columns, VisibleBlocks visible, ColumnRuns[] runs) {
        int bytes() {
            int raisedBlocks = 0;
            for (ColumnTop column : columns) {
                raisedBlocks += column.raised().count();
            }
            int runs = 0;
            for (ColumnRuns level : this.runs) {
                runs += level.tops().length;
            }
            return CHUNK_TOP_BYTES + raisedBlocks * RAISED_BLOCK_BYTES + visible.count() * VISIBLE_BLOCK_BYTES + runs * RUN_BYTES;
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
