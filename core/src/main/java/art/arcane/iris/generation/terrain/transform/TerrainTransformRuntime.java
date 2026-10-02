package art.arcane.iris.generation.terrain.transform;

import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.context.IrisContext;
import art.arcane.iris.generation.mantle.CaveTerrainSnapshot;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.terrain.Terrain3DColumn;
import art.arcane.iris.world.history.FloatingBiomeOverlay;
import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.hunk.storage.StorageHunk;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.Arrays;
import java.util.Objects;

public final class TerrainTransformRuntime implements AutoCloseable {
    private static final long CACHE_BYTES = 64L * 1024 * 1024;

    private final Engine engine;
    private final IrisComplex complex;
    private final TerrainTransformer transformer;
    private final TerrainColumnTransformer columnTransformer;
    private final int radius;
    private final int minY;
    private final int height;
    private final NativeBlockState air;
    private final Cache<Long, Snapshot> originals;
    private final Cache<Long, Snapshot> transformed;
    private final Cache<Long, ColumnSnapshot> transformedColumns;
    private volatile boolean closed;

    public TerrainTransformRuntime(Options options, TerrainTransformer transformer) {
        engine = Objects.requireNonNull(options, "terrain runtime options").engine();
        complex = options.complex();
        this.transformer = Objects.requireNonNull(transformer, "terrain transformer");
        columnTransformer = transformer.columnTransformer();
        radius = transformer.radius();
        if (radius < 0 || radius > 16) {
            throw new IllegalArgumentException("Terrain transform radius must be between 0 and 16 blocks");
        }
        minY = engine.getMinHeight();
        height = engine.getHeight();
        if (height <= 0) {
            throw new IllegalArgumentException("Terrain height must be positive");
        }
        air = Objects.requireNonNull(B.getState("AIR"), "air block state");
        originals = newCache();
        transformed = newCache();
        transformedColumns = columnTransformer == null ? null : Caffeine.newBuilder().maximumWeight(CACHE_BYTES)
                .weigher((Long key, ColumnSnapshot snapshot) -> snapshot.weight()).build();
    }

