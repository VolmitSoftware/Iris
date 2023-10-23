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

package com.volmit.iris.engine.framework.placer;

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IObjectPlacer;
import com.volmit.iris.engine.object.IrisObjectPlacement;
import com.volmit.iris.engine.object.TileData;
import com.volmit.iris.util.math.RNG;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;

public class HeightmapObjectPlacer implements IObjectPlacer {
    private final long s;
    private final IrisObjectPlacement config;
    private final IObjectPlacer oplacer;

    public HeightmapObjectPlacer(Engine engine, RNG rng, int x, int yv, int z, IrisObjectPlacement config, IObjectPlacer oplacer) {
        s = rng.nextLong() + yv + z - x;
        this.config = config;
        this.oplacer = oplacer;
    }

    public int getHighest(int param1Int1, int param1Int2, IrisData data) {
        return (int) Math.round(config.getHeightmap().getNoise(this.s, param1Int1, param1Int2, data));
    }

    public int getHighest(int param1Int1, int param1Int2, IrisData data, boolean param1Boolean) {
        return (int) Math.round(config.getHeightmap().getNoise(this.s, param1Int1, param1Int2, data));
    }

    public void set(int param1Int1, int param1Int2, int param1Int3, BlockData param1BlockData) {
        oplacer.set(param1Int1, param1Int2, param1Int3, param1BlockData);
    }

    public BlockData get(int param1Int1, int param1Int2, int param1Int3) {
        return oplacer.get(param1Int1, param1Int2, param1Int3);
    }

    public boolean isPreventingDecay() {
        return oplacer.isPreventingDecay();
    }

    @Override
    public boolean isCarved(int x, int y, int z) {
        return false;
    }

    public boolean isSolid(int param1Int1, int param1Int2, int param1Int3) {
        return oplacer.isSolid(param1Int1, param1Int2, param1Int3);
    }

    public boolean isUnderwater(int param1Int1, int param1Int2) {
        return oplacer.isUnderwater(param1Int1, param1Int2);
    }

    public int getFluidHeight() {
        return oplacer.getFluidHeight();
    }

    public boolean isDebugSmartBore() {
        return oplacer.isDebugSmartBore();
    }

    public void setTile(int param1Int1, int param1Int2, int param1Int3, TileData<? extends TileState> param1TileData) {
        oplacer.setTile(param1Int1, param1Int2, param1Int3, param1TileData);
    }

    @Override
    public Engine getEngine() {
        return null;
    }
}
