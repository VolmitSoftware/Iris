/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core.studio.bridge;

import com.google.gson.Gson;
import com.volmit.block.BlockState;
import com.volmit.geometry.BlockBox;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.GeneratedObject;
import com.volmit.object.ObjectBlock;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A block scene as the studio reads it, little endian:
 * <pre>
 * u32 header length | header, UTF-8 JSON | padding to 4 bytes
 * i32 blocks[blockCount * 4]: x, y, z from the anchor, then the index of the block's state in the palette
 * </pre>
 * The header holds the block states the scene uses, once each, and its bounds from the anchor. Air is left out: the
 * studio draws nothing there either.
 */
final class SceneEncoding {
    private static final int ALIGNMENT_BYTES = 4;
    private static final int INTS_PER_BLOCK = 4;
    private static final Set<String> AIR = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");

    private SceneEncoding() {
    }

    static byte[] encode(GeneratedObject scene, Gson gson) {
        BlockPosition anchor = scene.anchor();
        Map<String, Integer> palette = new LinkedHashMap<>();
        List<int[]> blocks = new ArrayList<>();
        for (ObjectBlock block : scene.blocks()) {
            if (isAir(block.state())) {
                continue;
            }
            int paletteIndex = palette.computeIfAbsent(block.state().key(), ignored -> palette.size());
            BlockPosition position = block.position();
            blocks.add(new int[]{position.x() - anchor.x(), position.y() - anchor.y(), position.z() - anchor.z(), paletteIndex});
        }
        BlockBox bounds = scene.bounds();
        Header header = new Header(List.copyOf(palette.keySet()), blocks.size(),
                new int[]{bounds.minX() - anchor.x(), bounds.minY() - anchor.y(), bounds.minZ() - anchor.z()},
                new int[]{bounds.maxX() - anchor.x(), bounds.maxY() - anchor.y(), bounds.maxZ() - anchor.z()});
        byte[] headerBytes = gson.toJson(header).getBytes(StandardCharsets.UTF_8);
        int blocksAt = aligned(Integer.BYTES + headerBytes.length);
        ByteBuffer buffer = ByteBuffer.allocate(blocksAt + blocks.size() * INTS_PER_BLOCK * Integer.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(headerBytes.length).put(headerBytes).position(blocksAt);
        for (int[] block : blocks) {
            for (int value : block) {
                buffer.putInt(value);
            }
        }
        return buffer.array();
    }

    private static boolean isAir(BlockState state) {
        return AIR.contains(state.material().toString());
    }

    private static int aligned(int bytes) {
        return (bytes + ALIGNMENT_BYTES - 1) / ALIGNMENT_BYTES * ALIGNMENT_BYTES;
    }

    /** @param min the lowest corner from the anchor, inclusive; {@code max} the highest, exclusive */
    private record Header(List<String> palette, int blockCount, int[] min, int[] max) {
    }
}
