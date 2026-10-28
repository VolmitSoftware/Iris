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

package art.arcane.iris.engine.object.placement;

import art.arcane.iris.engine.object.prefab.TileData;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.spi.PlatformBlockState;
import org.jetbrains.annotations.Nullable;

public interface IObjectPlacer {
    int getHighest(int x, int z, IrisData data);

    int getHighest(int x, int z, IrisData data, boolean ignoreFluid);

    void set(int x, int y, int z, PlatformBlockState d);

    PlatformBlockState get(int x, int y, int z);

    boolean isPreventingDecay();

    boolean isCarved(int x, int y, int z);

    /**
     * Whether the block at this position is real, solid ground. Only live-world placers can answer this;
     * at mantle time terrain has not been written yet, so the height stream is the authority.
     */
    default boolean isSurfaceSolid(int x, int y, int z) {
        return true;
    }

    boolean isSolid(int x, int y, int z);

    boolean isUnderwater(int x, int z);

    int getFluidHeight();

    void setTile(int xx, int yy, int zz, TileData tile);

    <T> void setData(int xx, int yy, int zz, T data);

    <T> @Nullable T getData(int xx, int yy, int zz, Class<T> t);

    Engine getEngine();
}
