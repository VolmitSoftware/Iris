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

package com.volmit.iris.util.matter;

import com.volmit.iris.Iris;
import com.volmit.iris.util.data.Cuboid;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;

public class WorldMatter {
    public static void placeMatter(Matter matter, Location at) {
        if (matter.hasSlice(BlockData.class)) {
            matter.slice(BlockData.class).writeInto(at);
        }

        if (matter.hasSlice(MatterEntityGroup.class)) {
            matter.slice(MatterEntityGroup.class).writeInto(at);
        }

        if (matter.hasSlice(TileWrapper.class)) {
            matter.slice(TileWrapper.class).writeInto(at);
        }
    }

    public static Matter createMatter(String author, Location a, Location b) {
        Cuboid c = new Cuboid(a, b);
        Matter s = new IrisMatter(c.getSizeX(), c.getSizeY(), c.getSizeZ());
        Iris.info(s.getWidth() + " " + s.getHeight() + " " + s.getDepth());
        s.getHeader().setAuthor(author);
        s.slice(BlockData.class).readFrom(c.getLowerNE());
        s.slice(MatterEntityGroup.class).readFrom(c.getLowerNE());
        s.slice(TileWrapper.class).readFrom(c.getLowerNE());
        s.trimSlices();

        return s;
    }
}
