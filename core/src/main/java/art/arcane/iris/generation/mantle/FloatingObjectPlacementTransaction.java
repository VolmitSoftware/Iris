/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.generation.mantle;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.structure.object.IObjectPlacer;
import art.arcane.iris.generation.block.TileData;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.generation.block.B;
import art.arcane.volmlib.util.collection.KList;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.function.Supplier;
import java.util.Objects;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.iris.world.storage.matter.IrisMatterSupport;
import java.util.Map;

final class FloatingObjectPlacementTransaction implements IObjectPlacer {
    private final IObjectPlacer delegate;
    private final ObjectContinuationBundle.PlacementKey placementKey;
    private final KList<BufferedMutation> mutations;
    private final Map<PositionKey, NativeBlockState> bufferedBlocks;
    private int blockWrites;

    FloatingObjectPlacementTransaction(IObjectPlacer delegate, ObjectContinuationBundle.PlacementKey placementKey) {
        this.delegate = Objects.requireNonNull(delegate);
        this.placementKey = Objects.requireNonNull(placementKey);
        this.mutations = new KList<>();
        this.bufferedBlocks = new HashMap<>();
    }

    CommitResult commit() {
        if (blockWrites == 0) {
            discard();
            return CommitResult.EMPTY;
        }
        Engine engine = delegate.getEngine();
        IrisComplex complex = engine == null ? null : engine.getComplex();
        for (BufferedMutation mutation : mutations) {
            if (complex != null
                    && !complex.allowsNewDiscreteContentAt(mutation.x(), mutation.z())) {
                discard();
                return CommitResult.REJECTED_TRANSITION;
            }
            if (delegate instanceof IslandObjectPlacer island
                    && !island.canWriteObjectBlock(mutation.x(), mutation.y(), mutation.z())) {
                discard();
                return CommitResult.REJECTED_SUPPORT;
            }
        }
        for (BufferedMutation mutation : mutations) {
            mutation.apply(delegate);
        }
        persistContinuation(engine);
        discard();
        return CommitResult.COMMITTED;
    }

    private void persistContinuation(Engine engine) {
        int minimumX = Integer.MAX_VALUE;
        int minimumZ = Integer.MAX_VALUE;
        int maximumX = Integer.MIN_VALUE;
        int maximumZ = Integer.MIN_VALUE;
        for (BufferedMutation mutation : mutations) {
            minimumX = Math.min(minimumX, mutation.x());
            minimumZ = Math.min(minimumZ, mutation.z());
            maximumX = Math.max(maximumX, mutation.x());
            maximumZ = Math.max(maximumZ, mutation.z());
        }
        if ((minimumX >> 4) == (maximumX >> 4) && (minimumZ >> 4) == (maximumZ >> 4)) {
            return;
        }
        IrisMatterSupport.ensureRegistered();
        LinkedHashMap<ObjectContinuationBundle.ChunkPosition, Matter> payloads = new LinkedHashMap<>();
        for (BufferedMutation mutation : mutations) {
            ObjectContinuationBundle.ChunkPosition chunk = new ObjectContinuationBundle.ChunkPosition(mutation.x() >> 4, mutation.z() >> 4);
            Matter payload = payloads.computeIfAbsent(chunk, ignored -> new IrisMatter(16, engine.getHeight(), 16));
            ObjectContinuationPersistence.put(payload, mutation.x() & 15, mutation.y(), mutation.z() & 15, mutation.value());
        }
        LinkedHashMap<ObjectContinuationBundle.ChunkPosition, Supplier<Matter>> destinations = new LinkedHashMap<>();
        payloads.forEach((chunk, payload) -> destinations.put(chunk, () -> payload));
        ObjectContinuationPersistence.persist(engine.getMantle().getMantle(), placementKey,
                new ObjectContinuationBundle.Bounds(minimumX, minimumZ, maximumX, maximumZ), destinations);
    }

    void discard() {
        mutations.clear();
        bufferedBlocks.clear();
        blockWrites = 0;
    }

    @Override
    public int getHighest(int x, int z, IrisData data) {
        return delegate.getHighest(x, z, data);
    }

    @Override
    public int getHighest(int x, int z, IrisData data, boolean ignoreFluid) {
        return delegate.getHighest(x, z, data, ignoreFluid);
    }

    @Override
    public void set(int x, int y, int z, NativeBlockState state) {
        if (state == null) {
            return;
        }
        mutations.add(new BlockMutation(x, y, z, state));
        bufferedBlocks.put(new PositionKey(x, y, z), state);
        blockWrites++;
    }

    @Override
    public NativeBlockState get(int x, int y, int z) {
        NativeBlockState state = bufferedBlocks.get(new PositionKey(x, y, z));
        return state == null ? delegate.get(x, y, z) : state;
    }

    @Override
    public boolean isPreventingDecay() {
        return delegate.isPreventingDecay();
    }

    @Override
    public boolean isCarved(int x, int y, int z) {
        return delegate.isCarved(x, y, z);
    }

    @Override
    public boolean isSurfaceSolid(int x, int y, int z) {
        return delegate.isSurfaceSolid(x, y, z);
    }

    @Override
    public boolean isSolid(int x, int y, int z) {
        NativeBlockState state = bufferedBlocks.get(new PositionKey(x, y, z));
        return state == null ? delegate.isSolid(x, y, z) : B.isSolid(state);
    }

    @Override
    public boolean isUnderwater(int x, int z) {
        return delegate.isUnderwater(x, z);
    }

    @Override
    public int getFluidHeight() {
        return delegate.getFluidHeight();
    }

    @Override
    public boolean isDebugSmartBore() {
        return delegate.isDebugSmartBore();
    }

    @Override
    public void setTile(int x, int y, int z, TileData tile) {
        if (tile != null) {
            mutations.add(new TileMutation(x, y, z, tile));
        }
    }

    @Override
    public <T> void setData(int x, int y, int z, T data) {
        if (data == null) {
            return;
        }
        mutations.add(new DataMutation(x, y, z, data));
        if (data instanceof NativeBlockState state) {
            bufferedBlocks.put(new PositionKey(x, y, z), state);
            blockWrites++;
        }
    }

    @Override
    public <T> @Nullable T getData(int x, int y, int z, Class<T> type) {
        for (int i = mutations.size() - 1; i >= 0; i--) {
            BufferedMutation mutation = mutations.get(i);
            if (mutation.x() != x || mutation.y() != y || mutation.z() != z) {
                continue;
            }
            Object value = mutation.value();
            if (type.isInstance(value)) {
                return type.cast(value);
            }
        }
        return delegate.getData(x, y, z, type);
    }

    @Override
    public Engine getEngine() {
        return delegate.getEngine();
    }

    enum CommitResult {
        COMMITTED,
        EMPTY,
        REJECTED_TRANSITION,
        REJECTED_SUPPORT
    }

    private interface BufferedMutation {
        int x();

        int y();

        int z();

        Object value();

        void apply(IObjectPlacer placer);
    }

    private record BlockMutation(int x, int y, int z, NativeBlockState value) implements BufferedMutation {
        @Override
        public void apply(IObjectPlacer placer) {
            placer.set(x, y, z, value);
        }
    }

    private record TileMutation(int x, int y, int z, TileData value) implements BufferedMutation {
        @Override
        public void apply(IObjectPlacer placer) {
            placer.setTile(x, y, z, value);
        }
    }

    private record DataMutation(int x, int y, int z, Object value) implements BufferedMutation {
        @Override
        public void apply(IObjectPlacer placer) {
            placer.setData(x, y, z, value);
        }
    }

    private record PositionKey(int x, int y, int z) {
    }
}
