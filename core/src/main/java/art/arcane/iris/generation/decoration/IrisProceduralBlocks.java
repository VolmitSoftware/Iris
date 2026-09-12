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
import art.arcane.iris.structure.object.IrisObject;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.volmlib.util.math.Vector3i;
import art.arcane.volmlib.util.math.RNG;

import java.util.Map;

/**
 * Shared low-level helpers for building procedural objects in memory: resolving a
 * single block id or a noise palette into a platform block state, and assembling a
 * raw block map into a centered IrisObject anchored so its y=0 layer sits one block
 * above the terrain surface (same convention the tree generator uses).
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

    public static PlatformBlockState resolve(String block, IrisMaterialPalette palette, IrisData data, int x, int y, int z, RNG paletteRng) {
        if (paletteSet(palette)) {
            return palette.get(paletteRng, x, y, z, data);
        }
        if (block != null && !block.isEmpty()) {
            return B.getStateOrNull(block, false);
        }
        return null;
    }

    public static boolean hasProperty(PlatformBlockState state, String property) {
        String key = state.key();
        int start = propertyStart(key, property);
        if (start < 0) {
            return false;
        }
        int valueStart = start + property.length() + 2;
        return key.indexOf(',', valueStart) >= 0 || key.indexOf(']', valueStart) >= 0;
    }

    /** Blocks that fall when unsupported: sand, gravel and concrete powder. */
    public static boolean isGravityAffected(PlatformBlockState state) {
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

    public static String materialKey(PlatformBlockState state) {
        String memoized = state.materialKey();
        if (memoized != null) {
            return memoized;
        }
        String key = state.key();
        int bracket = key.indexOf('[');
        return bracket < 0 ? key : key.substring(0, bracket);
    }

    public static String propertyValue(PlatformBlockState state, String property) {
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

    public static IrisObject assemble(Map<Vector3i, PlatformBlockState> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return null;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Vector3i v : blocks.keySet()) {
            minX = Math.min(minX, v.getBlockX());
            minY = Math.min(minY, v.getBlockY());
            minZ = Math.min(minZ, v.getBlockZ());
            maxX = Math.max(maxX, v.getBlockX());
            maxY = Math.max(maxY, v.getBlockY());
            maxZ = Math.max(maxZ, v.getBlockZ());
        }

        int w = maxX - minX + 1;
        int h = maxY - minY + 1;
        int d = maxZ - minZ + 1;
        int cx = w / 2;
        int cy = h / 2;
        int cz = d / 2;

        IrisObject object = new IrisObject(w, h, d);
        for (Map.Entry<Vector3i, PlatformBlockState> entry : blocks.entrySet()) {
            Vector3i v = entry.getKey();
            int nx = v.getBlockX() - minX - cx;
            int ny = v.getBlockY() - cy + 1;
            int nz = v.getBlockZ() - minZ - cz;
            object.getBlocks().put(new IrisBlockVector(nx, ny, nz), entry.getValue());
        }

        return object;
    }
}
