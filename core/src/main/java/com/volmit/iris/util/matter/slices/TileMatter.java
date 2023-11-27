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

package com.volmit.iris.util.matter.slices;

import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.engine.object.TileData;
import com.volmit.iris.util.data.palette.Palette;
import com.volmit.iris.util.matter.Sliced;
import com.volmit.iris.util.matter.TileWrapper;
import com.volmit.iris.util.nbt.tag.CompoundTag;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@SuppressWarnings("rawtypes")
@Sliced
public class TileMatter extends RawMatter<TileWrapper> {

    public TileMatter() {
        this(1, 1, 1);
    }

    public TileMatter(int width, int height, int depth) {
        super(width, height, depth, TileWrapper.class);
        registerWriter(World.class, (w, d, x, y, z) -> {
            CompoundTag tag = commonNbt(x, y, z, d.getData().getTileId());
            INMS.get().deserializeTile(d.getData().toNBT(d.getData().toNBT(tag)), new Location(w, x, y, z));
            Iris.warn("S: " + tag);
        });
        registerReader(World.class, (w, x, y, z) -> {
            TileData d = TileData.getTileState(w.getBlockAt(new Location(w, x, y, z)));
            if (d == null)
                return null;
            return new TileWrapper(d);
        });
    }

    @Override
    public Palette<TileWrapper> getGlobalPalette() {
        return null;
    }

    public void writeNode(TileWrapper b, DataOutputStream dos) throws IOException {
        b.getData().toBinary(dos);
    }

    public TileWrapper readNode(DataInputStream din) throws IOException {
        return new TileWrapper(TileData.read(din));
    }

    private CompoundTag commonNbt(int x, int y, int z, String mobId) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        tag.putBoolean("keepPacked", false);
        tag.putString("id", mobId);
        return tag;
    }
}
