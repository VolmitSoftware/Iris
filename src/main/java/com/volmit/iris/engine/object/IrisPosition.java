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

import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.Snippet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Vector;

@Snippet("position-3d")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents a position")
@Data
public class IrisPosition {
    @Desc("The x position")
    private int x = 0;

    @Desc("The y position")
    private int y = 0;

    @Desc("The z position")
    private int z = 0;

    public IrisPosition(BlockVector bv) {
        this(bv.getBlockX(), bv.getBlockY(), bv.getBlockZ());
    }

    public IrisPosition(Location l) {
        this(l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    public IrisPosition(Vector v) {
        this(v.getBlockX(), v.getBlockY(), v.getBlockZ());
    }

    public IrisPosition(double x, double y, double z) {
        this((int) x, (int) y, (int) z);
    }


    public IrisPosition add(IrisPosition relativePosition) {
        return new IrisPosition(relativePosition.x + x, relativePosition.y + y, relativePosition.z + z);
    }

    public IrisPosition sub(IrisPosition relativePosition) {
        return new IrisPosition(x - relativePosition.x, y - relativePosition.y, z - relativePosition.z);
    }

    public Location toLocation(World world) {
        return new Location(world, x, y, z);
    }

    public IrisPosition copy() {
        return new IrisPosition(x, y, z);
    }

    @Override
    public String toString() {
        return "[" + getX() + "," + getY() + "," + getZ() + "]";
    }

    public boolean isLongerThan(IrisPosition s, int maxLength) {
        return Math.abs(Math.pow(s.x - x, 2) + Math.pow(s.y - y, 2) + Math.pow(s.z - z, 2)) > maxLength * maxLength;
    }

    public Vector toVector() {
        return new Vector(x, y, z);
    }
}
