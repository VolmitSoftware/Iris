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

package art.arcane.iris.generation.decoration.ruin;

import art.arcane.volmlib.util.documentation.Description;

@Description("The overall silhouette of a procedural ruin. Each form rasterizes a different crumbling man-made shape from the same size, weathering, and erosion settings.")
public enum IrisRuinForm {
    @Description("A broken vertical column. A square trunk (widthMin..widthMax footprint) rises to a randomized height then snaps off with a jagged crown, leaving a toppled stub. Use for ancient pillars and obelisk stumps.")
    PILLAR,
    @Description("A standing wall segment. A flat slab spanning lengthMin..lengthMax long and heightMin..heightMax tall, thickness widthMin..widthMax, punched through with noise-driven gaps and a ragged top edge. Use for collapsed building sides and fortifications.")
    WALL,
    @Description("A freestanding arch. Two legs spaced by lengthMin..lengthMax joined at the top by a rasterized semicircular span (the lintel), so the structure literally arches. Use for gateways, aqueduct bays, and ruined doorways.")
    ARCH,
    @Description("A flat foundation patch. A single (or few) block-thick slab covering a widthMin..lengthMax footprint at ground level, heavily eroded into an irregular outline. Use for floor remnants, plazas, and building foundations.")
    FLOOR_SLAB,
    @Description("A low scattered debris pile. A noise blob of partial blocks mounded near the ground, tallest at the center and thinning toward the edges. Use for collapsed-structure rubble and scree.")
    RUBBLE
}
