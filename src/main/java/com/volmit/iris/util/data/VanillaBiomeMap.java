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
        add(Biome.OCEAN, 0x000070, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.PLAINS, 0x8DB360, Color.GREEN, Luminosity.LIGHT, SaturationType.MEDIUM);
        add(Biome.DESERT, 0xFA9418, Color.YELLOW, Luminosity.LIGHT, SaturationType.MEDIUM);
        add(Biome.WINDSWEPT_HILLS, 0x606060, Color.MONOCHROME, Luminosity.BRIGHT, null);
        add(Biome.FOREST, 0x056621, Color.GREEN, Luminosity.BRIGHT, null);
        add(Biome.TAIGA, 0x0B6659, Color.GREEN, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.SWAMP, 0x07F9B2, Color.ORANGE, Luminosity.DARK, SaturationType.MEDIUM);
        add(Biome.RIVER, 0x0000FF, Color.BLUE, Luminosity.LIGHT, SaturationType.LOW);
        add(Biome.NETHER_WASTES, 0xBF3B3B, Color.RED, Luminosity.LIGHT, SaturationType.MEDIUM);
        add(Biome.THE_END, 0x8080FF, Color.PURPLE, Luminosity.LIGHT, SaturationType.LOW);
        add(Biome.FROZEN_OCEAN, 0x7070D6, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.FROZEN_RIVER, 0xA0A0FF, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.SNOWY_PLAINS, 0xFFFFFF, Color.MONOCHROME, Luminosity.LIGHT, null);
        add(Biome.MUSHROOM_FIELDS, 0xFF00FF, Color.PURPLE, Luminosity.BRIGHT, null);
        add(Biome.BEACH, 0xFADE55, Color.YELLOW, Luminosity.LIGHT, SaturationType.LOW);
        add(Biome.JUNGLE, 0x537B09, Color.GREEN, Luminosity.BRIGHT, SaturationType.HIGH);
        add(Biome.SPARSE_JUNGLE, 0x628B17, Color.GREEN, Luminosity.BRIGHT, SaturationType.HIGH);
        add(Biome.DEEP_OCEAN, 0x000030, Color.BLUE, Luminosity.DARK, null);
        add(Biome.STONY_SHORE, 0xA2A284, Color.GREEN, Luminosity.DARK, null);
        add(Biome.SNOWY_BEACH, 0xFAF0C0, Color.YELLOW, Luminosity.LIGHT, null);
        add(Biome.BIRCH_FOREST, 0x307444, Color.GREEN, Luminosity.LIGHT, null);
        add(Biome.DARK_FOREST, 0x40511A, Color.GREEN, Luminosity.DARK, null);
        add(Biome.SNOWY_TAIGA, 0x31554A, Color.BLUE, Luminosity.LIGHT, null);
        add(Biome.OLD_GROWTH_PINE_TAIGA, 0x596651, Color.ORANGE, Luminosity.LIGHT, null);
        add(Biome.WINDSWEPT_FOREST, 0x507050, Color.MONOCHROME, Luminosity.BRIGHT, null);
        add(Biome.SAVANNA, 0xBDB25F, Color.GREEN, Luminosity.LIGHT, null);
        add(Biome.SAVANNA_PLATEAU, 0xA79D64, Color.GREEN, Luminosity.LIGHT, null);
        add(Biome.BADLANDS, 0xD94515, Color.ORANGE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.WOODED_BADLANDS, 0xB09765, Color.ORANGE, Luminosity.BRIGHT, SaturationType.HIGH);
        add(Biome.SMALL_END_ISLANDS, 0xff1a8c, Color.PURPLE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.END_MIDLANDS, 0x8080FF, Color.YELLOW, Luminosity.LIGHT, SaturationType.LOW);
        add(Biome.END_HIGHLANDS, 0x8080FF, Color.PURPLE, Luminosity.LIGHT, SaturationType.LOW);
        add(Biome.END_BARRENS, 0x8080FF, Color.PURPLE, Luminosity.LIGHT, SaturationType.MEDIUM);
        add(Biome.WARM_OCEAN, 0x0000AC, Color.BLUE, Luminosity.BRIGHT, SaturationType.LOW);
        add(Biome.LUKEWARM_OCEAN, 0x000090, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.COLD_OCEAN, 0x202070, Color.BLUE, Luminosity.BRIGHT, SaturationType.HIGH);
        add(Biome.DEEP_LUKEWARM_OCEAN, 0x000040, Color.BLUE, Luminosity.DARK, SaturationType.MEDIUM);
        add(Biome.DEEP_COLD_OCEAN, 0x202038, Color.BLUE, Luminosity.DARK, SaturationType.HIGH);
        add(Biome.DEEP_FROZEN_OCEAN, 0x404090, Color.BLUE, Luminosity.LIGHT, SaturationType.LOW);
        add(Biome.THE_VOID, 0x000000, Color.MONOCHROME, Luminosity.DARK, null);
        add(Biome.SUNFLOWER_PLAINS, 0xB5DB88, Color.GREEN, Luminosity.LIGHT, SaturationType.LOW);
        add(Biome.WINDSWEPT_GRAVELLY_HILLS, 0x789878, Color.MONOCHROME, Luminosity.LIGHT, null);
        add(Biome.FLOWER_FOREST, 0x2D8E49, Color.RED, Luminosity.LIGHT, SaturationType.LOW);
        add(Biome.ICE_SPIKES, 0xB4DCDC, Color.BLUE, Luminosity.LIGHT, SaturationType.LOW);
        add(Biome.OLD_GROWTH_BIRCH_FOREST, 0x589C6C, Color.GREEN, Luminosity.LIGHT, null);
        add(Biome.OLD_GROWTH_SPRUCE_TAIGA, 0x818E79, Color.ORANGE, Luminosity.DARK, SaturationType.HIGH);
        add(Biome.WINDSWEPT_SAVANNA, 0xE5DA87, Color.ORANGE, Luminosity.LIGHT, SaturationType.HIGH);
        add(Biome.ERODED_BADLANDS, 0xFF6D3D, Color.ORANGE, Luminosity.LIGHT, SaturationType.HIGH);
        add(Biome.BAMBOO_JUNGLE, 0x768E14, Color.GREEN, Luminosity.BRIGHT, SaturationType.HIGH);
        add(Biome.SOUL_SAND_VALLEY, 0x5E3830, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.CRIMSON_FOREST, 0xDD0808, Color.RED, Luminosity.DARK, SaturationType.HIGH);
        add(Biome.WARPED_FOREST, 0x49907B, Color.BLUE, Luminosity.BRIGHT, null);
        add(Biome.BASALT_DELTAS, 0x403636, Color.MONOCHROME, Luminosity.DARK, null);
        add(Biome.DRIPSTONE_CAVES, 0xcc6600, Color.ORANGE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.LUSH_CAVES, 0x003300, Color.GREEN, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.MEADOW, 0xff00ff, Color.BLUE, Luminosity.BRIGHT, SaturationType.LOW);
        add(Biome.GROVE, 0x80ff80, Color.GREEN, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.SNOWY_SLOPES, 0x00ffff, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.FROZEN_PEAKS, 0xA0A0A0, Color.MONOCHROME, Luminosity.LIGHT, null);
        add(Biome.JAGGED_PEAKS, 0x3d7bc2, Color.MONOCHROME, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.STONY_PEAKS, 0x888888, Color.MONOCHROME, Luminosity.LIGHT, null);
        add(Biome.CUSTOM, 0xffffff, Color.MONOCHROME, Luminosity.DARK, SaturationType.MONOCHROME);

    }


    private static void add(Biome biome, int color, Color randomColor, Luminosity luminosity, SaturationType saturation) {
        BIOME_HEX.put(biome, color);
        BIOME_COLOR.put(biome, randomColor);
        if (luminosity != null) BIOME_LUMINOSITY.put(biome, luminosity);
        if (saturation != null) BIOME_SATURATION.put(biome, saturation);
        BIOME_IDs.put(biome, (short) biome.ordinal());
    }

    private static void add(Biome biome, int color, Color randomColor, Luminosity luminosity) {
        add(biome, color, randomColor, luminosity, null);
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
