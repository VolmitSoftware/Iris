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

package com.volmit.iris.engine.object.procedural.tree;

import com.volmit.iris.engine.object.procedural.IrisProceduralBlocks;
import com.volmit.iris.engine.object.procedural.ObjectBuilder;
import org.bukkit.block.data.BlockData;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class TreePlausibility {
    private static final int MAX_DISTANCE = 7;
    private static final int[][] NEIGHBORS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private TreePlausibility() {
    }

    public static void apply(Map<TreeBlockCanvas.Vec, BlockData> resolved, Set<TreeBlockCanvas.Vec> trunkPositions,
                             Set<TreeBlockCanvas.Vec> leafPositions, Tree tree, ObjectBuilder blocks) {
        Set<TreeBlockCanvas.Vec> realLeaves = new HashSet<>();
        for (TreeBlockCanvas.Vec v : leafPositions) {
            BlockData state = resolved.get(v);
            if (state != null && IrisProceduralBlocks.hasProperty(state, "persistent") && IrisProceduralBlocks.hasProperty(state, "distance")) {
                realLeaves.add(v);
            }
        }
        if (realLeaves.isEmpty()) {
            return;
        }

        Map<TreeBlockCanvas.Vec, Integer> distance = computeDistances(trunkPositions, realLeaves);

        for (TreeBlockCanvas.Vec v : realLeaves) {
            BlockData leaf = resolved.get(v);
            Integer d = distance.get(v);
            if (!tree.plausible()) {
                leaf = withLeafState(blocks, leaf, "true", "1");
            } else if (d != null && d < MAX_DISTANCE) {
                leaf = withLeafState(blocks, leaf, "false", String.valueOf(Math.max(1, d)));
            } else {
                leaf = withLeafState(blocks, leaf, "true", String.valueOf(MAX_DISTANCE));
            }
            resolved.put(v, leaf);
        }
    }

    private static BlockData withLeafState(ObjectBuilder blocks, BlockData leaf, String persistent, String distance) {
        return blocks.withProperty(blocks.withProperty(leaf, "persistent", persistent), "distance", distance);
    }

    static Map<TreeBlockCanvas.Vec, Integer> computeDistances(Set<TreeBlockCanvas.Vec> trunkPositions, Set<TreeBlockCanvas.Vec> realLeaves) {
        Map<TreeBlockCanvas.Vec, Integer> distance = new HashMap<>();
        ArrayDeque<TreeBlockCanvas.Vec> queue = new ArrayDeque<>();

        for (TreeBlockCanvas.Vec v : realLeaves) {
            for (int[] n : NEIGHBORS) {
                if (trunkPositions.contains(new TreeBlockCanvas.Vec(v.x() + n[0], v.y() + n[1], v.z() + n[2]))) {
                    distance.put(v, 1);
                    queue.add(v);
                    break;
                }
            }
        }

        while (!queue.isEmpty()) {
            TreeBlockCanvas.Vec v = queue.poll();
            int d = distance.get(v);
            if (d >= MAX_DISTANCE) {
                continue;
            }
            for (int[] n : NEIGHBORS) {
                TreeBlockCanvas.Vec nv = new TreeBlockCanvas.Vec(v.x() + n[0], v.y() + n[1], v.z() + n[2]);
                if (!realLeaves.contains(nv)) {
                    continue;
                }
                Integer existing = distance.get(nv);
                if (existing == null || existing > d + 1) {
                    distance.put(nv, d + 1);
                    queue.add(nv);
                }
            }
        }

        return distance;
    }
}
