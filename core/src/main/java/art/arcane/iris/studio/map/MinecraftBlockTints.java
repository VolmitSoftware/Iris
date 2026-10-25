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


package art.arcane.iris.studio.map;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Mirrors Minecraft's BlockColors: which colour each tinted face of a block is multiplied with in the world.
final class MinecraftBlockTints {
    static final int NO_TINT = 0xFFFFFF;
    private static final Set<String> GRASS_TINTED = Set.of(
            "grass_block", "short_grass", "fern", "potted_fern", "bush", "tall_grass", "large_fern", "sugar_cane");
    private static final Set<String> GRASS_TINTED_ON_SECOND_LAYER = Set.of("pink_petals", "wildflowers");
    private static final Set<String> FOLIAGE_TINTED = Set.of(
            "oak_leaves", "jungle_leaves", "acacia_leaves", "dark_oak_leaves", "vine", "mangrove_leaves");
    private static final Set<String> WATER_TINTED_BY_FLUID_RENDERER = Set.of("water", "bubble_column");
    private static final Set<String> GROWING_STEMS = Set.of("melon_stem", "pumpkin_stem");
    private static final Map<String, Integer> FIXED_TINTS = Map.of(
            "spruce_leaves", 0x619961,
            "birch_leaves", 0x80A755,
            "attached_melon_stem", 0xE0C71C,
            "attached_pumpkin_stem", 0xE0C71C,
            "lily_pad", 0x208030
    );

    private MinecraftBlockTints() {
    }

    static Optional<BiomeTint> biomeTint(String blockName, int tintIndex) {
        if (WATER_TINTED_BY_FLUID_RENDERER.contains(blockName)) {
            return Optional.of(BiomeTint.WATER);
        }
        if (GRASS_TINTED_ON_SECOND_LAYER.contains(blockName)) {
            return tintIndex == 1 ? Optional.of(BiomeTint.GRASS) : Optional.empty();
        }
        if (tintIndex != 0) {
            return Optional.empty();
        }
        if (GRASS_TINTED.contains(blockName)) {
            return Optional.of(BiomeTint.GRASS);
        }
        if (FOLIAGE_TINTED.contains(blockName)) {
            return Optional.of(BiomeTint.FOLIAGE);
        }
        if (blockName.equals("leaf_litter")) {
            return Optional.of(BiomeTint.DRY_FOLIAGE);
        }
        if (blockName.equals("water_cauldron")) {
            return Optional.of(BiomeTint.WATER);
        }
        return Optional.empty();
    }

    static int fixedTint(String blockName, Map<String, String> properties, int tintIndex) {
        if (tintIndex != 0) {
            return NO_TINT;
        }
        if (GROWING_STEMS.contains(blockName)) {
            return stemTint(Integer.parseInt(properties.getOrDefault("age", "0")));
        }
        return FIXED_TINTS.getOrDefault(blockName, NO_TINT);
    }

    private static int stemTint(int age) {
        return (age * 32) << 16 | (255 - age * 8) << 8 | (age * 4);
    }
}
