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

package art.arcane.iris.engine.procedural.part;

import art.arcane.iris.engine.procedural.IrisProceduralBlocks;
import art.arcane.iris.modules.objects.ObjectBuilder;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gives leaves the state vanilla decay expects: every leaf is told its distance to wood. Leaves farther from wood than
 * vanilla keeps are made persistent, so they stay; the pack's parts should keep wood near them instead.
 */
final class LeafDecay {
    private static final int UNREACHED_DISTANCE = IrisProceduralBlocks.UNREACHED_LEAF_DISTANCE;
    private static final int[][] NEIGHBORS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private final Map<Long, BlockState> blocks;
    private final ObjectBuilder builder;
    private final Set<Long> leaves = new LinkedHashSet<>();

    private LeafDecay(Map<Long, BlockState> blocks, ObjectBuilder builder) {
        this.blocks = blocks;
        this.builder = builder;
        List<Long> keys = new ArrayList<>(blocks.keySet());
        keys.sort(Long::compare);
        for (long key : keys) {
            if (isLeaf(blocks.get(key))) {
                leaves.add(key);
            }
        }
    }

    /** Leaves that decay naturally, except those too far from wood, which are kept persistent. */
    static void supportLeaves(Map<Long, BlockState> blocks, ObjectBuilder builder) {
        LeafDecay decay = new LeafDecay(blocks, builder);
        if (decay.leaves.isEmpty()) {
            return;
        }
        decay.markDistances();
    }

    /** Leaves that never decay. */
    static void persistLeaves(Map<Long, BlockState> blocks, ObjectBuilder builder) {
        LeafDecay decay = new LeafDecay(blocks, builder);
        for (long leaf : decay.leaves) {
            decay.setLeafState(leaf, true, 1);
        }
    }

    private void markDistances() {
        Map<Long, Integer> distance = distances();
        for (long leaf : leaves) {
            Integer d = distance.get(leaf);
            if (d != null && d < UNREACHED_DISTANCE) {
                setLeafState(leaf, false, Math.max(1, d));
            } else {
                setLeafState(leaf, true, UNREACHED_DISTANCE);
            }
        }
    }

    /** How many leaf steps each leaf is from wood, up to the farthest vanilla tracks. */
    private Map<Long, Integer> distances() {
        Map<Long, Integer> distance = new HashMap<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        for (long leaf : leaves) {
            for (int[] n : NEIGHBORS) {
                BlockState neighbor = blocks.get(PartCanvas.key(PartCanvas.x(leaf) + n[0], PartCanvas.y(leaf) + n[1], PartCanvas.z(leaf) + n[2]));
                if (neighbor != null && builder.has(neighbor, BlockTrait.LOG)) {
                    distance.put(leaf, 1);
                    queue.add(leaf);
                    break;
                }
            }
        }
        while (!queue.isEmpty()) {
            long leaf = queue.poll();
            int d = distance.get(leaf);
            if (d >= UNREACHED_DISTANCE) {
                continue;
            }
            for (int[] n : NEIGHBORS) {
                long next = PartCanvas.key(PartCanvas.x(leaf) + n[0], PartCanvas.y(leaf) + n[1], PartCanvas.z(leaf) + n[2]);
                BlockState state = blocks.get(next);
                if (state == null || !isLeaf(state)) {
                    continue;
                }
                Integer known = distance.get(next);
                if (known == null || known > d + 1) {
                    distance.put(next, d + 1);
                    queue.add(next);
                }
            }
        }
        return distance;
    }

    private void setLeafState(long leaf, boolean persistent, int distance) {
        BlockState state = blocks.get(leaf);
        state = builder.withPropertyIfAccepted(state, "persistent", String.valueOf(persistent));
        blocks.put(leaf, builder.withPropertyIfAccepted(state, "distance", String.valueOf(distance)));
    }

    private static boolean isLeaf(BlockState state) {
        return IrisProceduralBlocks.hasProperty(state, "persistent") && IrisProceduralBlocks.hasProperty(state, "distance");
    }
}
