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

package art.arcane.iris.generation.procedural;

import art.arcane.iris.generation.decoration.IrisProceduralBlocks;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.math.Vector3i;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Block helpers for baking procedural objects: reading a state's properties, and assembling a raw block map into a
 * shape centered on x and z with its lowest layer one block above the anchor.
 */
public final class ProceduralBlocks {
    /** The distance vanilla gives a leaf with no wood near enough to keep it. */
    public static final int UNREACHED_LEAF_DISTANCE = 7;

    private ProceduralBlocks() {
    }

    public static boolean isLeafTooFarFromWood(NativeBlockState state) {
        return hasProperty(state, "persistent") && String.valueOf(UNREACHED_LEAF_DISTANCE).equals(propertyValue(state, "distance"));
    }

    public static boolean paletteSet(IrisMaterialPalette palette) {
        return IrisProceduralBlocks.paletteSet(palette);
    }

    public static boolean hasProperty(NativeBlockState state, String property) {
        return IrisProceduralBlocks.hasProperty(state, property);
    }

    public static String propertyValue(NativeBlockState state, String property) {
        return IrisProceduralBlocks.propertyValue(state, property);
    }

    /** The blocks centered on x and z, with their lowest layer one block above the anchor; null when there are none. */
    public static ProceduralShape assemble(Map<Vector3i, NativeBlockState> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return null;
        }

        Anchor anchor = Anchor.of(blocks.keySet());
        List<ProceduralBlock> placed = new ArrayList<>(blocks.size());
        for (Map.Entry<Vector3i, NativeBlockState> entry : blocks.entrySet()) {
            placed.add(anchor.place(entry.getKey(), entry.getValue()));
        }

        return new ProceduralShape(anchor.width(), anchor.height(), anchor.depth(), placed);
    }

    /** Where {@link #assemble} moves each block of a set: centered on x and z, its lowest layer one block above the anchor. */
    public record Anchor(int minX, int minZ, int width, int height, int depth) {
        public static Anchor of(Collection<Vector3i> positions) {
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (Vector3i v : positions) {
                minX = Math.min(minX, v.getBlockX());
                minY = Math.min(minY, v.getBlockY());
                minZ = Math.min(minZ, v.getBlockZ());
                maxX = Math.max(maxX, v.getBlockX());
                maxY = Math.max(maxY, v.getBlockY());
                maxZ = Math.max(maxZ, v.getBlockZ());
            }
            return new Anchor(minX, minZ, maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        }

        public ProceduralBlock place(Vector3i position, NativeBlockState state) {
            return new ProceduralBlock(position.getBlockX() - minX - width / 2, position.getBlockY() - height / 2 + 1,
                    position.getBlockZ() - minZ - depth / 2, state);
        }

        public ProceduralBlock place(Vector3i position) {
            return place(position, null);
        }
    }
}
