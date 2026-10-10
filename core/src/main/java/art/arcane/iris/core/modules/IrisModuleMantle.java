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
import com.volmit.block.BlockState;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Iris's mantle as IrisApi's {@link Mantle}, in world X and Z and heights above the dimension's bottom (which is
 * how the mantle stores them), over the chunks one invocation may touch.
 * Every module layer a block holds lives in one {@link ModuleLayerValues} slice, so a layer needs no slice type
 * of its own and a layer no loaded module registers survives unchanged. Only registered layers can be used.
 * Changing a block's values is atomic per mantle chunk, so stages running on neighboring chunks at once never
 * lose each other's layers. {@link #edit()} buffers changes across layers until they are committed; while an
 * edit is open, the view itself only reads.
 */
final class IrisModuleMantle implements Mantle {
    private static final int CHUNK_SHIFT = 4;
    private static final int SECTION_SHIFT = 4;
    private static final int LOCAL_MASK = 15;
    private static final Comparator<Position> POSITION_ORDER = Comparator.comparingInt(Position::x)
            .thenComparingInt(Position::y).thenComparingInt(Position::z);
    private static final Comparator<Stored<?>> VISIT_ORDER = Comparator.<Stored<?>>comparingInt(Stored::x)
            .thenComparingInt(Stored::y).thenComparingInt(Stored::z);

    /** The mantle chunk at chunk coordinates, or null when it is outside what the invocation prepared. */
    interface Chunks {
        MantleChunk<Matter> chunk(int chunkX, int chunkZ);
    }

    private final BlockBox bounds;
    private final Chunks chunks;
    private final Map<String, MantleRegistration<?>> registrations;
    private final PlannedBlocks plannedBlocks;
    private BufferedEdit openEdit;

    IrisModuleMantle(BlockBox bounds, Chunks chunks, Collection<MantleRegistration<?>> registrations, PlannedBlocks plannedBlocks) {
        this.bounds = bounds;
        this.chunks = chunks;
        this.registrations = registrations.stream().collect(Collectors.toMap(registration -> registration.key().id(), Function.identity()));
        this.plannedBlocks = plannedBlocks;
    }

    @Override
    public BlockBox bounds() {
        return bounds;
    }

    @Override
    public <T> MantleLayer<T> layer(MantleKey<T> key) {
        return new Layer<>(key, codecOf(key));
    }

    @Override
    public MantleLayer<BlockState> blocks() {
        return plannedBlocks;
    }

    @Override
    public MantleEdit edit() {
        if (openEdit != null) {
            throw new IllegalStateException("This mantle already has an open edit; commit or close it first");
        }
        openEdit = new BufferedEdit();
        return openEdit;
    }

    @SuppressWarnings("unchecked") // The registration's type was compared with the key's.
    private <T> MantleCodec<T> codecOf(MantleKey<T> key) {
        Objects.requireNonNull(key, "key");
        MantleRegistration<?> registration = registrations.get(key.id());
        if (registration == null) {
            throw new IllegalArgumentException("Mantle layer " + key.id() + " is not registered by any module of the pack");
        }
        if (registration.key().type() != key.type()) {
            throw new IllegalArgumentException("Mantle layer " + key.id() + " is registered as " + registration.key().type().getName()
                    + ", not " + key.type().getName());
        }
        return (MantleCodec<T>) registration.codec();
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
            ModuleLayerValues values = valuesAt(chunkAt(x, y, z), x, y, z);
            return values == null ? null : values.get(key, codec);
        }

        @Override
        public void set(int x, int y, int z, T value) {
            requireNoOpenEdit();
            requireValue(key, value);
            change(x, y, z, values -> values.with(key, codec, value));
        }

        @Override
        public void remove(int x, int y, int z) {
            requireNoOpenEdit();
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
            requireNoOpenEdit();
            for (Stored<T> stored : storedIn(area)) {
                remove(stored.x(), stored.y(), stored.z());
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
            chunk.iterate(ModuleLayerValues.class, (localX, y, localZ, values) -> {
                int x = originX + localX;
                int z = originZ + localZ;
                if (values.has(key.id()) && area.contains(x, y, z)) {
                    stored.add(new Stored<>(x, y, z, values.get(key, codec)));
                }
            });
        }
    }

    /** Where an edit stands: only an open edit buffers and reads; a failed commit leaves it to be closed. */
    private enum EditState { OPEN, COMMITTED, FAILED, CLOSED }

    /** One buffered change of one layer at one block. */
    private sealed interface Change permits Put, Removal {
        ModuleLayerValues applyTo(ModuleLayerValues values);
    }

    private record Put<T>(MantleKey<T> key, MantleCodec<T> codec, T value) implements Change {
        @Override
        public ModuleLayerValues applyTo(ModuleLayerValues values) {
            return values.with(key, codec, value);
        }
    }

    private record Removal(String layerId) implements Change {
        @Override
        public ModuleLayerValues applyTo(ModuleLayerValues values) {
            return values.without(layerId);
        }
    }

    private record Position(int x, int y, int z) {}

    /**
     * Changes across layers, buffered per block in x, y, z order, that reach the mantle only on {@link #commit()}.
     * Reads and visits see them over the unchanged mantle.
     */
    private final class BufferedEdit implements MantleEdit {
        private final TreeMap<Position, Map<String, Change>> changes = new TreeMap<>(POSITION_ORDER);
        private EditState state = EditState.OPEN;

        @Override
        public BlockBox bounds() {
            return bounds;
        }

        @Override
        public <T> MantleLayer<T> layer(MantleKey<T> key) {
            requireOpen();
            return new EditLayer<>(new Layer<>(key, codecOf(key)));
        }

