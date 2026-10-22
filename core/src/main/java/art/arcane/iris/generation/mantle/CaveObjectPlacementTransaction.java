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
import art.arcane.iris.generation.subterrain.SubterrainCell;
import art.arcane.iris.generation.subterrain.SubterrainRasterizer;
import art.arcane.iris.generation.cache.Cache;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.structure.object.IObjectPlacer;
import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.math.BlockPosition;
import art.arcane.iris.generation.block.B;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

final class CaveObjectPlacementTransaction implements IObjectPlacer {
    private static final int CACHE_MISS = Integer.MIN_VALUE;
    private static final int POSITION_BITS = 21;
    private static final int POSITION_LIMIT = 1 << (POSITION_BITS - 1);
    private static final long POSITION_MASK = (1L << POSITION_BITS) - 1L;

    private final IObjectPlacer delegate;
    private final Engine engine;
    private final int anchorY;
    private final int minDepthBelowSurface;
    private final int worldHeight;
    private static final byte BLOCK = 0;
    private static final byte TILE = 1;
    private static final byte DATA = 2;
    private int[] mutationX = new int[64];
    private int[] mutationY = new int[64];
    private int[] mutationZ = new int[64];
    private Object[] mutationValues = new Object[64];
    private byte[] mutationKinds = new byte[64];
    private boolean[] mutationCanopies = new boolean[64];
    private int mutationCount;
    private final Long2ObjectOpenHashMap<NativeBlockState> bufferedBlocks;
    private final Long2IntOpenHashMap surfaceHeights;
    private final Long2IntOpenHashMap caveCeilings;
    private int blockWrites;
    private BlockPosition canopyPosition;
    private boolean originKnown;
    private int originX;
    private int originZ;

    CaveObjectPlacementTransaction(IObjectPlacer delegate, int anchorY, int minDepthBelowSurface) {
        this.delegate = delegate;
        this.engine = delegate.getEngine();
        this.anchorY = anchorY;
        this.minDepthBelowSurface = Math.max(0, minDepthBelowSurface);
        this.worldHeight = engine == null ? 0 : engine.getHeight();
        this.bufferedBlocks = new Long2ObjectOpenHashMap<>();
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
        boolean previousChecked = false;
        int previousX = 0;
        int previousY = 0;
        int previousZ = 0;
        for (int index = 0; index < mutationCount; index++) {
            int x = mutationX[index];
            int y = mutationY[index];
            int z = mutationZ[index];
            if (previousChecked && x == previousX && y == previousY && z == previousZ) {
                continue;
            }
            if (complex != null
                    && !complex.allowsNewDiscreteContentAt(x, z)) {
                discard();
                return CommitResult.REJECTED_TRANSITION;
            }
            if (!isWithinBounds(x, y, z)) {
                discard();
                return CommitResult.REJECTED_BOUNDS;
            }
            SubterrainCell feature = engine.getSubterrainCell(x, y, z);
            if (SubterrainRasterizer.protectsPlacement(feature)
                    || feature != null && feature.solid() && delegate.isSolid(x, y, z)) {
                discard();
                return CommitResult.REJECTED_SUBTERRAIN;
            }
            HydrologyCaveCell hydrology = delegate.getData(x, y, z, HydrologyCaveCell.class);
            if (hydrology != null && hydrology.protectsPlacement()) {
                discard();
                return CommitResult.REJECTED_HYDROLOGY;
            }
            previousChecked = true;
            previousX = x;
            previousY = y;
            previousZ = z;
        }

        applyMutations();
        discard();
        return CommitResult.COMMITTED;
    }

    private void applyMutations() {
        boolean canopyOpen = false;
        boolean skipCanopy = false;
        try {
            for (int index = 0; index < mutationCount; index++) {
                int x = mutationX[index];
                int y = mutationY[index];
                int z = mutationZ[index];
                Object value = mutationValues[index];
                boolean canopy = mutationCanopies[index];
                if (value instanceof NativeBlockState) {
                    if (canopyOpen) {
                        delegate.endTreeCanopy();
                        canopyOpen = false;
                    }
                    skipCanopy = canopy && !delegate.beginTreeCanopy(new BlockPosition(x, y, z));
                    canopyOpen = canopy && !skipCanopy;
                }
                if (!canopy || !skipCanopy) {
                    switch (mutationKinds[index]) {
                        case BLOCK -> delegate.set(x, y, z, (NativeBlockState) value);
                        case TILE -> delegate.setTile(x, y, z, (TileData) value);
                        default -> delegate.setData(x, y, z, value);
                    }
                }
            }
        } finally {
            if (canopyOpen) {
                delegate.endTreeCanopy();
            }
        }
    }

