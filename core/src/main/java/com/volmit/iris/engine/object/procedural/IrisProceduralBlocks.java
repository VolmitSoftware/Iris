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

package com.volmit.iris.engine.object.procedural;

import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.math.Vector3i;
import org.bukkit.block.data.BlockData;
import com.volmit.iris.engine.object.IrisMaterialPalette;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shared low-level helpers for building procedural objects in memory: resolving a single block id or a noise palette
 * into a block state, and assembling a raw block map into a centered object shape anchored so its y=0 layer sits one
 * block above the terrain surface (same convention the tree generator uses).
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
        return palette != null && !palette.getPalette().isEmpty();
    }

    public static boolean paletteSet(Optional<IrisMaterialPalette> palette) {
        return palette.filter(IrisProceduralBlocks::paletteSet).isPresent();
    }

    public static BlockData resolve(String block, Optional<IrisMaterialPalette> palette, ObjectBuilder blocks, int x, int y, int z, RNG paletteRng) {
        return resolve(block, palette.orElse(null), blocks, x, y, z, paletteRng);
    }

    public static BlockData resolve(Optional<String> block, Optional<IrisMaterialPalette> palette, ObjectBuilder blocks, int x, int y, int z, RNG paletteRng) {
        return resolve(block.orElse(null), palette.orElse(null), blocks, x, y, z, paletteRng);
    }

    /** The block of {@code palette} at the position, picked with the seed of {@code paletteRng} as the palette's old class did; else {@code block}. */
    public static BlockData resolve(String block, IrisMaterialPalette palette, ObjectBuilder blocks, int x, int y, int z, RNG paletteRng) {
        if (paletteSet(palette)) {
            return blocks.pick(palette, paletteRng.getSeed(), x, y, z);
        }
        if (block != null && !block.isEmpty()) {
            return blocks.block(block);
        }
        return null;
    }

    public static boolean hasProperty(BlockData state, String property) {
        return propertyValue(state, property) != null;
    }

    public static String materialKey(BlockData state) {
        return state.getMaterial().getKey().toString();
    }

    public static String propertyValue(BlockData state, String property) {
        String text = state.getAsString();
        int open = text.indexOf('[');
        if (open < 0) {
            return null;
        }
        for (String pair : text.substring(open + 1, text.length() - 1).split(",")) {
            int equals = pair.indexOf('=');
            if (equals > 0 && pair.substring(0, equals).equals(property)) {
                return pair.substring(equals + 1);
            }
        }
        return null;
    }

    /** The blocks centered on x and z, with their lowest layer one block above the anchor; null when there are none. */
    public static ProceduralShape assemble(Map<Vector3i, BlockData> blocks) {
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

        List<ObjectBlock> placed = new ArrayList<>(blocks.size());
        for (Map.Entry<Vector3i, BlockData> entry : blocks.entrySet()) {
            Vector3i v = entry.getKey();
            int nx = v.getBlockX() - minX - cx;
            int ny = v.getBlockY() - cy + 1;
            int nz = v.getBlockZ() - minZ - cz;
            placed.add(new ObjectBlock(new BlockPosition(nx, ny, nz), entry.getValue()));
        }

        return new ProceduralShape(w, h, d, placed);
    }
}
