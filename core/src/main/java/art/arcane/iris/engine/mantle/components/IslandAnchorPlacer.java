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

package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.builtin.floating.IslandAnchor;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.mantle.MantleWriter;
import art.arcane.iris.engine.object.placement.IObjectPlacer;
import art.arcane.iris.engine.object.prefab.TileData;
import art.arcane.iris.spi.PlatformBlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Iris's placer for an object placed against a floating island: heights and the island's solid blocks come from the
 * {@link IslandAnchor} the floating module samples, and a block is written only where the anchor lets the object write.
 * Everything else is the mantle's.
 */
public final class IslandAnchorPlacer implements IObjectPlacer {
    private final MantleWriter wrapped;
    private final IslandAnchor anchor;

    public IslandAnchorPlacer(MantleWriter wrapped, IslandAnchor anchor) {
        this.wrapped = wrapped;
        this.anchor = anchor;
    }

    public boolean canWriteObjectBlock(int x, int y, int z) {
        return anchor.writes(x, y, z);
    }

    @Override
    public int getHighest(int x, int z, IrisData data) {
        return anchor.faceY(x, z);
    }

    @Override
    public int getHighest(int x, int z, IrisData data, boolean ignoreFluid) {
        return getHighest(x, z, data);
    }

    @Override
    public boolean isUnderwater(int x, int z) {
        return false;
    }

    @Override
    public boolean isSolid(int x, int y, int z) {
        return anchor.hasIsland(x, z) ? anchor.islandSolid(x, y, z) : wrapped.isSolid(x, y, z);
    }

    @Override
    public boolean isCarved(int x, int y, int z) {
        return wrapped.isCarved(x, y, z);
    }

    @Override
    public boolean isSurfaceSolid(int x, int y, int z) {
        return wrapped.isSurfaceSolid(x, y, z);
    }

    @Override
    public void set(int x, int y, int z, PlatformBlockState state) {
        if (anchor.writes(x, y, z)) {
            wrapped.set(x, y, z, state);
        }
    }

    @Override
    public PlatformBlockState get(int x, int y, int z) {
        return wrapped.get(x, y, z);
    }

    @Override
    public boolean isPreventingDecay() {
        return wrapped.isPreventingDecay();
    }

    @Override
    public int getFluidHeight() {
        return wrapped.getFluidHeight();
    }

    @Override
    public boolean isDebugSmartBore() {
        return wrapped.isDebugSmartBore();
    }

    @Override
    public void setTile(int x, int y, int z, TileData tile) {
        if (anchor.writes(x, y, z)) {
            wrapped.setTile(x, y, z, tile);
        }
    }

    @Override
    public <T> void setData(int x, int y, int z, T data) {
        if (anchor.writes(x, y, z)) {
            wrapped.setData(x, y, z, data);
        }
    }

    @Override
    public <T> @Nullable T getData(int x, int y, int z, Class<T> type) {
        return wrapped.getData(x, y, z, type);
    }

    @Override
    public Engine getEngine() {
        return wrapped == null ? null : wrapped.getEngine();
    }
}