    void discard() {
        Arrays.fill(mutationValues, 0, mutationCount, null);
        mutationCount = 0;
        bufferedBlocks.clear();
        blockWrites = 0;
    }

    private void buffer(byte kind, int x, int y, int z, Object value) {
        if (mutationCount == mutationValues.length) {
            int capacity = mutationCount * 2;
            mutationX = Arrays.copyOf(mutationX, capacity);
            mutationY = Arrays.copyOf(mutationY, capacity);
            mutationZ = Arrays.copyOf(mutationZ, capacity);
            mutationValues = Arrays.copyOf(mutationValues, capacity);
            mutationKinds = Arrays.copyOf(mutationKinds, capacity);
            mutationCanopies = Arrays.copyOf(mutationCanopies, capacity);
        }
        mutationX[mutationCount] = x;
        mutationY[mutationCount] = y;
        mutationZ[mutationCount] = z;
        mutationValues[mutationCount] = value;
        mutationKinds[mutationCount] = kind;
        mutationCanopies[mutationCount] = isCanopy(x, y, z);
        mutationCount++;
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
    public void set(int x, int y, int z, NativeBlockState state) {
        if (state == null) {
            return;
        }
        buffer(BLOCK, x, y, z, state);
        bufferedBlocks.put(bufferKey(x, y, z), state);
        blockWrites++;
    }

    @Override
    public boolean beginTreeCanopy(BlockPosition position) {
        if (!IObjectPlacer.super.beginTreeCanopy(position)) {
            return false;
        }
        canopyPosition = position;
        return true;
    }

    @Override
    public void endTreeCanopy() {
        canopyPosition = null;
    }

    private boolean isCanopy(int x, int y, int z) {
        return canopyPosition != null && canopyPosition.getX() == x
                && canopyPosition.getY() == y && canopyPosition.getZ() == z;
    }

    @Override
    public NativeBlockState get(int x, int y, int z) {
        NativeBlockState buffered = bufferedBlocks.isEmpty() ? null : bufferedBlocks.get(bufferKey(x, y, z));
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
        NativeBlockState buffered = bufferedBlocks.isEmpty() ? null : bufferedBlocks.get(bufferKey(x, y, z));
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
    public int getFluidHeight(int x, int z) {
        SubterrainCell feature = engine == null ? null : engine.getSubterrainCell(x, anchorY, z);
        if (feature != null && feature.fluid()) {
            return feature.room().fluidHeadY() + 1 - engine.getMinHeight();
        }
        return delegate.getFluidHeight(x, z);
    }

    @Override
    public boolean isDebugSmartBore() {
        return delegate.isDebugSmartBore();
    }

    @Override
    public void setTile(int x, int y, int z, TileData tile) {
        if (tile != null) {
            buffer(TILE, x, y, z, tile);
        }
    }

    @Override
    public <T> void setData(int x, int y, int z, T data) {
        if (data == null) {
            return;
        }
        buffer(DATA, x, y, z, data);
        if (data instanceof NativeBlockState state) {
            bufferedBlocks.put(bufferKey(x, y, z), state);
            blockWrites++;
        }
    }

    @Override
    public <T> @Nullable T getData(int x, int y, int z, Class<T> type) {
        for (int index = mutationCount - 1; index >= 0; index--) {
            if (mutationX[index] != x || mutationY[index] != y || mutationZ[index] != z) {
                continue;
            }
            Object value = mutationValues[index];
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

    private long bufferKey(int x, int y, int z) {
        if (!originKnown) {
            originX = x;
            originZ = z;
            originKnown = true;
        }
        int dx = x - originX;
        int dz = z - originZ;
        if (dx < -POSITION_LIMIT || dx >= POSITION_LIMIT || dz < -POSITION_LIMIT || dz >= POSITION_LIMIT
                || y < -POSITION_LIMIT || y >= POSITION_LIMIT) {
            throw new IllegalStateException("Cave object write at " + x + "," + y + "," + z
                    + " is too far from its first write at " + originX + "," + originZ);
        }
        return ((dx & POSITION_MASK) << (POSITION_BITS * 2)) | ((dz & POSITION_MASK) << POSITION_BITS) | (y & POSITION_MASK);
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
        REJECTED_HYDROLOGY,
        REJECTED_SUBTERRAIN
    }
}
