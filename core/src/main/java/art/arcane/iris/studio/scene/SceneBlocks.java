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

package art.arcane.iris.studio.scene;

import art.arcane.iris.structure.object.IrisObject;
import com.google.gson.Gson;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Blocks collected for the studio's 3D scene, which it reads little endian:
 * <pre>
 * u32 header length | header, UTF-8 JSON | padding to 4 bytes
 * i32 blocks[blockCount * 4]: x, y, z, then the index of the block's state in the palette
 * </pre>
 * The header holds the block states used once each and the scene's bounds. Air is left out: the studio draws nothing
 * there either. Positions are from the scene's lowest corner.
 */
public final class SceneBlocks {
    /** The part of a block no part is known for. */
    public static final int NO_PART = -1;
    private static final int ALIGNMENT_BYTES = 4;
    private static final int INTS_PER_BLOCK = 4;
    private static final int PART_FIELD = 4;
    private static final Set<String> AIR = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");

    private final Map<String, Integer> palette = new LinkedHashMap<>();
    private final List<int[]> blocks = new ArrayList<>();
    private final int[] min = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
    private final int[] max = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};

    public static SceneBlocks of(IrisObject object) {
        SceneBlocks scene = new SceneBlocks();
        object.getBlocks().forEach((position, state) -> scene.add(position.getBlockX(), position.getBlockY(), position.getBlockZ(), state.key()));
        return scene;
    }

    /**
     * The scene of one of an entry's baked objects, wrapping {@code variant} around how many there are.
     *
     * @param what what the entry is, such as "tree", for the message when it bakes nothing
     */
    public static SceneBlocks ofVariant(List<IrisObject> variants, int variant, String what) {
        if (variants.isEmpty()) {
            throw new NoSuchElementException("This " + what + " bakes nothing");
        }
        return of(variants.get(Math.floorMod(variant, variants.size())));
    }

    public void add(int x, int y, int z, String state) {
        add(x, y, z, state, NO_PART);
    }

    /** @param part which part of the entry placed the block, numbered by the view from 0, or {@link #NO_PART} */
    public void add(int x, int y, int z, String state, int part) {
        if (AIR.contains(state)) {
            return;
        }
        int paletteIndex = palette.computeIfAbsent(state, ignored -> palette.size());
        blocks.add(new int[]{x, y, z, paletteIndex, part});
        int[] at = {x, y, z};
        for (int axis = 0; axis < 3; axis++) {
            min[axis] = Math.min(min[axis], at[axis]);
            max[axis] = Math.max(max[axis], at[axis] + 1);
        }
    }

    public int count() {
        return blocks.size();
    }

    /** The part of each block, in the order {@link #encode} writes them. */
    public int[] parts() {
        return blocks.stream().mapToInt(block -> block[PART_FIELD]).toArray();
    }

    /** The scene with its lowest corner at the origin; an empty scene is one block across. */
    public byte[] encode(Gson gson) {
        boolean empty = blocks.isEmpty();
        int[] origin = empty ? new int[]{0, 0, 0} : min;
        int[] top = empty ? new int[]{1, 1, 1} : max;
        byte[] header = gson.toJson(new Header(List.copyOf(palette.keySet()), blocks.size(),
                new int[]{0, 0, 0}, new int[]{top[0] - origin[0], top[1] - origin[1], top[2] - origin[2]})).getBytes(StandardCharsets.UTF_8);
        int blocksAt = aligned(Integer.BYTES + header.length);
        ByteBuffer buffer = ByteBuffer.allocate(blocksAt + blocks.size() * INTS_PER_BLOCK * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(header.length).put(header).position(blocksAt);
        for (int[] block : blocks) {
            buffer.putInt(block[0] - origin[0]).putInt(block[1] - origin[1]).putInt(block[2] - origin[2]).putInt(block[3]);
        }
        return buffer.array();
    }

    private static int aligned(int bytes) {
        return (bytes + ALIGNMENT_BYTES - 1) / ALIGNMENT_BYTES * ALIGNMENT_BYTES;
    }

    private record Header(List<String> palette, int blockCount, int[] min, int[] max) {
    }
}
