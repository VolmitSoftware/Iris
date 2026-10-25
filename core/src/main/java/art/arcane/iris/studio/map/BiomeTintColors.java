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

import java.util.Locale;

// The colours one Minecraft biome multiplies onto tinted blocks, as worked out by Biome#getGrassColor and friends.
record BiomeTintColors(int grass, GrassColorModifier grassModifier, int foliage, int dryFoliage, int water) {
    int colorAt(BiomeTint tint, int blockX, int blockZ) {
        return switch (tint) {
            case GRASS -> grassModifier.modify(blockX, blockZ, grass);
            case FOLIAGE -> foliage;
            case DRY_FOLIAGE -> dryFoliage;
            case WATER -> water;
        };
    }

    boolean variesByPosition() {
        return grassModifier == GrassColorModifier.SWAMP;
    }

    enum GrassColorModifier {
        NONE,
        DARK_FOREST,
        SWAMP;

        private static final double SWAMP_NOISE_SCALE = 0.0225D;
        private static final double SWAMP_WET_THRESHOLD = -0.1D;
        private static final int SWAMP_WET_GRASS = 0x4C763C;
        private static final int SWAMP_DRY_GRASS = 0x6A7039;
        private static final int DARK_FOREST_SHIFT = 0x28340A;

        static GrassColorModifier named(String name) {
            return switch (name.toLowerCase(Locale.ROOT)) {
                case "dark_forest" -> DARK_FOREST;
                case "swamp" -> SWAMP;
                default -> NONE;
            };
        }

        int modify(int blockX, int blockZ, int baseColor) {
            return switch (this) {
                case NONE -> baseColor;
                case DARK_FOREST -> ((baseColor & 0xFEFEFE) + DARK_FOREST_SHIFT) >> 1;
                case SWAMP -> BiomeInfoNoise.at(blockX * SWAMP_NOISE_SCALE, blockZ * SWAMP_NOISE_SCALE) < SWAMP_WET_THRESHOLD
                        ? SWAMP_WET_GRASS
                        : SWAMP_DRY_GRASS;
            };
        }
    }
}
