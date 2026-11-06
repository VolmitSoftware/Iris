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

package art.arcane.iris.engine.procedural;

import art.arcane.volmlib.util.math.Vector3i;
import com.volmit.block.BlockState;
import com.volmit.block.PaletteSpec;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.ObjectBlock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Block helpers for baking procedural objects: reading a state's properties, and assembling a raw block map into a
 * shape centered on x and z with its lowest layer one block above the anchor.
 */
public final class IrisProceduralBlocks {
    /** The distance vanilla gives a leaf with no wood near enough to keep it. */
    public static final int UNREACHED_LEAF_DISTANCE = 7;

    private IrisProceduralBlocks() {
    }

    /** Whether {@code state} is a leaf baked with no wood near enough for vanilla to keep it. */
    public static boolean isLeafTooFarFromWood(BlockState state) {
        return hasProperty(state, "persistent") && String.valueOf(UNREACHED_LEAF_DISTANCE).equals(propertyValue(state, "distance"));
    }

    public static boolean paletteSet(PaletteSpec palette) {
        return palette != null && !palette.palette().isEmpty();
    }

    public static boolean hasProperty(BlockState state, String property) {
        return state.properties().containsKey(property);
    }

    public static String propertyValue(BlockState state, String property) {
        return state.properties().get(property);
    }

    /** The blocks centered on x and z, with their lowest layer one block above the anchor; null when there are none. */
    public static ProceduralShape assemble(Map<Vector3i, BlockState> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return null;
        }

        Anchor anchor = Anchor.of(blocks.keySet());
        List<ObjectBlock> placed = new ArrayList<>(blocks.size());
        for (Map.Entry<Vector3i, BlockState> entry : blocks.entrySet()) {
            placed.add(new ObjectBlock(anchor.place(entry.getKey()), entry.getValue()));
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

        public BlockPosition place(Vector3i position) {
            return new BlockPosition(position.getBlockX() - minX - width / 2, position.getBlockY() - height / 2 + 1,
                    position.getBlockZ() - minZ - depth / 2);
        }
    }
}
