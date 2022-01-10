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
        add(Biome.PLAINS, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.DESERT, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.WINDSWEPT_HILLS, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.FOREST, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.TAIGA, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.SWAMP, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.RIVER, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.NETHER_WASTES, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.THE_END, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.FROZEN_OCEAN, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.FROZEN_RIVER, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.SNOWY_PLAINS, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.MUSHROOM_FIELDS, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.BEACH, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.JUNGLE, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.SPARSE_JUNGLE, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.DEEP_OCEAN, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.STONY_SHORE, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.SNOWY_BEACH, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.BIRCH_FOREST, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.DARK_FOREST, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.SNOWY_TAIGA, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.OLD_GROWTH_PINE_TAIGA, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.WINDSWEPT_FOREST, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.SAVANNA, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.SAVANNA_PLATEAU, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.BADLANDS, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.WOODED_BADLANDS, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.SMALL_END_ISLANDS, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.END_MIDLANDS, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.END_HIGHLANDS, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.END_BARRENS, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.WARM_OCEAN, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.LUKEWARM_OCEAN, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.COLD_OCEAN, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.DEEP_LUKEWARM_OCEAN, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.DEEP_COLD_OCEAN, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.DEEP_FROZEN_OCEAN, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.THE_VOID, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.SUNFLOWER_PLAINS, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.WINDSWEPT_GRAVELLY_HILLS, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.FLOWER_FOREST, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.ICE_SPIKES, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.OLD_GROWTH_BIRCH_FOREST, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.OLD_GROWTH_SPRUCE_TAIGA, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.WINDSWEPT_SAVANNA, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.ERODED_BADLANDS, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.BAMBOO_JUNGLE, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.SOUL_SAND_VALLEY, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.CRIMSON_FOREST, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.WARPED_FOREST, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.BASALT_DELTAS, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.DRIPSTONE_CAVES, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.LUSH_CAVES, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.MEADOW, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.GROVE, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.SNOWY_SLOPES, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.FROZEN_PEAKS, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.JAGGED_PEAKS, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.STONY_PEAKS, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
        add(Biome.CUSTOM, 0x000070, (short) 0, Color.BLUE, Luminosity.BRIGHT, SaturationType.MEDIUM);
    }

    private static void add(Biome biome, int color, short id, Color randomColor, Luminosity luminosity, SaturationType saturation) {
        BIOME_HEX.put(biome, color);
        BIOME_COLOR.put(biome, randomColor);
        if(luminosity != null) BIOME_LUMINOSITY.put(biome, luminosity);
        if(saturation != null) BIOME_SATURATION.put(biome, saturation);
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