    public void generate(int x, int z, Hunk<NativeBlockState> blocks, Hunk<NativeBiome> biomes,
                         boolean multicore, ChunkContext target) {
        requireOpen();
        if ((x & 15) != 0 || (z & 15) != 0 || blocks.getWidth() != 16 || blocks.getDepth() != 16
                || blocks.getHeight() != height || biomes.getWidth() != 16 || biomes.getDepth() != 16
                || biomes.getHeight() != height || target.getComplex() != complex) {
            throw new IllegalArgumentException("Terrain transform output must match its runtime and chunk");
        }
        Snapshot snapshot = transformed(x >> 4, z >> 4);
        target.setTerrainBiomeOutput(biomes);
        for (int y = 0; y < height; y++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    int index = index(localX, y, localZ);
                    blocks.setRaw(localX, y, localZ, snapshot.blocks[index]);
                    NativeBiome biome = snapshot.biomes[index];
                    if (biome != null) {
                        biomes.setRaw(localX, y, localZ, biome);
                    }
                }
            }
        }
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                target.setTerrainHeight(localX, localZ, snapshot.solidHeights[(localZ << 4) | localX]);
            }
        }
        target.setCaveTerrain(snapshot.caves);
        target.setFloatingBiomes(snapshot.floating == null ? null : snapshot.floating.copy());
    }

    public int height(int x, int z, boolean ignoreFluid) {
        requireOpen();
        Snapshot snapshot = fullForQuery(x, z);
        if (snapshot != null) {
            return snapshot.height(x & 15, z & 15, ignoreFluid);
        }
        ColumnSnapshot column = transformedColumn(x, z);
        return ignoreFluid ? column.ground : column.top;
    }

    public int readyHeight(int x, int z, boolean ignoreFluid) {
        requireOpen();
        Snapshot snapshot = transformed.getIfPresent(key(x >> 4, z >> 4));
        if (snapshot != null) {
            return snapshot.height(x & 15, z & 15, ignoreFluid);
        }
        ColumnSnapshot column = readyTransformedColumn(x, z);
        return column == null ? Integer.MIN_VALUE : ignoreFluid ? column.ground : column.top;
    }

    public NativeBlockState block(int worldX, int localY, int worldZ) {
        requireOpen();
        if (localY < 0 || localY >= height) {
            return air;
        }
        return transformed(worldX >> 4, worldZ >> 4).blocks[index(worldX & 15, localY, worldZ & 15)];
    }

    public NativeBlockState readyBlock(int worldX, int localY, int worldZ) {
        requireOpen();
        if (localY < 0 || localY >= height) {
            return air;
        }
        Snapshot snapshot = transformed.getIfPresent(key(worldX >> 4, worldZ >> 4));
        return snapshot == null ? null : snapshot.blocks[index(worldX & 15, localY, worldZ & 15)];
    }

    public Terrain3DColumn column(int x, int z) {
        requireOpen();
        Snapshot snapshot = fullForQuery(x, z);
        return snapshot == null ? transformedColumn(x, z).terrain : snapshot.column(x & 15, z & 15);
    }

    public Terrain3DColumn readyColumn(int x, int z) {
        requireOpen();
        Snapshot snapshot = transformed.getIfPresent(key(x >> 4, z >> 4));
        if (snapshot != null) {
            return snapshot.column(x & 15, z & 15);
        }
        ColumnSnapshot column = readyTransformedColumn(x, z);
        return column == null ? null : column.terrain;
    }

    public int fluidHeight(int x, int z) {
        requireOpen();
        Snapshot snapshot = fullForQuery(x, z);
        return snapshot == null ? transformedColumn(x, z).fluid : snapshot.fluidHeights[((z & 15) << 4) | (x & 15)];
    }

    public int readyFluidHeight(int x, int z) {
        requireOpen();
        Snapshot snapshot = transformed.getIfPresent(key(x >> 4, z >> 4));
        if (snapshot != null) {
            return snapshot.fluidHeights[((z & 15) << 4) | (x & 15)];
        }
        ColumnSnapshot column = readyTransformedColumn(x, z);
        return column == null ? Integer.MIN_VALUE : column.fluid;
    }

    @Override
    public void close() {
        closed = true;
        originals.invalidateAll();
        transformed.invalidateAll();
        originals.cleanUp();
        transformed.cleanUp();
        if (transformedColumns != null) {
            transformedColumns.invalidateAll();
            transformedColumns.cleanUp();
        }
    }

    private Cache<Long, Snapshot> newCache() {
        return Caffeine.newBuilder().maximumWeight(CACHE_BYTES)
                .weigher((Long key, Snapshot snapshot) -> snapshot.weight()).build();
    }

    private Snapshot transformed(int chunkX, int chunkZ) {
        Snapshot snapshot = transformed.get(key(chunkX, chunkZ), ignored -> transform(chunkX, chunkZ));
        if (closed) {
            transformed.invalidateAll();
            requireOpen();
        }
        return snapshot;
    }

    private Snapshot original(int chunkX, int chunkZ) {
        Snapshot snapshot = originals.get(key(chunkX, chunkZ), ignored -> materialize(chunkX, chunkZ));
        if (closed) {
            originals.invalidateAll();
            requireOpen();
        }
        return snapshot;
    }

    private Snapshot fullForQuery(int x, int z) {
        return columnTransformer == null ? transformed(x >> 4, z >> 4)
                : transformed.getIfPresent(key(x >> 4, z >> 4));
    }

    private ColumnSnapshot readyTransformedColumn(int x, int z) {
        return transformedColumns == null ? null : transformedColumns.getIfPresent(key(x, z));
    }

    private ColumnSnapshot transformedColumn(int x, int z) {
        ColumnSnapshot snapshot = transformedColumns.get(key(x, z), ignored -> transformColumn(x, z));
        if (closed) {
            transformedColumns.invalidateAll();
            requireOpen();
        }
        return snapshot;
    }

    private Snapshot materialize(int chunkX, int chunkZ) {
        requireOpen();
        int x = chunkX << 4;
        int z = chunkZ << 4;
        NativeBlockState[] states = new NativeBlockState[Math.multiplyExact(256, height)];
        Arrays.fill(states, air);
        NativeBiome[] biomeStates = new NativeBiome[states.length];
        Hunk<NativeBlockState> blocks = new SnapshotHunk<>(states, air);
        Hunk<NativeBiome> biomes = new SnapshotHunk<>(biomeStates, null);
        long session = engine.getGenerationSessionId();
        ChunkContext context = new ChunkContext(x, z, complex, session, true,
                ChunkContext.PrefillPlan.NATURAL_TERRAIN, engine.getMetrics(), engine.getDimensionStackContext());
        try (IrisContext.Scope scope = IrisContext.open(engine, session, context)) {
            engine.getMode().generateTerrain(x, z, blocks, biomes, false, context);
        }
        FloatingBiomeOverlay floating = context.getFloatingBiomes();
        return new Snapshot(new SnapshotData(states, biomeStates, context.getCaveTerrain(),
                floating == null ? null : floating.copy()), false);
    }

    private Snapshot transform(int chunkX, int chunkZ) {
        requireOpen();
        Snapshot source = original(chunkX, chunkZ);
        TransformContext context = new TransformContext(new TransformTarget(chunkX, chunkZ, false, 0, 0), source);
        try {
            transformer.transform(context);
        } finally {
            context.active = false;
        }
        return new Snapshot(new SnapshotData(context.blocks, source.biomes, source.caves, source.floating), true);
    }

    private ColumnSnapshot transformColumn(int x, int z) {
        requireOpen();
        Snapshot source = original(x >> 4, z >> 4);
        TransformContext context = new TransformContext(
                new TransformTarget(x >> 4, z >> 4, true, x & 15, z & 15), source);
        try {
            columnTransformer.transform(context, x & 15, z & 15);
        } finally {
            context.active = false;
        }
        return new ColumnSnapshot(context.blocks);
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Terrain transform runtime is closed");
        }
    }

    private static int index(int x, int y, int z) {
        return (y << 8) | (z << 4) | x;
    }

    private static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xffffffffL);
    }

    public record Options(Engine engine, IrisComplex complex) {
        public Options {
            Objects.requireNonNull(engine, "terrain engine");
            Objects.requireNonNull(complex, "terrain complex");
        }
    }

    private record TransformTarget(int chunkX, int chunkZ, boolean columnOnly, int localX, int localZ) {
    }

    private record SnapshotData(NativeBlockState[] blocks, NativeBiome[] biomes,
                                CaveTerrainSnapshot caves, FloatingBiomeOverlay floating) {
    }

    private static final class SnapshotHunk<T> extends StorageHunk<T> {
        private final T[] values;
        private final T fallback;

        private SnapshotHunk(T[] values, T fallback) {
            super(16, values.length / 256, 16);
            this.values = values;
            this.fallback = fallback;
        }

        @Override
        public T getRaw(int x, int y, int z) {
            return y < 0 || y >= getHeight() ? fallback : values[index(x, y, z)];
        }

        @Override
        public void setRaw(int x, int y, int z, T value) {
            if (value != null) {
                values[index(x, y, z)] = value;
            }
        }
    }

    private final class TransformContext implements TerrainTransformContext {
        private final int x;
        private final int z;
        private final NativeBlockState[] blocks;
        private final TransformTarget target;
        private final Snapshot[] sources = new Snapshot[9];
        private volatile boolean active = true;

        private TransformContext(TransformTarget target, Snapshot source) {
            this.target = target;
            x = target.chunkX() << 4;
            z = target.chunkZ() << 4;
            if (target.columnOnly()) {
                blocks = new NativeBlockState[height];
                for (int y = 0; y < height; y++) {
                    blocks[y] = source.blocks[index(target.localX(), y, target.localZ())];
                }
            } else {
                blocks = source.blocks.clone();
            }
            sources[4] = source;
        }

        @Override
        public int blockX() {
            return x;
        }

        @Override
        public int blockZ() {
            return z;
        }

        @Override
        public int minY() {
            return minY;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public NativeBlockState original(int worldX, int worldY, int worldZ) {
            requireActive();
            long localX = (long) worldX - x;
            long localZ = (long) worldZ - z;
            if (localX < -radius || localX >= 16L + radius || localZ < -radius || localZ >= 16L + radius) {
                throw new IllegalArgumentException("Terrain transform read exceeds its declared radius");
            }
            long localY = (long) worldY - minY;
            if (localY < 0 || localY >= height) {
                return air;
            }
            int sourceX = worldX >> 4;
            int sourceZ = worldZ >> 4;
            int slot = (sourceZ - (z >> 4) + 1) * 3 + sourceX - (x >> 4) + 1;
            Snapshot snapshot = sources[slot];
            if (snapshot == null) {
                snapshot = TerrainTransformRuntime.this.original(sourceX, sourceZ);
                sources[slot] = snapshot;
            }
            return snapshot.blocks[index(worldX & 15, (int) localY, worldZ & 15)];
        }

        @Override
        public void set(int localX, int localY, int localZ, NativeBlockState state) {
            requireActive();
            Objects.requireNonNull(state, "terrain transform block state");
            if (localX < 0 || localX >= 16 || localZ < 0 || localZ >= 16 || localY < 0 || localY >= height) {
                throw new IllegalArgumentException("Terrain transform write exceeds its output chunk");
            }
            if (target.columnOnly() && (localX != target.localX() || localZ != target.localZ())) {
                throw new IllegalArgumentException("Terrain column transform write exceeds its output column");
            }
            if (complex.allowsMantleWrite(x + localX, z + localZ)) {
                blocks[target.columnOnly() ? localY : index(localX, localY, localZ)] = state;
            }
        }

        private void requireActive() {
            requireOpen();
            if (!active) {
                throw new IllegalStateException("Terrain transform context is no longer active");
            }
        }
    }

    private static final class ColumnSnapshot {
        private final Terrain3DColumn terrain;
        private final int ground;
        private final int top;
        private final int fluid;

        private ColumnSnapshot(NativeBlockState[] blocks) {
            int solidHeight = -1;
            int topHeight = -1;
            int fluidHeight = -1;
            for (int y = blocks.length - 1; y >= 0; y--) {
                NativeBlockState state = blocks[y];
                if (state.isAir()) {
                    continue;
                }
                if (topHeight == -1) {
                    topHeight = y;
                }
                if ((state.isFluid() || state.isWaterLogged()) && fluidHeight == -1) {
                    fluidHeight = y;
                }
                if (!state.isFluid() && solidHeight == -1) {
                    solidHeight = y;
                }
            }
            ground = solidHeight;
            top = topHeight;
            fluid = fluidHeight;
            terrain = Terrain3DColumn.fromOccupancy(ground, blocks.length,
                    y -> !blocks[y].isAir() && !blocks[y].isFluid());
        }

        private int weight() {
            return 192 + terrain.spanCount() * 8;
        }
    }

    private static final class Snapshot {
        private final NativeBlockState[] blocks;
        private final NativeBiome[] biomes;
        private final CaveTerrainSnapshot caves;
        private final FloatingBiomeOverlay floating;
        private final int[] solidHeights;
        private final int[] topHeights;
        private final int[] fluidHeights;
        private final Terrain3DColumn[] columns;

        private Snapshot(SnapshotData data, boolean effective) {
            blocks = data.blocks();
            biomes = data.biomes();
            caves = data.caves();
            floating = data.floating();
            solidHeights = effective ? new int[256] : null;
            topHeights = effective ? new int[256] : null;
            fluidHeights = effective ? new int[256] : null;
            columns = effective ? new Terrain3DColumn[256] : null;
            if (!effective) {
                return;
            }
            Arrays.fill(solidHeights, -1);
            Arrays.fill(topHeights, -1);
            Arrays.fill(fluidHeights, -1);
            int height = blocks.length / 256;
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int column = (z << 4) | x;
                    for (int y = height - 1; y >= 0; y--) {
                        NativeBlockState state = blocks[index(x, y, z)];
                        if (state.isAir()) {
                            continue;
                        }
                        if (topHeights[column] == -1) {
                            topHeights[column] = y;
                        }
                        if ((state.isFluid() || state.isWaterLogged()) && fluidHeights[column] == -1) {
                            fluidHeights[column] = y;
                        }
                        if (!state.isFluid() && solidHeights[column] == -1) {
                            solidHeights[column] = y;
                        }
                    }
                }
            }
        }

        private int height(int x, int z, boolean ignoreFluid) {
            return (ignoreFluid ? solidHeights : topHeights)[(z << 4) | x];
        }

        private Terrain3DColumn column(int x, int z) {
            int index = (z << 4) | x;
            Terrain3DColumn column = columns[index];
            if (column == null) {
                column = Terrain3DColumn.fromOccupancy(solidHeights[index], blocks.length / 256, y -> {
                    NativeBlockState state = blocks[index(x, y, z)];
                    return !state.isAir() && !state.isFluid();
                });
                columns[index] = column;
            }
            return column;
        }

        private int weight() {
            long blockReferences = (long) blocks.length * 20L;
            long caveReferences = caves == null ? 0L : caves.estimatedRetainedBytes();
            long floatingReferences = floating == null ? 0L : (long) blocks.length * 4L;
            return (int) Math.min(Integer.MAX_VALUE, 16384L + blockReferences + caveReferences + floatingReferences);
        }
    }
}
