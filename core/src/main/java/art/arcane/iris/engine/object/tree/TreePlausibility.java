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

package art.arcane.iris.engine.object.tree;

import art.arcane.iris.engine.object.IrisProceduralBlocks;
import art.arcane.iris.engine.object.IrisProceduralTree;
import art.arcane.iris.spi.PlatformBlockState;

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

    public static void apply(Map<TreeBlockCanvas.Vec, PlatformBlockState> resolved, Set<TreeBlockCanvas.Vec> trunkPositions,
                             Set<TreeBlockCanvas.Vec> leafPositions, IrisProceduralTree tree) {
        Set<TreeBlockCanvas.Vec> realLeaves = new HashSet<>();
        for (TreeBlockCanvas.Vec v : leafPositions) {
            PlatformBlockState state = resolved.get(v);
            if (state != null && IrisProceduralBlocks.hasProperty(state, "persistent") && IrisProceduralBlocks.hasProperty(state, "distance")) {
                realLeaves.add(v);
            }
        }
        if (realLeaves.isEmpty()) {
            return;
        }

        Map<TreeBlockCanvas.Vec, Integer> distance = tree.isPlausible()
                ? computeDistances(trunkPositions, realLeaves)
                : Map.of();

        for (TreeBlockCanvas.Vec v : realLeaves) {
            PlatformBlockState leaf = resolved.get(v);
            Integer d = distance.get(v);
            if (!tree.isPlausible()) {
                leaf = leaf.withProperty("persistent", "true").withProperty("distance", "1");
            } else if (d != null && d < MAX_DISTANCE) {
                leaf = leaf.withProperty("persistent", "false").withProperty("distance", String.valueOf(Math.max(1, d)));
            } else {
                leaf = leaf.withProperty("persistent", "true").withProperty("distance", String.valueOf(MAX_DISTANCE));
            }
            resolved.put(v, leaf);
        }
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
