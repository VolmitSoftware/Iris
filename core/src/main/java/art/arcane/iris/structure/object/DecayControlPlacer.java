/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.structure.object;

import art.arcane.iris.generation.block.TileData;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.spi.PlatformBlockState;
import org.jetbrains.annotations.Nullable;

public class DecayControlPlacer implements IObjectPlacer {
    private final IObjectPlacer delegate;

    public DecayControlPlacer(IObjectPlacer delegate) {
        this.delegate = delegate;
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
    public void set(int x, int y, int z, PlatformBlockState d) {
        delegate.set(x, y, z, d);
    }

    @Override
    public PlatformBlockState get(int x, int y, int z) {
        return delegate.get(x, y, z);
    }

    @Override
    public boolean isPreventingDecay() {
        return false;
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
        return delegate.isSolid(x, y, z);
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
    public void setTile(int xx, int yy, int zz, TileData tile) {
        delegate.setTile(xx, yy, zz, tile);
    }

    @Override
    public <T> void setData(int xx, int yy, int zz, T data) {
        delegate.setData(xx, yy, zz, data);
    }

    @Override
    public <T> @Nullable T getData(int xx, int yy, int zz, Class<T> t) {
        return delegate.getData(xx, yy, zz, t);
    }

    @Override
    public Engine getEngine() {
        return delegate.getEngine();
    }
}
