/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.probe;

import java.util.Set;

/**
 * The stub's idea of plants, enough for generation to exercise what Iris does with them: grasses, ferns, flowers,
 * saplings and mushrooms are foliage and decorations that stand only on soil, and vines hang. Minecraft knows many
 * more; the probe only needs the common ones a pack decorates with.
 */
final class StubPlants {
    private static final Set<String> PLANTS = Set.of(
            "minecraft:short_grass", "minecraft:tall_grass", "minecraft:fern", "minecraft:large_fern",
            "minecraft:dandelion", "minecraft:poppy", "minecraft:blue_orchid", "minecraft:allium",
            "minecraft:azure_bluet", "minecraft:oxeye_daisy", "minecraft:cornflower", "minecraft:lily_of_the_valley",
            "minecraft:sunflower", "minecraft:lilac", "minecraft:rose_bush", "minecraft:peony", "minecraft:dead_bush",
            "minecraft:sweet_berry_bush", "minecraft:brown_mushroom", "minecraft:red_mushroom");
    private static final Set<String> PLANT_SUFFIXES = Set.of("_tulip", "_sapling");
    private static final Set<String> SOILS = Set.of(
            "minecraft:grass_block", "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:podzol",
            "minecraft:rooted_dirt", "minecraft:moss_block", "minecraft:mud", "minecraft:mycelium",
            "minecraft:sand", "minecraft:red_sand", "minecraft:farmland");
    private static final Set<String> VINES = Set.of(
            "minecraft:vine", "minecraft:glow_lichen", "minecraft:cave_vines", "minecraft:cave_vines_plant",
            "minecraft:weeping_vines", "minecraft:twisting_vines");

    private StubPlants() {
    }

    static boolean isPlant(String blockKey) {
        return PLANTS.contains(blockKey) || PLANT_SUFFIXES.stream().anyMatch(blockKey::endsWith);
    }

    static boolean holdsPlants(String blockKey) {
        return SOILS.contains(blockKey);
    }

    static boolean isVine(String blockKey) {
        return VINES.contains(blockKey);
    }
}
