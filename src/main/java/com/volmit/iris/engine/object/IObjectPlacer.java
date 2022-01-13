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

package com.volmit.iris.engine.object;

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.framework.Engine;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;

public interface IObjectPlacer {
    int getHighest(int x, int z, IrisData data);

    int getHighest(int x, int z, IrisData data, boolean ignoreFluid);

    void set(int x, int y, int z, BlockData d);

    BlockData get(int x, int y, int z);

    boolean isPreventingDecay();

    boolean isCarved(int x, int y, int z);

    boolean isSolid(int x, int y, int z);

    boolean isUnderwater(int x, int z);

    int getFluidHeight();

    boolean isDebugSmartBore();

    void setTile(int xx, int yy, int zz, TileData<? extends TileState> tile);

    Engine getEngine();
}
