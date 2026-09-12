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

package art.arcane.iris.structure.placement;

import art.arcane.volmlib.util.documentation.Description;

@Description("Controls how structure start positions are scattered across the world.")
public enum StructureDistribution {
    @Description("Vanilla-style spacing/separation grid. One placement attempt per grid cell, randomly offset within it by the separation. Use spacing, separation and salt.")
    RANDOM_SPREAD,

    @Description("A per-chunk probability roll, like object density. Use density (0-1).")
    DENSITY,

    @Description("Concentric rings around the world origin, like vanilla strongholds. Use rings.")
    CONCENTRIC_RINGS
}
