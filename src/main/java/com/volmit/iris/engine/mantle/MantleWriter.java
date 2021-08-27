/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.engine.mantle;

import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.data.cache.Cache;
import com.volmit.iris.engine.object.common.IObjectPlacer;
import com.volmit.iris.engine.object.feature.IrisFeaturePositional;
import com.volmit.iris.engine.object.tile.TileData;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.mantle.MantleChunk;
import com.volmit.iris.util.matter.Matter;
import lombok.Data;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;

@Data
public class MantleWriter implements IObjectPlacer {
    private final EngineMantle engineMantle;
    private final Mantle mantle;
    private final KMap<Long, MantleChunk> cachedChunks;
    private final int radius;
    private final int x;
    private final int z;

    public MantleWriter(EngineMantle engineMantle, Mantle mantle, int x, int z, int radius) {
        this.engineMantle = engineMantle;
        this.mantle = mantle;
        this.cachedChunks = new KMap<>();
        this.radius = radius;
        this.x = x;
        this.z = z;

        for (int i = -radius; i <= radius; i++) {
            for (int j = -radius; j <= radius; j++) {
                cachedChunks.put(Cache.key(i + x, j + z), mantle.getChunk(i + x, j + z));
            }
        }
    }

    public <T> void setData(int x, int y, int z, T t) {
        int cx = x >> 4;
        int cz = z >> 4;

        if (y < 0 || y >= mantle.getWorldHeight()) {
            return;
        }

        if (cx >= this.x - radius && cx <= this.x + radius
                && cz >= this.z - radius && cz <= this.z + radius) {
            MantleChunk chunk = cachedChunks.get(Cache.key(cx, cz));

            if (chunk == null) {
                Iris.error("Mantle Writer Accessed " + cx + "," + cz + " and came up null (and yet within bounds!)");
                return;
            }

            if (t instanceof IrisFeaturePositional) {
                chunk.addFeature((IrisFeaturePositional) t);
            } else {
                Matter matter = chunk.getOrCreate(y >> 4);
                matter.slice(matter.getClass(t)).set(x & 15, y & 15, z & 15, t);
            }
        } else {
            Iris.error("Mantle Writer[" + this.x + "," + this.z + ",R" + this.radius + "] Tried to access " + x + "," + y + "," + z + " (Chunk " + cx + "," + cz + ") which is OUT OF BOUNDS!");
        }
    }

    @Override
    public int getHighest(int x, int z, IrisData data) {
        return engineMantle.getHighest(x, z, data);
    }

    @Override
    public int getHighest(int x, int z, IrisData data, boolean ignoreFluid) {
        return engineMantle.getHighest(x, z, data, ignoreFluid);
    }

    @Override
    public void set(int x, int y, int z, BlockData d) {
        setData(x, y, z, d);
    }

    @Override
    public BlockData get(int x, int y, int z) {
        return getEngineMantle().get(x, y, z);
    }

    @Override
    public boolean isPreventingDecay() {
        return getEngineMantle().isPreventingDecay();
    }

    @Override
    public boolean isSolid(int x, int y, int z) {
        return getEngineMantle().isSolid(x, y, z);
    }

    @Override
    public boolean isUnderwater(int x, int z) {
        return getEngineMantle().isUnderwater(x, z);
    }

    @Override
    public int getFluidHeight() {
        return getEngineMantle().getFluidHeight();
    }

    @Override
    public boolean isDebugSmartBore() {
        return getEngineMantle().isDebugSmartBore();
    }

    @Override
    public void setTile(int xx, int yy, int zz, TileData<? extends TileState> tile) {
        getEngineMantle().setTile(xx, yy, zz, tile);
    }
}