        @Override
        public MantleLayer<BlockState> blocks() {
            requireOpen();
            return plannedBlocks;
        }

        @Override
        public MantleEdit edit() {
            throw new UnsupportedOperationException("Mantle edits cannot be nested; write through this edit");
        }

        /** Publishes every change, or none when a chunk it touches is missing; the edit then only closes. */
        @Override
        public void commit() {
            requireOpen();
            try {
                publish();
            } catch (RuntimeException failure) {
                state = EditState.FAILED;
                throw failure;
            }
            end(EditState.COMMITTED);
        }

        @Override
        public void close() {
            if (state == EditState.OPEN || state == EditState.FAILED) {
                changes.clear();
                end(EditState.CLOSED);
            }
        }

        private void publish() {
            for (Position position : changes.keySet()) {
                chunkAt(position.x(), position.y(), position.z());
            }
            changes.forEach((position, blockChanges) -> change(position.x(), position.y(), position.z(), values -> {
                ModuleLayerValues changed = values;
                for (Change layerChange : blockChanges.values()) {
                    changed = layerChange.applyTo(changed);
                }
                return changed;
            }));
        }

        private void end(EditState ended) {
            state = ended;
            openEdit = null;
        }

        private void requireOpen() {
            if (state != EditState.OPEN) {
                throw new IllegalStateException("This mantle edit is " + state.name().toLowerCase(Locale.ROOT) + "; open a new one with edit()");
            }
        }

        private Change changeAt(int x, int y, int z, String layerId) {
            Map<String, Change> blockChanges = changes.get(new Position(x, y, z));
            return blockChanges == null ? null : blockChanges.get(layerId);
        }

        private void buffer(int x, int y, int z, String layerId, Change change) {
            if (!bounds.contains(x, y, z)) {
                throw new IndexOutOfBoundsException("Block " + x + ", " + y + ", " + z + " is outside the mantle this invocation prepared, " + bounds);
            }
            changes.computeIfAbsent(new Position(x, y, z), ignored -> new LinkedHashMap<>()).put(layerId, change);
        }

        /** A layer seen through the edit: buffered changes over the mantle's stored values. */
        private final class EditLayer<T> implements MantleLayer<T> {
            private final Layer<T> stored;

            EditLayer(Layer<T> stored) {
                this.stored = stored;
            }

            @Override
            public T get(int x, int y, int z) {
                requireOpen();
                Change change = changeAt(x, y, z, stored.key.id());
                if (change == null) {
                    return stored.get(x, y, z);
                }
                return change instanceof Put<?> put ? stored.key.type().cast(put.value()) : null;
            }

            @Override
            public void set(int x, int y, int z, T value) {
                requireOpen();
                requireValue(stored.key, value);
                buffer(x, y, z, stored.key.id(), new Put<>(stored.key, stored.codec, value));
            }

            @Override
            public void remove(int x, int y, int z) {
                requireOpen();
                buffer(x, y, z, stored.key.id(), new Removal(stored.key.id()));
            }

            @Override
            public void visit(BlockBox area, MantleVisitor<T> visitor) {
                Objects.requireNonNull(visitor, "visitor");
                visible(area).forEach((position, value) -> visitor.accept(position.x(), position.y(), position.z(), value));
            }

            @Override
            public void clear(BlockBox area) {
                for (Position position : visible(area).keySet()) {
                    remove(position.x(), position.y(), position.z());
                }
            }

            /** This layer's values in {@code area} as the edit sees them, in x, y, z order. */
            private TreeMap<Position, T> visible(BlockBox area) {
                requireOpen();
                TreeMap<Position, T> visible = new TreeMap<>(POSITION_ORDER);
                for (Stored<T> value : stored.storedIn(area)) {
                    visible.put(new Position(value.x(), value.y(), value.z()), value.value());
                }
                changes.forEach((position, blockChanges) -> {
                    Change change = blockChanges.get(stored.key.id());
                    if (change == null || !area.contains(position.x(), position.y(), position.z())) {
                        return;
                    }
                    if (change instanceof Put<?> put) {
                        visible.put(position, stored.key.type().cast(put.value()));
                    } else {
                        visible.remove(position);
                    }
                });
                return visible;
            }
        }
    }

    /** Replaces the values of one block under the chunk's lock, so concurrent stages on neighboring chunks keep each other's layers. */
    private void change(int x, int y, int z, UnaryOperator<ModuleLayerValues> change) {
        MantleChunk<Matter> chunk = chunkAt(x, y, z);
        synchronized (chunk) {
            ModuleLayerValues current = valuesAt(chunk, x, y, z);
            ModuleLayerValues changed = change.apply(current == null ? ModuleLayerValues.EMPTY : current);
            MatterSlice<ModuleLayerValues> slice = chunk.getOrCreate(y >> SECTION_SHIFT).slice(ModuleLayerValues.class);
            slice.set(x & LOCAL_MASK, y & LOCAL_MASK, z & LOCAL_MASK, changed.isEmpty() ? null : changed);
        }
    }

    private void requireNoOpenEdit() {
        if (openEdit != null) {
            throw new IllegalStateException("This mantle has an open edit; write through the edit until it is committed or closed");
        }
    }

    private static <T> void requireValue(MantleKey<T> key, T value) {
        Objects.requireNonNull(value, "value");
        if (!key.type().isInstance(value)) {
            throw new IllegalArgumentException("Mantle layer " + key.id() + " holds " + key.type().getName() + ", not "
                    + value.getClass().getName());
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
