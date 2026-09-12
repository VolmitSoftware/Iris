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

package art.arcane.iris.generation.biome;

import art.arcane.volmlib.util.documentation.Description;

@Description("How the top profile of a floating-child island is shaped.")
public enum TopShapeMode {
    @Description("Evaluate the target biome's own terrain generators to build the island top. Mountains biome produces real peaks, desert produces dunes, plains is flat. Recommended default.")
    BIOME,

    @Description("Drive the top profile from topShapeStyle noise, independent of the target biome's generators. Amplitude controlled by topShapeAmp.")
    NOISE,

    @Description("Flat slab on top, topHeight blocks above the base. Ignores noise and biome generators.")
    FLAT
}
