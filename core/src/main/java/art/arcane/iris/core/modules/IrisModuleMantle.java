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

package art.arcane.iris.core.modules;

import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterSlice;
import com.volmit.host.MantleRegistration;
import com.volmit.mantle.BlockBox;
import com.volmit.mantle.Mantle;
import com.volmit.mantle.MantleCodec;
import com.volmit.mantle.MantleEdit;
import com.volmit.mantle.MantleKey;
import com.volmit.mantle.MantleLayer;
import com.volmit.mantle.MantleVisitor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Iris's mantle as IrisApi's {@link Mantle}, in world coordinates, over the chunks one invocation may touch.
 * Every module layer a block holds lives in one {@link ModuleLayerValues} slice, so a layer needs no slice type
 * of its own and a layer no loaded module registers survives unchanged. Only registered layers can be used.
 * Changing a block's values is atomic per mantle chunk, so stages running on neighboring chunks at once never
 * lose each other's layers.
 */
final class IrisModuleMantle implements Mantle {
    private static final int CHUNK_SHIFT = 4;
    private static final int SECTION_SHIFT = 4;
    private static final int LOCAL_MASK = 15;
    private static final Comparator<Stored<?>> VISIT_ORDER = Comparator.<Stored<?>>comparingInt(Stored::x)
            .thenComparingInt(Stored::y).thenComparingInt(Stored::z);

    /** The mantle chunk at chunk coordinates, or null when it is outside what the invocation prepared. */
    interface Chunks {
        MantleChunk<Matter> chunk(int chunkX, int chunkZ);
    }

    private final BlockBox bounds;
    private final int minY;
    private final Chunks chunks;
    private final Map<String, MantleRegistration<?>> registrations;

    /** @param minY the world height of the mantle's lowest block */
    IrisModuleMantle(BlockBox bounds, int minY, Chunks chunks, Collection<MantleRegistration<?>> registrations) {
        this.bounds = bounds;
        this.minY = minY;
        this.chunks = chunks;
        this.registrations = registrations.stream().collect(Collectors.toMap(registration -> registration.key().id(), Function.identity()));
    }

    @Override
    public BlockBox bounds() {
        return bounds;
    }

    @Override
    @SuppressWarnings("unchecked") // The registration's type was compared with the key's.
    public <T> MantleLayer<T> layer(MantleKey<T> key) {
        Objects.requireNonNull(key, "key");
        MantleRegistration<?> registration = registrations.get(key.id());
        if (registration == null) {
            throw new IllegalArgumentException("Mantle layer " + key.id() + " is not registered by any module of the pack");
        }
        if (registration.key().type() != key.type()) {
            throw new IllegalArgumentException("Mantle layer " + key.id() + " is registered as " + registration.key().type().getName()
                    + ", not " + key.type().getName());
        }
        return new Layer<>(key, (MantleCodec<T>) registration.codec());
    }

    @Override
    public MantleEdit edit() {
        throw new UnsupportedOperationException("Buffered mantle edits are not bridged to Iris yet; write through layer(...)");
    }

    private record Stored<T>(int x, int y, int z, T value) {}

    private final class Layer<T> implements MantleLayer<T> {
        private final MantleKey<T> key;
        private final MantleCodec<T> codec;

        Layer(MantleKey<T> key, MantleCodec<T> codec) {
            this.key = key;
            this.codec = codec;
        }

        @Override
        public T get(int x, int y, int z) {
            ModuleLayerValues values = valuesAt(chunkAt(x, y, z), x, y - minY, z);
            return values == null ? null : values.get(key, codec);
        }

        @Override
        public void set(int x, int y, int z, T value) {
            Objects.requireNonNull(value, "value");
            if (!key.type().isInstance(value)) {
                throw new IllegalArgumentException("Mantle layer " + key.id() + " holds " + key.type().getName() + ", not "
                        + value.getClass().getName());
            }
            change(x, y, z, values -> values.with(key, codec, value));
        }

        @Override
        public void remove(int x, int y, int z) {
            change(x, y, z, values -> values.without(key.id()));
        }

