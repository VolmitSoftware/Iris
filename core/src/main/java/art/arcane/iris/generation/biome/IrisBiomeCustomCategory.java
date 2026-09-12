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

@Description("The custom biome category. Vanilla asks for this, basically what represents your biome closest?")
public enum IrisBiomeCustomCategory {
    @Description("Tags the generated datapack biome as vanilla category 'beach' (shoreline biomes).")
    beach,

    @Description("Tags the generated datapack biome as vanilla category 'desert' (hot, dry sand biomes).")
    desert,

    @Description("Tags the generated datapack biome as vanilla category 'extreme_hills' (mountain biomes).")
    extreme_hills,

    @Description("Tags the generated datapack biome as vanilla category 'forest' (tree-dense temperate biomes).")
    forest,

    @Description("Tags the generated datapack biome as vanilla category 'icy' (snow and ice biomes).")
    icy,

    @Description("Tags the generated datapack biome as vanilla category 'jungle'.")
    jungle,

    @Description("Tags the generated datapack biome as vanilla category 'mesa' (badlands).")
    mesa,

    @Description("Tags the generated datapack biome as vanilla category 'mushroom' (mushroom fields).")
    mushroom,

    @Description("Tags the generated datapack biome as vanilla category 'nether'.")
    nether,

    @Description("Tags the generated datapack biome as vanilla category 'none' (no classification).")
    none,

    @Description("Tags the generated datapack biome as vanilla category 'ocean'.")
    ocean,

    @Description("Tags the generated datapack biome as vanilla category 'plains'. This is the default when category is omitted.")
    plains,

    @Description("Tags the generated datapack biome as vanilla category 'river'.")
    river,

    @Description("Tags the generated datapack biome as vanilla category 'savanna'.")
    savanna,

    @Description("Tags the generated datapack biome as vanilla category 'swamp'.")
    swamp,

    @Description("Tags the generated datapack biome as vanilla category 'taiga'.")
    taiga,

    @Description("Tags the generated datapack biome as vanilla category 'the_end'.")
    the_end
}
