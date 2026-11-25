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

package art.arcane.iris.generation.decoration;

import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;

/**
 * Block state helpers shared by object placement, decorators, terrain and the procedural objects: reading a state's
 * properties and material, normalizing waterlogging and spotting blocks that fall.
 */
public final class IrisProceduralBlocks {
    public static final String[] FACE_PROPERTIES = {"north", "east", "south", "west", "up", "down"};

    private IrisProceduralBlocks() {
    }

    public static int[] faceOffset(String face) {
        return switch (face) {
            case "north" -> new int[]{0, 0, -1};
            case "south" -> new int[]{0, 0, 1};
            case "east" -> new int[]{1, 0, 0};
            case "west" -> new int[]{-1, 0, 0};
            case "up" -> new int[]{0, 1, 0};
            default -> new int[]{0, -1, 0};
        };
    }

    public static boolean paletteSet(IrisMaterialPalette palette) {
        return palette != null && palette.getPalette() != null && !palette.getPalette().isEmpty();
    }

    public static boolean hasProperty(NativeBlockState state, String property) {
        String key = state.key();
        int start = propertyStart(key, property);
        if (start < 0) {
            return false;
        }
        int valueStart = start + property.length() + 2;
        return key.indexOf(',', valueStart) >= 0 || key.indexOf(']', valueStart) >= 0;
    }

    /** Blocks that fall when unsupported: sand, gravel and concrete powder. */
    public static boolean isGravityAffected(NativeBlockState state) {
        if (state == null) {
            return false;
        }
        String key = materialKey(state);
        return key.equals("minecraft:sand")
                || key.equals("minecraft:red_sand")
                || key.equals("minecraft:gravel")
                || key.equals("minecraft:suspicious_sand")
                || key.equals("minecraft:suspicious_gravel")
                || key.endsWith("_concrete_powder");
    }

    public static String materialKey(NativeBlockState state) {
        String memoized = state.materialKey();
        if (memoized != null) {
            return memoized;
        }
        String key = state.key();
        int bracket = key.indexOf('[');
        return bracket < 0 ? key : key.substring(0, bracket);
    }

    public static String propertyValue(NativeBlockState state, String property) {
        String key = state.key();
        int start = propertyStart(key, property);
        if (start < 0) {
            return null;
        }
        int valueStart = start + property.length() + 2;
        int end = key.indexOf(',', valueStart);
        if (end < 0) {
            end = key.indexOf(']', valueStart);
        }
        if (end < 0) {
            return null;
        }
        return key.substring(valueStart, end);
    }

    /**
     * Index of the delimiter opening "[property=" or ",property=" within the key, or -1. Scans in place
     * rather than building the search needles, and prefers a "[" match over a "," match.
     */
    private static int propertyStart(String key, String property) {
        if (key == null) {
            return -1;
        }
        int bracket = key.indexOf('[');
        if (bracket < 0) {
            return -1;
        }
        int start = delimitedPropertyIndex(key, property, bracket, '[');
        return start < 0 ? delimitedPropertyIndex(key, property, bracket, ',') : start;
    }

    private static int delimitedPropertyIndex(String key, String property, int from, char delimiter) {
        int length = property.length();
        int limit = key.length() - length - 2;
        for (int i = key.indexOf(delimiter, from); i >= 0 && i <= limit; i = key.indexOf(delimiter, i + 1)) {
            if (key.regionMatches(i + 1, property, 0, length) && key.charAt(i + 1 + length) == '=') {
                return i;
            }
        }
        return -1;
    }

    public static NativeBlockState normalizeWaterlogging(NativeBlockState state, NativeBlockState existing, boolean enabled) {
        if (state == null) {
            return null;
        }
        String current = propertyValue(state, "waterlogged");
        if (current == null) {
            return state;
        }
        boolean authored = "true".equals(current);
        boolean submerged = existing != null && (existing.isWater() || "true".equals(propertyValue(existing, "waterlogged")));
        String value = submerged && (enabled || authored) ? "true" : "false";
        return value.equals(current) ? state : state.withProperty("waterlogged", value);
    }
}