        @Override
        public void visit(BlockBox area, MantleVisitor<T> visitor) {
            Objects.requireNonNull(visitor, "visitor");
            for (Stored<T> stored : storedIn(area)) {
                visitor.accept(stored.x(), stored.y(), stored.z(), stored.value());
            }
        }

        @Override
        public void clear(BlockBox area) {
            for (Stored<T> stored : storedIn(area)) {
                remove(stored.x(), stored.y(), stored.z());
            }
        }

        private void change(int x, int y, int z, Function<ModuleLayerValues, ModuleLayerValues> change) {
            MantleChunk<Matter> chunk = chunkAt(x, y, z);
            int mantleY = y - minY;
            synchronized (chunk) {
                ModuleLayerValues current = valuesAt(chunk, x, mantleY, z);
                ModuleLayerValues changed = change.apply(current == null ? ModuleLayerValues.EMPTY : current);
                MatterSlice<ModuleLayerValues> slice = chunk.getOrCreate(mantleY >> SECTION_SHIFT).slice(ModuleLayerValues.class);
                slice.set(x & LOCAL_MASK, mantleY & LOCAL_MASK, z & LOCAL_MASK, changed.isEmpty() ? null : changed);
            }
        }

        /** This layer's values in {@code area}, ordered by x, then y, then z, as {@link MantleLayer#visit} promises. */
        private List<Stored<T>> storedIn(BlockBox area) {
            requireInside(area);
            List<Stored<T>> stored = new ArrayList<>();
            for (int chunkX = area.minX() >> CHUNK_SHIFT; chunkX <= (area.maxX() - 1) >> CHUNK_SHIFT; chunkX++) {
                for (int chunkZ = area.minZ() >> CHUNK_SHIFT; chunkZ <= (area.maxZ() - 1) >> CHUNK_SHIFT; chunkZ++) {
                    collect(chunkX, chunkZ, area, stored);
                }
            }
            stored.sort(VISIT_ORDER);
            return stored;
        }

        private void collect(int chunkX, int chunkZ, BlockBox area, List<Stored<T>> stored) {
            MantleChunk<Matter> chunk = requireChunk(chunkX, chunkZ);
            int originX = chunkX << CHUNK_SHIFT;
            int originZ = chunkZ << CHUNK_SHIFT;
            chunk.iterate(ModuleLayerValues.class, (localX, mantleY, localZ, values) -> {
                int x = originX + localX;
                int y = mantleY + minY;
                int z = originZ + localZ;
                if (values.has(key.id()) && area.contains(x, y, z)) {
                    stored.add(new Stored<>(x, y, z, values.get(key, codec)));
                }
            });
        }
    }

    private MantleChunk<Matter> chunkAt(int x, int y, int z) {
        if (!bounds.contains(x, y, z)) {
            throw new IndexOutOfBoundsException("Block " + x + ", " + y + ", " + z + " is outside the mantle this invocation prepared, " + bounds);
        }
        return requireChunk(x >> CHUNK_SHIFT, z >> CHUNK_SHIFT);
    }

    private MantleChunk<Matter> requireChunk(int chunkX, int chunkZ) {
        MantleChunk<Matter> chunk = chunks.chunk(chunkX, chunkZ);
        if (chunk == null) {
            throw new IllegalStateException("Mantle chunk " + chunkX + ", " + chunkZ + " is inside " + bounds + " but was not prepared");
        }
        return chunk;
    }

    private void requireInside(BlockBox area) {
        Objects.requireNonNull(area, "area");
        if (!bounds.contains(area)) {
            throw new IndexOutOfBoundsException("Area " + area + " is not inside the mantle this invocation prepared, " + bounds);
        }
    }

    private static ModuleLayerValues valuesAt(MantleChunk<Matter> chunk, int x, int mantleY, int z) {
        int section = mantleY >> SECTION_SHIFT;
        if (!chunk.exists(section)) {
            return null;
        }
        Matter matter = chunk.get(section);
        if (matter == null || !matter.hasSlice(ModuleLayerValues.class)) {
            return null;
        }
        return matter.<ModuleLayerValues>getSlice(ModuleLayerValues.class).get(x & LOCAL_MASK, mantleY & LOCAL_MASK, z & LOCAL_MASK);
    }
}
