package art.arcane.iris.structure.object;

import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.generation.mantle.ObjectContinuationBundle;
import art.arcane.iris.generation.mantle.ObjectContinuationPersistence;
import art.arcane.iris.world.storage.matter.IrisMatterSupport;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.value.IrisPosition;

import art.arcane.iris.integration.Identifier;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.generation.decoration.tree.TreeBlockMaterial;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.iris.world.storage.matter.TileWrapper;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.MatterMarker;
import art.arcane.volmlib.util.matter.MatterSlice;
import art.arcane.volmlib.util.matter.MatterStructurePOI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Compiles fixed placements once per dimension snapshot and writes only the current chunk's cells.
 * Stored Y coordinates are offsets from the dimension floor; configured positions use absolute world Y.
 */
public final class IrisStaticObjectLayer {
    private static final IrisStaticObjectLayer EMPTY = new IrisStaticObjectLayer(Map.of(), List.of());
    private static final List<Class<?>> REPLACED_METADATA = List.of(
            TileWrapper.class, Identifier.class, String.class, TreeBlockMaterial.class,
            MatterMarker.class, MatterStructurePOI.class);

    private final Map<Long, Chunk> chunks;
    private final List<Placement> placements;
    private final Map<Long, List<Placement>> placementsByChunk;
    private volatile TransitionLayer transitionLayer;

    private IrisStaticObjectLayer(Map<Long, Chunk> chunks, List<Placement> placements) {
        this.chunks = chunks;
        this.placements = placements;
        Map<Long, List<Placement>> indexed = new HashMap<>();
        for (Placement placement : placements) {
            for (long chunk : placement.chunks().keySet()) {
                indexed.computeIfAbsent(chunk, key -> new ArrayList<>()).add(placement);
            }
        }
        indexed.replaceAll((chunk, entries) -> List.copyOf(entries));
        this.placementsByChunk = Map.copyOf(indexed);
    }

    public static IrisStaticObjectLayer compile(IrisDimension dimension, IrisData data) {
        Objects.requireNonNull(dimension.getStaticObjects(), "staticObjects must be a list");
        if (dimension.getStaticObjects().isEmpty()) {
            return EMPTY;
        }
        List<Placement> placements = new ArrayList<>(dimension.getStaticObjects().size());
        for (int index = 0; index < dimension.getStaticObjects().size(); index++) {
            IrisStaticObject entry = dimension.getStaticObjects().get(index);
            try {
                Objects.requireNonNull(entry, "entry must not be null");
                entry.validate(dimension.getMinHeight(), dimension.getMaxHeight());
                if (entry.evaluateCompat(data, dimension.getLoadKey(), index).excluded()) {
                    // The pack report already names the entry; the rest of the layer compiles as authored.
                    continue;
                }
                IrisObject object = data.getObjectLoader().load(entry.getObject());
                if (object == null) {
                    throw new IllegalArgumentException("Object '" + entry.getObject() + "' could not be loaded");
                }
                double scale = entry.resolveScale(dimension);
                if (scale != 1D) {
                    object = object.scaledAroundOrigin(scale, entry.getScaleInterpolation());
                } else if (entry.isSmartBore()) {
                    object = object.copy();
                }
                Compilation compilation = new Compilation(dimension.getMaxHeight() - dimension.getMinHeight());
                IrisPosition position = entry.getPosition();
                object.place(position.getX(), position.getY() - dimension.getMinHeight(),
                        position.getZ(), compilation, entry.toPlacement(), new RNG(entry.getSeed()), data);
                placements.add(compilation.freeze(new ObjectContinuationBundle.PlacementKey(
                        ObjectContinuationBundle.Kind.STATIC, position.getX() >> 4, position.getZ() >> 4, index)));
            } catch (RuntimeException failure) {
                throw new IllegalArgumentException("Invalid staticObjects[" + index + "] in dimension '"
                        + dimension.getLoadKey() + "': " + failure.getMessage(), failure);
            }
        }
        return merge(placements);
    }

    public IrisStaticObjectLayer forTransition(Engine engine) {
        if (engine == null || placements.isEmpty()) {
            return this;
        }
        IrisComplex complex = engine.getComplex();
        if (complex.getTransitionGenerationPlan() == null) {
            return this;
        }
        GenerationHistoryRuntimeRouter router = engine instanceof IrisEngine irisEngine
                ? irisEngine.getGenerationHistoryRuntimeRouter().orElse(null) : null;
        boolean cacheable = !(engine instanceof IrisEngine) || router != null;
        TransitionLayer cached = transitionLayer;
        if (cacheable && cached != null && cached.complex() == complex && cached.router() == router) {
            return cached.layer();
        }
        List<Placement> accepted = new ArrayList<>(placements.size());
        for (Placement placement : placements) {
            Footprint footprint = placement.footprint();
            if (footprint == null || complex.allowsNewGenerationFootprint(
                    footprint.minimumX(), footprint.minimumZ(), footprint.maximumX(), footprint.maximumZ())) {
                accepted.add(placement);
            }
        }
        IrisStaticObjectLayer filtered = accepted.size() == placements.size() ? this : merge(accepted);
        if (cacheable) {
            transitionLayer = new TransitionLayer(complex, router, filtered);
        }
        return filtered;
    }

