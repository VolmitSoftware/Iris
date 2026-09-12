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

@Description("How the shards of a crystal cluster are angularly distributed around the surface normal.")
public enum IrisCrystalDistribution {
    @Description("Each shard gets a uniformly random azimuth around the normal. Produces a chaotic, naturally clumped cluster.")
    RANDOM,

    @Description("Each shard's azimuth advances by the golden angle (~137.5 degrees) from the previous one. Produces an evenly spaced sunflower-like fan with no two shards overlapping, ideal for a geode rosette.")
    GOLDEN_ANGLE
}
