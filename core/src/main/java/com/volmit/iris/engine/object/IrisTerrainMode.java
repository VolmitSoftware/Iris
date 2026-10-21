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

@Desc("Where the shape of the terrain comes from")
public enum IrisTerrainMode {
    @Desc("Biomes are placed by noise without looking at height, and each biome shapes its own terrain with its generators. The overlay noise is added on top.")
    BIOME,

    @Desc("The overlay noise shapes the base terrain. Land, sea and biomes are chosen by the height of that base terrain (see each biome's placement), then each biome adds its generators on top.")
    HYBRID
}