    public List<Block> blocks(int chunkX, int chunkZ) {
        Chunk chunk = chunks.get(chunkKey(chunkX, chunkZ));
        return chunk == null ? List.of() : chunk.ordered();
    }

    /** Tests world X/Z and internal Y, with the dimension minimum already subtracted. */
    public boolean contains(int x, int y, int z) {
        if (chunks.isEmpty() || y < 0) {
            return false;
        }
        Chunk chunk = chunks.get(chunkKey(x >> 4, z >> 4));
        return chunk != null && chunk.blocks().containsKey(Compilation.blockKey(x, y, z));
    }

    public boolean isEmpty() {
        return chunks.isEmpty();
    }

    public void apply(Engine engine, int x, int z, Hunk<NativeBlockState> output) {
        List<Block> blocks = blocks(x >> 4, z >> 4);
        if (blocks.isEmpty()) {
            return;
        }
        persistContinuations(engine, x >> 4, z >> 4, output.getHeight());
        MantleChunk<Matter> chunk = engine.getMantle().getMantle().useChunk(x >> 4, z >> 4);
        try {
            for (Block block : blocks) {
                Matter section = chunk.getOrCreate(block.y() >> 4);
                for (Class<?> type : REPLACED_METADATA) {
                    MatterSlice<?> slice = section.getSlice(type);
                    if (slice != null) {
                        slice.set(block.x(), block.y() & 15, block.z(), null);
                    }
                }
                NativeBlockState state = block.state();
                if (state.isCustom()) {
                    section.slice(Identifier.class).set(block.x(), block.y() & 15, block.z(),
                            Identifier.fromString(state.deferredPlacementKey()));
                    engine.getMantle().getMantle().flag(x >> 4, z >> 4, MantleFlag.CUSTOM_ACTIVE, true);
                }
                if (block.tile() != null) {
                    section.slice(TileWrapper.class).set(block.x(), block.y() & 15, block.z(),
                            new TileWrapper(block.tile().clone()));
                }
                output.set(block.x(), block.y(), block.z(), state.placementBaseState());
            }
        } finally {
            chunk.release();
        }
    }

    private void persistContinuations(Engine engine, int chunkX, int chunkZ, int height) {
        for (Placement placement : placementsByChunk.getOrDefault(chunkKey(chunkX, chunkZ), List.of())) {
            if (placement.chunks().size() < 2) {
                continue;
            }
            Map<ObjectContinuationBundle.ChunkPosition, Supplier<Matter>> fragments = new HashMap<>();
            for (Map.Entry<Long, Chunk> destination : placement.chunks().entrySet()) {
                long key = destination.getKey();
                fragments.put(new ObjectContinuationBundle.ChunkPosition((int) (key >> 32), (int) key),
                        () -> continuationPayload(destination.getValue(), height));
            }
            Footprint footprint = placement.footprint();
            ObjectContinuationPersistence.persist(engine.getMantle().getMantle(), placement.key(),
                    new ObjectContinuationBundle.Bounds(footprint.minimumX(), footprint.minimumZ(),
                            footprint.maximumX(), footprint.maximumZ()), fragments);
        }
    }

    private static Matter continuationPayload(Chunk chunk, int height) {
        IrisMatterSupport.ensureRegistered();
        Matter payload = new IrisMatter(16, height, 16);
        for (Block block : chunk.ordered()) {
            ObjectContinuationPersistence.put(payload, block.x(), block.y(), block.z(), block.state());
            if (block.tile() != null) {
                ObjectContinuationPersistence.put(payload, block.x(), block.y(), block.z(), block.tile().clone());
            }
        }
        return payload;
    }

    private static IrisStaticObjectLayer merge(List<Placement> placements) {
        if (placements.isEmpty()) {
            return EMPTY;
        }
        Map<Long, Map<Integer, Block>> combined = new HashMap<>();
        for (Placement placement : placements) {
            for (Map.Entry<Long, Chunk> chunk : placement.chunks().entrySet()) {
                combined.computeIfAbsent(chunk.getKey(), key -> new HashMap<>()).putAll(chunk.getValue().blocks());
            }
        }
        return new IrisStaticObjectLayer(freezeChunks(combined), List.copyOf(placements));
    }

