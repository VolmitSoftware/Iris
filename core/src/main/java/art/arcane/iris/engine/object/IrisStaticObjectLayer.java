package art.arcane.iris.engine.object;

import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.TreeBlockMaterial;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.iris.util.project.matter.TileWrapper;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterMarker;
import art.arcane.volmlib.util.matter.MatterSlice;
import art.arcane.volmlib.util.matter.MatterStructurePOI;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Compiles fixed placements once per dimension snapshot and writes only the current chunk's cells.
 * Stored Y coordinates are offsets from the dimension floor; configured positions use absolute world Y.
 */
public final class IrisStaticObjectLayer {
    private static final IrisStaticObjectLayer EMPTY = new IrisStaticObjectLayer(Map.of());
    private static final List<Class<?>> REPLACED_METADATA = List.of(
            TileWrapper.class, Identifier.class, String.class, TreeBlockMaterial.class,
            MatterMarker.class, MatterStructurePOI.class);

    private final Map<Long, Chunk> chunks;

    private IrisStaticObjectLayer(Map<Long, Chunk> chunks) {
        this.chunks = chunks;
    }

    public static IrisStaticObjectLayer compile(IrisDimension dimension, IrisData data) {
        Objects.requireNonNull(dimension.getStaticObjects(), "staticObjects must be a list");
        if (dimension.getStaticObjects().isEmpty()) {
            return EMPTY;
        }
        Compilation compilation = new Compilation(dimension.getMaxHeight() - dimension.getMinHeight());
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
                IrisPosition position = entry.getPosition();
                object.place(position.getX(), position.getY() - dimension.getMinHeight(),
                        position.getZ(), compilation, entry.toPlacement(), new RNG(entry.getSeed()), data);
            } catch (RuntimeException failure) {
                throw new IllegalArgumentException("Invalid staticObjects[" + index + "] in dimension '"
                        + dimension.getLoadKey() + "': " + failure.getMessage(), failure);
            }
        }
        return compilation.freeze();
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

    public void apply(Engine engine, int x, int z, Hunk<PlatformBlockState> output) {
        List<Block> blocks = blocks(x >> 4, z >> 4);
        if (blocks.isEmpty()) {
            return;
        }
        MantleChunk<Matter> chunk = engine.getMantle().getMantle().getChunk(x >> 4, z >> 4);
        chunk.use();
        try {
            for (Block block : blocks) {
                Matter section = chunk.getOrCreate(block.y() >> 4);
                for (Class<?> type : REPLACED_METADATA) {
                    MatterSlice<?> slice = section.getSlice(type);
                    if (slice != null) {
                        slice.set(block.x(), block.y() & 15, block.z(), null);
                    }
                }
                PlatformBlockState state = block.state();
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

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    public record Block(int x, int y, int z, PlatformBlockState state, TileData tile) {
    }

    private record Chunk(Map<Integer, Block> blocks, List<Block> ordered) {
    }

    private static final class Compilation implements IObjectPlacer {
        private final int height;
        private final Map<Long, Map<Integer, Block>> chunks = new HashMap<>();

        private Compilation(int height) {
            this.height = height;
        }

        private IrisStaticObjectLayer freeze() {
            Map<Long, Chunk> compiled = new HashMap<>(chunks.size());
            for (Map.Entry<Long, Map<Integer, Block>> chunk : chunks.entrySet()) {
                Map<Integer, Block> blocks = Map.copyOf(chunk.getValue());
                compiled.put(chunk.getKey(), new Chunk(blocks, List.copyOf(blocks.values())));
            }
            return new IrisStaticObjectLayer(Map.copyOf(compiled));
        }

        @Override
        public void set(int x, int y, int z, PlatformBlockState state) {
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
            Map<Integer, Block> chunk = chunks.computeIfAbsent(chunkKey(x >> 4, z >> 4), key -> new HashMap<>());
            chunk.put(blockKey(x, y, z), new Block(x & 15, y, z & 15, state, null));
        }

        @Override
        public PlatformBlockState get(int x, int y, int z) {
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
