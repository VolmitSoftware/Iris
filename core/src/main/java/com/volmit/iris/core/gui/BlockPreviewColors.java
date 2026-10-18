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

package com.volmit.iris.core.gui;

import org.bukkit.block.data.BlockData;

import java.awt.Color;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class BlockPreviewColors {
    private static final Method MAP_COLOR_METHOD = findMapColorMethod();
    private static final double[] MAP_SHADES = {1D, 0.86D, 0.71D, 0.53D};
    private static final int NO_MAP_COLOR = 0;

    private BlockPreviewColors() {
    }

    static Map<String, Integer> colorsByBlock(List<BlockData> blocks) {
        Map<String, Integer> colors = new HashMap<>();
        List<Integer> usedColors = new ArrayList<>();
        for (BlockData block : blocks) {
            String key = block.getAsString(true);
            if (colors.containsKey(key)) {
                continue;
            }
            int baseColor = baseColor(block);
            int color = firstUnusedShade(baseColor, usedColors);
            usedColors.add(color);
            colors.put(key, color);
        }
        return colors;
    }

    // Blocks that share a map colour, like sand and sandstone, get darker shades the way Minecraft maps shade them.
    private static int firstUnusedShade(int baseColor, List<Integer> usedColors) {
        for (double shade : MAP_SHADES) {
            int shaded = shade(baseColor, shade);
            if (!usedColors.contains(shaded)) {
                return shaded;
            }
        }
        return baseColor;
    }

    private static int baseColor(BlockData block) {
        int mapColor = mapColor(block);
        return mapColor == NO_MAP_COLOR ? colorFromName(block.getMaterial().getKey().getKey()) : mapColor;
    }

    private static int mapColor(BlockData block) {
        if (MAP_COLOR_METHOD == null) {
            return NO_MAP_COLOR;
        }
        try {
            return ((org.bukkit.Color) MAP_COLOR_METHOD.invoke(block)).asRGB();
        } catch (ReflectiveOperationException | RuntimeException error) {
            return NO_MAP_COLOR;
        }
    }

    private static int colorFromName(String name) {
        float hue = (name.hashCode() & 0xFFFF) / (float) 0xFFFF;
        return Color.HSBtoRGB(hue, 0.45F, 0.75F) & 0xFFFFFF;
    }

    static int shade(int color, double shade) {
        int red = (int) Math.min(255, ((color >>> 16) & 0xFF) * shade);
        int green = (int) Math.min(255, ((color >>> 8) & 0xFF) * shade);
        int blue = (int) Math.min(255, (color & 0xFF) * shade);
        return (red << 16) | (green << 8) | blue;
    }

    // Iris compiles against the 1.20.1 API, which predates BlockData#getMapColor.
    private static Method findMapColorMethod() {
        try {
            return BlockData.class.getMethod("getMapColor");
        } catch (NoSuchMethodException error) {
            return null;
        }
    }
}