    private static Map<Long, Chunk> freezeChunks(Map<Long, Map<Integer, Block>> chunks) {
        Map<Long, Chunk> compiled = new HashMap<>(chunks.size());
        for (Map.Entry<Long, Map<Integer, Block>> chunk : chunks.entrySet()) {
            Map<Integer, Block> blocks = Map.copyOf(chunk.getValue());
            compiled.put(chunk.getKey(), new Chunk(blocks, List.copyOf(blocks.values())));
        }
        return Map.copyOf(compiled);
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    public record Block(int x, int y, int z, NativeBlockState state, TileData tile) {
    }

    private record Chunk(Map<Integer, Block> blocks, List<Block> ordered) {
    }

    private record Footprint(int minimumX, int minimumZ, int maximumX, int maximumZ) {
    }

    private record Placement(ObjectContinuationBundle.PlacementKey key, Map<Long, Chunk> chunks, Footprint footprint) {
    }

    private record TransitionLayer(IrisComplex complex, GenerationHistoryRuntimeRouter router, IrisStaticObjectLayer layer) {
    }

    private static final class Compilation implements IObjectPlacer {
        private final int height;
        private final Map<Long, Map<Integer, Block>> chunks = new HashMap<>();
        private int minimumX = Integer.MAX_VALUE;
        private int minimumZ = Integer.MAX_VALUE;
        private int maximumX = Integer.MIN_VALUE;
        private int maximumZ = Integer.MIN_VALUE;

        private Compilation(int height) {
            this.height = height;
        }

        private Placement freeze(ObjectContinuationBundle.PlacementKey key) {
            return new Placement(key, freezeChunks(chunks), chunks.isEmpty() ? null
                    : new Footprint(minimumX, minimumZ, maximumX, maximumZ));
        }

        @Override
        public void set(int x, int y, int z, NativeBlockState state) {
            if (y < 0 || y >= height) {
                throw new IllegalArgumentException("Transformed object extends outside the dimension height");
            }
            if (Math.abs((long) x) > 29999984L || Math.abs((long) z) > 29999984L) {
                throw new IllegalArgumentException("Transformed object extends outside the world coordinate limits");
            }
            Objects.requireNonNull(state, "Object block must not be null");
            if (state.isCustom() && (state.deferredPlacementKey() == null || state.placementBaseState() == null)) {
                throw new IllegalArgumentException("Custom object block has no placement data: " + state.key());
            }
            minimumX = Math.min(minimumX, x);
            minimumZ = Math.min(minimumZ, z);
            maximumX = Math.max(maximumX, x);
            maximumZ = Math.max(maximumZ, z);
            Map<Integer, Block> chunk = chunks.computeIfAbsent(chunkKey(x >> 4, z >> 4), key -> new HashMap<>());
            chunk.put(blockKey(x, y, z), new Block(x & 15, y, z & 15, state, null));
        }

        @Override
        public NativeBlockState get(int x, int y, int z) {
            Block block = block(x, y, z);
            return block == null ? IrisObject.States.air() : block.state();
        }

        @Override
        public void setTile(int x, int y, int z, TileData tile) {
            Block block = block(x, y, z);
            if (block != null) {
                chunks.get(chunkKey(x >> 4, z >> 4)).put(blockKey(x, y, z),
                        new Block(block.x(), block.y(), block.z(), block.state(), tile.clone()));
            }
        }

        @Override
        public int getHighest(int x, int z, IrisData data) {
            throw new IllegalStateException("Static objects cannot sample terrain");
        }

        @Override
        public int getHighest(int x, int z, IrisData data, boolean ignoreFluid) {
            return getHighest(x, z, data);
        }

        @Override
        public boolean isPreventingDecay() {
            return false;
        }

        @Override
        public boolean isCarved(int x, int y, int z) {
            return false;
        }

        @Override
        public boolean isSolid(int x, int y, int z) {
            return get(x, y, z).isSolid();
        }

        @Override
        public boolean isUnderwater(int x, int z) {
            return false;
        }

        @Override
        public int getFluidHeight() {
            return 0;
        }

        @Override
        public boolean isDebugSmartBore() {
            return false;
        }

        @Override
        public <T> void setData(int x, int y, int z, T data) {
            throw new IllegalStateException("Static objects cannot create placement markers");
        }

        @Override
        public <T> T getData(int x, int y, int z, Class<T> type) {
            return null;
        }

        @Override
        public Engine getEngine() {
            return null;
        }

        private Block block(int x, int y, int z) {
            Map<Integer, Block> chunk = chunks.get(chunkKey(x >> 4, z >> 4));
            return chunk == null ? null : chunk.get(blockKey(x, y, z));
        }

        private static int blockKey(int x, int y, int z) {
            return (y << 8) | ((z & 15) << 4) | (x & 15);
        }
    }
}
