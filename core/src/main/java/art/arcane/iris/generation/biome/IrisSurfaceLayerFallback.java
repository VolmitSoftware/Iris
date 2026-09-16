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

@Description("What a column surfaces with when every biome layer is rejected, usually because all of them are slope gated and the column is too steep.")
public enum IrisSurfaceLayerFallback {
    @Description("Leave the column without layers so it surfaces with the dimension rock palette. Use this for biomes meant to be bare rock where they get steep.")
    ROCK,

    @Description("Cap the column with a single block of the first layer's palette, ignoring that layer's slope condition. Keeps steep coastlines and cliff faces from turning into bare stone.")
    TOP_LAYER
}
