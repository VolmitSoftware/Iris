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

import art.arcane.volmlib.nativelib.terrain.NativeBlockState;

import java.awt.image.BufferedImage;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * What an object is made of: its size and how many of each block type it uses, most used first.
 */
record ObjectSummary(String key, int sizeX, int sizeY, int sizeZ, int blockCount, List<BlockUse> blockUses) {
    static ObjectSummary of(String key, ObjectMesh.Bounds bounds, Iterable<NativeBlockState> blocks,
                            Function<NativeBlockState, BufferedImage> iconOf) {
        Map<String, Integer> counts = new HashMap<>();
        Map<String, NativeBlockState> examples = new HashMap<>();
        int blockCount = 0;
        for (NativeBlockState block : blocks) {
            String blockType = BlockNames.typeOf(block);
            counts.merge(blockType, 1, Integer::sum);
            examples.putIfAbsent(blockType, block);
            blockCount++;
        }
        List<BlockUse> uses = counts.entrySet().stream()
                .map(count -> new BlockUse(count.getKey(), count.getValue(), iconOf.apply(examples.get(count.getKey()))))
                .sorted(Comparator.comparingInt(BlockUse::count).reversed().thenComparing(BlockUse::blockType))
                .toList();
        return new ObjectSummary(key, bounds.sizeX(), bounds.sizeY(), bounds.sizeZ(), blockCount, uses);
    }

    String name() {
        return key.substring(key.lastIndexOf('/') + 1);
    }

    double filledFraction() {
        long volume = (long) sizeX * sizeY * sizeZ;
        return volume == 0 ? 0D : blockCount / (double) volume;
    }

    /**
     * @param icon the block's texture, or null when Minecraft's textures are not available
     */
    record BlockUse(String blockType, int count, BufferedImage icon) {
    }
}
