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

package art.arcane.iris.generation.decoration.tree;

import art.arcane.volmlib.util.documentation.Description;

@Description("How leaves fill a disc or cluster.")
public enum IrisTreeLeafMode {
    @Description("Corner-trimmed disc/sphere for a rounded silhouette.")
    TRIMMED,
    @Description("Completely filled disc/sphere.")
    FILLED,
    @Description("Seeded probabilistic falloff toward the edge controlled by leafDensity.")
    DENSITY,
    @Description("Seeded value-noise mask controlled by leafDensity.")
    NOISE,
    @Description("Only the outer shell is placed, leaving the interior hollow.")
    HOLLOW,
    @Description("Quadratic radial gradient: dense at the center, thinning sharply toward the edge.")
    GRADIENT,
    @Description("Coarse low-frequency noise that forms clumps and gaps of foliage.")
    CLUMPED,
    @Description("Trimmed with heavy random erosion of the outer ring for a ragged, weathered look.")
    TATTERED,
    @Description("Very thin scattering of leaves across the volume.")
    SPARSE
}
