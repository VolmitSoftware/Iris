package com.volmit.support;

import com.volmit.block.BlockState;
import com.volmit.geometry.BlockBox;
import com.volmit.geometry.BlockPosition;
import com.volmit.mantle.CarvedVoid;
import com.volmit.mantle.Mantle;
import com.volmit.mantle.MantleEdit;
import com.volmit.mantle.MantleKey;
import com.volmit.mantle.MantleLayer;
import com.volmit.mantle.MantleVisitor;
import com.volmit.resource.ResourceId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/** An in-memory mantle over fixed bounds; an invocation sees it through {@link #within(BlockBox)}. */
public final class MemoryMantle {
    private static final Comparator<BlockPosition> VISIT_ORDER = Comparator.comparingInt(BlockPosition::x)
            .thenComparingInt(BlockPosition::y).thenComparingInt(BlockPosition::z);

    /** Where the planned blocks are kept; the host owns them, so no module registers this layer. */
    private static final MantleKey<BlockState> PLANNED_BLOCKS = new MantleKey<>(ResourceId.parse("host:planned-blocks"),BlockState.class);

    /** Where the carved voids are kept; the host owns them, so no module registers this layer. */
    private static final MantleKey<CarvedVoid> CAVERNS = new MantleKey<>(ResourceId.parse("host:caverns"),CarvedVoid.class);

    /** Where the platform biomes stages set are kept; the host owns them, so no module registers this layer. */
    private static final MantleKey<ResourceId> PLATFORM_BIOMES = new MantleKey<>(ResourceId.parse("host:platform-biomes"),ResourceId.class);

    private final Map<ResourceId, TreeMap<BlockPosition, Object>> layers = new HashMap<>();

    /** A view that accepts reads and writes inside {@code bounds} only. */
    public Mantle within(BlockBox bounds) {
        return new Mantle() {
            public BlockBox bounds() { return bounds; }
            public <T> MantleLayer<T> layer(MantleKey<T> key) { return new Layer<>(key, bounds, true); }
            public MantleLayer<BlockState> blocks() { return new Layer<>(PLANNED_BLOCKS, bounds, false); }
            public MantleLayer<CarvedVoid> carved() { return new Layer<>(CAVERNS, bounds, true); }
            public void setPlatformBiome(int x, int y, int z, ResourceId biome) {
                if (!bounds.contains(x, y, z)) throw new IndexOutOfBoundsException("Outside the mantle view: " + x + "," + y + "," + z);
                values(PLATFORM_BIOMES).put(new BlockPosition(x, y, z), biome);
            }
            public MantleEdit edit() { throw new UnsupportedOperationException("Edits are not used in these tests"); }
        };
    }

    /** Puts a planned block into the mantle, as a placement would; stages only read them. */
    public void plan(int x, int y, int z, BlockState block) {
        values(PLANNED_BLOCKS).put(new BlockPosition(x, y, z), block);
    }

    /** Carves an open void into the mantle, as the cave planner would. */
    public void carve(int x, int y, int z) {
        values(CAVERNS).put(new BlockPosition(x, y, z), CarvedVoid.OPEN);
    }

    /** The platform biome a stage set at the block; null where none did. */
    public ResourceId platformBiome(int x, int y, int z) {
        return get(PLATFORM_BIOMES, x, y, z);
    }

    public <T> T get(MantleKey<T> key, int x, int y, int z) {
        return key.type().cast(values(key).get(new BlockPosition(x, y, z)));
    }

    private TreeMap<BlockPosition, Object> values(MantleKey<?> key) {
        return layers.computeIfAbsent(key.id(), ignored -> new TreeMap<>(VISIT_ORDER));
    }

    private final class Layer<T> implements MantleLayer<T> {
        private final MantleKey<T> key;
        private final BlockBox bounds;
        private final boolean writable;

        Layer(MantleKey<T> key, BlockBox bounds, boolean writable) {
            this.key = key;
            this.bounds = bounds;
            this.writable = writable;
        }

        public T get(int x, int y, int z) {
            requireInside(x, y, z);
            return MemoryMantle.this.get(key, x, y, z);
        }

        public void set(int x, int y, int z, T value) {
            requireWritable();
            requireInside(x, y, z);
            values(key).put(new BlockPosition(x, y, z), key.type().cast(value));
        }

        public void remove(int x, int y, int z) {
            requireWritable();
            requireInside(x, y, z);
            values(key).remove(new BlockPosition(x, y, z));
        }

        public void visit(BlockBox area, MantleVisitor<T> visitor) {
            if (!bounds.contains(area)) throw new IndexOutOfBoundsException("Outside the mantle view: " + area);
            values(key).forEach((position, value) -> {
                if (area.contains(position.x(), position.y(), position.z())) {
                    visitor.accept(position.x(), position.y(), position.z(), key.type().cast(value));
                }
            });
        }

        public void clear(BlockBox area) {
            requireWritable();
            if (!bounds.contains(area)) throw new IndexOutOfBoundsException("Outside the mantle view: " + area);
            values(key).keySet().removeIf(position -> area.contains(position.x(), position.y(), position.z()));
        }

        private void requireWritable() {
            if (!writable) throw new UnsupportedOperationException("Planned blocks are read-only to stages");
        }

        private void requireInside(int x, int y, int z) {
            if (!bounds.contains(x, y, z)) throw new IndexOutOfBoundsException("Outside the mantle view: " + x + "," + y + "," + z);
        }
    }
}
