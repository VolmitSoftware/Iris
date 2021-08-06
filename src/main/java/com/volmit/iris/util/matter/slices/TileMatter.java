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

package com.volmit.iris.util.matter.slices;

import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.engine.parallax.ParallaxAccess;
import com.volmit.iris.engine.parallax.ParallaxWorld;
import com.volmit.iris.util.matter.MatterTile;
import com.volmit.iris.util.matter.Sliced;
import com.volmit.iris.util.nbt.io.NBTUtil;
import com.volmit.iris.util.nbt.tag.CompoundTag;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@Sliced
public class TileMatter extends RawMatter<MatterTile> {
    public TileMatter() {
        this(1, 1, 1);
    }

    public TileMatter(int width, int height, int depth) {
        super(width, height, depth, MatterTile.class);
        registerWriter(World.class, ((w, d, x, y, z) -> INMS.get().deserializeTile(d.getTileData(), new Location(w, x, y, z))));
        registerReader(World.class, (w, x, y, z) -> {
            Location l = new Location(w, x, y, z);
            if(INMS.get().hasTile(l))
            {
                CompoundTag tag = INMS.get().serializeTile(l);

                if(tag != null)
                {
                    return new MatterTile(tag);
                }
            }

            return null;
        });
    }

    @Override
    public void writeNode(MatterTile b, DataOutputStream dos) throws IOException {
        NBTUtil.write(b.getTileData(), dos, false);
    }

    @Override
    public MatterTile readNode(DataInputStream din) throws IOException {
        return new MatterTile((CompoundTag) NBTUtil.read(din, false).getTag());
    }
}
