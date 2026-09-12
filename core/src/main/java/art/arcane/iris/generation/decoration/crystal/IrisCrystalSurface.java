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

package art.arcane.iris.generation.decoration.crystal;

import art.arcane.volmlib.util.documentation.Description;

@Description("The surface a crystal cluster grows from. This only orients the baked geometry (which way the shards point); placement of the object into a cave is handled by the placement system, not here.")
public enum IrisCrystalSurface {
    @Description("Grows from a cave floor. The budding base sits at the bottom and the shards point upward, fanning within the spread cone around the up axis. Built with positive py so the cluster grows up from the base layer.")
    FLOOR,

    @Description("Grows from a cave ceiling. The budding base sits at the top and the shards point downward like stalactites, fanning within the spread cone around the down axis. Built with negative py so the cluster grows down from the ceiling.")
    CEILING,

    @Description("Grows from a cave wall. The budding base hugs the wall and the shards point outward and slightly upward, fanning within the spread cone around a tilted normal so the cluster reads as a wall sconce of shards.")
    WALL
}
