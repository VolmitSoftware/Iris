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
import art.arcane.iris.generation.cache.Cache;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.structure.object.IObjectPlacer;
import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.block.B;
import art.arcane.volmlib.util.collection.KList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

final class CaveObjectPlacementTransaction implements IObjectPlacer {
    private static final int CACHE_MISS = Integer.MIN_VALUE;

    private final IObjectPlacer delegate;
    private final Engine engine;
    private final int anchorY;
    private final int minDepthBelowSurface;
    private final int worldHeight;
    private final KList<BufferedMutation> mutations;
    private final Map<PositionKey, PlatformBlockState> bufferedBlocks;
    private final Long2IntOpenHashMap surfaceHeights;
    private final Long2IntOpenHashMap caveCeilings;
    private int blockWrites;

    CaveObjectPlacementTransaction(IObjectPlacer delegate, int anchorY, int minDepthBelowSurface) {
        this.delegate = delegate;
        this.engine = delegate.getEngine();
        this.anchorY = anchorY;
        this.minDepthBelowSurface = Math.max(0, minDepthBelowSurface);
        this.worldHeight = engine == null ? 0 : engine.getHeight();
        this.mutations = new KList<>();
        this.bufferedBlocks = new HashMap<>();
        this.surfaceHeights = new Long2IntOpenHashMap();
        this.caveCeilings = new Long2IntOpenHashMap();
        this.surfaceHeights.defaultReturnValue(CACHE_MISS);
        this.caveCeilings.defaultReturnValue(CACHE_MISS);
    }

    CommitResult commit() {
        if (blockWrites == 0) {
            discard();
            return CommitResult.EMPTY;
        }

        IrisComplex complex = engine == null ? null : engine.getComplex();
        for (BufferedMutation mutation : mutations) {
            if (complex != null
                    && !complex.allowsNewDiscreteContentAt(mutation.x(), mutation.z())) {
                discard();
                return CommitResult.REJECTED_TRANSITION;
            }
            if (!isWithinBounds(mutation.x(), mutation.y(), mutation.z())) {
                discard();
                return CommitResult.REJECTED_BOUNDS;
            }
            HydrologyCaveCell hydrology = delegate.getData(
                    mutation.x(), mutation.y(), mutation.z(), HydrologyCaveCell.class);
            if (hydrology != null && hydrology.protectsPlacement()) {
                discard();
                return CommitResult.REJECTED_HYDROLOGY;
            }
        }

        for (BufferedMutation mutation : mutations) {
            mutation.apply(delegate);
        }
        discard();
        return CommitResult.COMMITTED;
    }

    void discard() {
        mutations.clear();
        bufferedBlocks.clear();
        blockWrites = 0;
    }

    int getCaveCeiling(int x, int z) {
        long key = Cache.key(x, z);
        int cached = caveCeilings.get(key);
        if (cached != CACHE_MISS) {
            return cached;
        }

        int ceiling = findCaveCeiling(x, z);
        caveCeilings.put(key, ceiling);
        return ceiling;
    }

    static int maxBuriedY(int worldHeight, int surfaceY, int minDepthBelowSurface) {
        return Math.min(worldHeight - 1, surfaceY - Math.max(0, minDepthBelowSurface));
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
    public void set(int x, int y, int z, PlatformBlockState state) {
        if (state == null) {
            return;
        }
        mutations.add(new BlockMutation(x, y, z, state));
        bufferedBlocks.put(new PositionKey(x, y, z), state);
        blockWrites++;
    }

    @Override
    public PlatformBlockState get(int x, int y, int z) {
        PlatformBlockState buffered = bufferedBlocks.get(new PositionKey(x, y, z));
        return buffered == null ? delegate.get(x, y, z) : buffered;
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
        PlatformBlockState buffered = bufferedBlocks.get(new PositionKey(x, y, z));
        return buffered == null ? delegate.isSolid(x, y, z) : B.isSolid(buffered);
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
        if (data instanceof PlatformBlockState state) {
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
        return engine;
    }

    private boolean isWithinBounds(int x, int y, int z) {
        if (engine == null || y < 0 || y >= worldHeight) {
            return false;
        }
        int surfaceY = getSurfaceHeight(x, z);
        if (y > maxBuriedY(worldHeight, surfaceY, minDepthBelowSurface)) {
            return false;
        }
        return y < getCaveCeiling(x, z);
    }

    private int getSurfaceHeight(int x, int z) {
        long key = Cache.key(x, z);
        int cached = surfaceHeights.get(key);
        if (cached != CACHE_MISS) {
            return cached;
        }
        int surfaceY = Engine.hostHeight(engine, x, z, true);
        surfaceHeights.put(key, surfaceY);
        return surfaceY;
    }

    private int findCaveCeiling(int x, int z) {
        if (worldHeight <= 0 || anchorY < 0 || anchorY >= worldHeight) {
            return 0;
        }
        if (!delegate.isCarved(x, anchorY, z)) {
            return Math.min(worldHeight, anchorY + 1);
        }

        int scanLimit = Math.min(worldHeight - 1, Math.max(anchorY, getSurfaceHeight(x, z)));
        for (int y = anchorY + 1; y <= scanLimit; y++) {
            if (!delegate.isCarved(x, y, z)) {
                return y;
            }
        }
        return Math.min(worldHeight, scanLimit + 1);
    }

    enum CommitResult {
        COMMITTED,
        EMPTY,
        REJECTED_TRANSITION,
        REJECTED_BOUNDS,
        REJECTED_HYDROLOGY
    }

    private interface BufferedMutation {
        int x();

        int y();

        int z();

        Object value();

        void apply(IObjectPlacer placer);
    }

    private record BlockMutation(int x, int y, int z, PlatformBlockState value) implements BufferedMutation {
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
