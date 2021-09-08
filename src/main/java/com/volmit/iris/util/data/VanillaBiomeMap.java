/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.util.data;

import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.inventorygui.RandomColor.Color;
import com.volmit.iris.util.inventorygui.RandomColor.Luminosity;
import com.volmit.iris.util.inventorygui.RandomColor.SaturationType;
import org.bukkit.block.Biome;

public class VanillaBiomeMap {

    private static final KMap<Biome, Integer> BIOME_HEX = new KMap<>();
    private static final KMap<Biome, Color> BIOME_COLOR = new KMap<>();
    private static final KMap<Biome, Luminosity> BIOME_LUMINOSITY = new KMap<>();
    private static final KMap<Biome, SaturationType> BIOME_SATURATION = new KMap<>();
    private static final KMap<Biome, Short> BIOME_IDs = new KMap<>();

    static {
        add(Biome.OCEAN, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.PLAINS, 0x8DB360, (short) 1, Color.GREEN, Luminosity.LIGHT, SaturationType.MEDIUM);
        add(Biome.DESERT, 0xFA9418, (short) 2, Color.YELLOW, Luminosity.LIGHT, SaturationType.MEDIUM);
        add(Biome.MOUNTAINS, 0x606060, (short) 3, Color.MONOCHROME, Luminosity.BRIGHT, null);
        add(Biome.FOREST, 0x056621, (short) 4, Color.GREEN, Luminosity.BRIGHT);
        add(Biome.TAIGA, 0x0B6659, (short) 5, Color.GREEN, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.SWAMP, 0x07F9B2, (short) 6, Color.ORANGE, Luminosity.DARK, SaturationType.MEDIUM);
        add(Biome.RIVER, 0x0000FF, (short) 7, Color.BLUE, Luminosity.LIGHT, SaturationType.LOW);
        add(Biome.NETHER_WASTES, 0xBF3B3B, (short) 8, Color.RED, Luminosity.LIGHT, SaturationType.MEDIUM);
        add(Biome.THE_END, 0x8080FF, (short) 9, Color.PURPLE, Luminosity.LIGHT, SaturationType.LOW);
        add(Biome.FROZEN_OCEAN, 0x7070D6, (short) 10, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.FROZEN_RIVER, 0xA0A0FF, (short) 11, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.SNOWY_TUNDRA, 0xFFFFFF, (short) 12, Color.MONOCHROME, Luminosity.LIGHT);
        add(Biome.SNOWY_MOUNTAINS, 0xA0A0A0, (short) 13, Color.MONOCHROME, Luminosity.LIGHT);
        add(Biome.MUSHROOM_FIELDS, 0xFF00FF, (short) 14, Color.PURPLE, Luminosity.BRIGHT);
        add(Biome.MUSHROOM_FIELD_SHORE, 0xA000FF, (short) 15, Color.PURPLE, Luminosity.BRIGHT);
        add(Biome.BEACH, 0xFADE55, (short) 16, Color.YELLOW, Luminosity.LIGHT, SaturationType.LOW);
        add(Biome.DESERT_HILLS, 0xD25F12, (short) 17, Color.YELLOW, Luminosity.LIGHT, SaturationType.MEDIUM);
        add(Biome.WOODED_HILLS, 0x22551C, (short) 18, Color.GREEN, Luminosity.LIGHT);
        add(Biome.TAIGA_HILLS, 0x163933, (short) 19, Color.GREEN, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.MOUNTAIN_EDGE, 0x72789A, (short) 20, Color.MONOCHROME, Luminosity.BRIGHT);
        add(Biome.JUNGLE, 0x537B09, (short) 21, Color.GREEN, Luminosity.BRIGHT, SaturationType.HIGH);
        add(Biome.JUNGLE_HILLS, 0x2C4205, (short) 22, Color.GREEN, Luminosity.DARK, SaturationType.HIGH);
        add(Biome.JUNGLE_EDGE, 0x628B17, (short) 23, Color.GREEN, Luminosity.BRIGHT, SaturationType.HIGH);
        add(Biome.DEEP_OCEAN, 0x000030, (short) 24, Color.BLUE, Luminosity.DARK);
        add(Biome.STONE_SHORE, 0xA2A284, (short) 25, Color.GREEN, Luminosity.DARK);
        add(Biome.SNOWY_BEACH, 0xFAF0C0, (short) 26, Color.YELLOW, Luminosity.LIGHT);
        add(Biome.BIRCH_FOREST, 0x307444, (short) 27, Color.GREEN, Luminosity.LIGHT);
        add(Biome.BIRCH_FOREST_HILLS, 0x1F5F32, (short) 28, Color.GREEN, Luminosity.LIGHT);
        add(Biome.DARK_FOREST, 0x40511A, (short) 29, Color.GREEN, Luminosity.DARK);
        add(Biome.SNOWY_TAIGA, 0x31554A, (short) 30, Color.BLUE, Luminosity.LIGHT);
        add(Biome.SNOWY_TAIGA_HILLS, 0x243F36, (short) 31, Color.BLUE, Luminosity.LIGHT);
        add(Biome.GIANT_TREE_TAIGA, 0x596651, (short) 32, Color.ORANGE, Luminosity.LIGHT);
        add(Biome.GIANT_TREE_TAIGA_HILLS, 0x454F3E, (short) 33, Color.ORANGE, Luminosity.LIGHT);
        add(Biome.WOODED_MOUNTAINS, 0x507050, (short) 34, Color.MONOCHROME, Luminosity.BRIGHT);
        add(Biome.SAVANNA, 0xBDB25F, (short) 35, Color.GREEN, Luminosity.LIGHT);
        add(Biome.SAVANNA_PLATEAU, 0xA79D64, (short) 36, Color.GREEN, Luminosity.LIGHT);
        add(Biome.BADLANDS, 0xD94515, (short) 37, Color.ORANGE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.WOODED_BADLANDS_PLATEAU, 0xB09765, (short) 38, Color.ORANGE, Luminosity.BRIGHT, SaturationType.HIGH);
        add(Biome.BADLANDS_PLATEAU, 0xCA8C65, (short) 39, Color.ORANGE, Luminosity.BRIGHT, SaturationType.HIGH);
        add(Biome.END_MIDLANDS, 0x8080FF, (short) 41, Color.YELLOW, Luminosity.LIGHT, SaturationType.LOW);
        add(Biome.END_HIGHLANDS, 0x8080FF, (short) 42, Color.PURPLE, Luminosity.LIGHT, SaturationType.LOW);
        add(Biome.END_BARRENS, 0x8080FF, (short) 43, Color.PURPLE, Luminosity.LIGHT, SaturationType.MEDIUM);
        add(Biome.WARM_OCEAN, 0x0000AC, (short) 44, Color.BLUE, Luminosity.BRIGHT, SaturationType.LOW);
        add(Biome.LUKEWARM_OCEAN, 0x000090, (short) 45, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.COLD_OCEAN, 0x202070, (short) 46, Color.BLUE, Luminosity.BRIGHT, SaturationType.HIGH);
        add(Biome.DEEP_WARM_OCEAN, 0x000050, (short) 47, Color.BLUE, Luminosity.DARK, SaturationType.LOW);
        add(Biome.DEEP_LUKEWARM_OCEAN, 0x000040, (short) 48, Color.BLUE, Luminosity.DARK, SaturationType.MEDIUM);
        add(Biome.DEEP_COLD_OCEAN, 0x202038, (short) 49, Color.BLUE, Luminosity.DARK, SaturationType.HIGH);
        add(Biome.DEEP_FROZEN_OCEAN, 0x404090, (short) 50, Color.BLUE, Luminosity.LIGHT, SaturationType.LOW);
        add(Biome.THE_VOID, 0x000000, (short) 127, Color.MONOCHROME, Luminosity.DARK);
        add(Biome.SUNFLOWER_PLAINS, 0xB5DB88, (short) 129, Color.GREEN, Luminosity.LIGHT, SaturationType.LOW);
        add(Biome.DESERT_LAKES, 0xFFBC40, (short) 130, Color.BLUE, Luminosity.LIGHT, SaturationType.LOW);
        add(Biome.GRAVELLY_MOUNTAINS, 0x888888, (short) 131, Color.MONOCHROME, Luminosity.LIGHT);
        add(Biome.FLOWER_FOREST, 0x2D8E49, (short) 132, Color.RED, Luminosity.LIGHT, SaturationType.LOW);
        add(Biome.TAIGA_MOUNTAINS, 0x338E81, (short) 133, Color.GREEN, Luminosity.DARK, SaturationType.MEDIUM);
        add(Biome.SWAMP_HILLS, 0x2FFFDA, (short) 134, Color.ORANGE, Luminosity.DARK, SaturationType.MEDIUM);
        add(Biome.ICE_SPIKES, 0xB4DCDC, (short) 140, Color.BLUE, Luminosity.LIGHT, SaturationType.LOW);
        add(Biome.MODIFIED_JUNGLE, 0x7BA331, (short) 149, Color.GREEN, Luminosity.BRIGHT, SaturationType.HIGH);
        add(Biome.MODIFIED_JUNGLE_EDGE, 0x8AB33F, (short) 151, Color.GREEN, Luminosity.BRIGHT, SaturationType.HIGH);
        add(Biome.TALL_BIRCH_FOREST, 0x589C6C, (short) 155, Color.GREEN, Luminosity.LIGHT);
        add(Biome.TALL_BIRCH_HILLS, 0x47875A, (short) 156, Color.GREEN, Luminosity.LIGHT);
        add(Biome.DARK_FOREST_HILLS, 0x687942, (short) 157, Color.GREEN, Luminosity.DARK);
        add(Biome.SNOWY_TAIGA_MOUNTAINS, 0x597D72, (short) 158, Color.BLUE, Luminosity.LIGHT);
        add(Biome.GIANT_SPRUCE_TAIGA, 0x818E79, (short) 160, Color.ORANGE, Luminosity.DARK, SaturationType.HIGH);
        add(Biome.GIANT_SPRUCE_TAIGA_HILLS, 0x6D7766, (short) 161, Color.ORANGE, Luminosity.DARK, SaturationType.HIGH);
        add(Biome.GRAVELLY_MOUNTAINS, 0x789878, (short) 162, Color.MONOCHROME, Luminosity.LIGHT);
        add(Biome.SHATTERED_SAVANNA, 0xE5DA87, (short) 163, Color.ORANGE, Luminosity.LIGHT, SaturationType.HIGH);
        add(Biome.SHATTERED_SAVANNA_PLATEAU, 0xCFC58C, (short) 164, Color.ORANGE, Luminosity.LIGHT, SaturationType.HIGH);
        add(Biome.ERODED_BADLANDS, 0xFF6D3D, (short) 165, Color.ORANGE, Luminosity.LIGHT, SaturationType.HIGH);
        add(Biome.MODIFIED_WOODED_BADLANDS_PLATEAU, 0xD8BF8D, (short) 166, Color.ORANGE, Luminosity.BRIGHT);
        add(Biome.MODIFIED_BADLANDS_PLATEAU, 0xF2B48D, (short) 167, Color.ORANGE, Luminosity.BRIGHT);
        add(Biome.BAMBOO_JUNGLE, 0x768E14, (short) 168, Color.GREEN, Luminosity.BRIGHT, SaturationType.HIGH);
        add(Biome.BAMBOO_JUNGLE_HILLS, 0x3B470A, (short) 169, Color.GREEN, Luminosity.BRIGHT, SaturationType.HIGH);
        add(Biome.SOUL_SAND_VALLEY, 0x5E3830, (short) 170, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.CRIMSON_FOREST, 0xDD0808, (short) 171, Color.RED, Luminosity.DARK, SaturationType.HIGH);
        add(Biome.WARPED_FOREST, 0x49907B, (short) 172, Color.BLUE, Luminosity.BRIGHT);
        add(Biome.BASALT_DELTAS, 0x403636, (short) 173, Color.MONOCHROME, Luminosity.DARK);
    }

    private static void add(Biome biome, int color, short id, Color randomColor, Luminosity luminosity, SaturationType saturation) {
        BIOME_HEX.put(biome, color);
        BIOME_COLOR.put(biome, randomColor);
        if (luminosity != null) BIOME_LUMINOSITY.put(biome, luminosity);
        if (saturation != null) BIOME_SATURATION.put(biome, saturation);
        BIOME_IDs.put(biome, id);
    }

    private static void add(Biome biome, int color, short id, Color randomColor, Luminosity luminosity) {
        add(biome, color, id, randomColor, luminosity, null);
    }

    public static int getColor(Biome biome) {
        return BIOME_HEX.get(biome);
    }

    public static Color getColorType(Biome biome) {
        return BIOME_COLOR.get(biome);
    }

    public static Luminosity getColorLuminosity(Biome biome) {
        return BIOME_LUMINOSITY.get(biome);
    }

    public static SaturationType getColorSaturatiom(Biome biome) {
        return BIOME_SATURATION.get(biome);
    }

    public static short getId(Biome biome) {
        return BIOME_IDs.get(biome);
    }
}
