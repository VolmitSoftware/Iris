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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TreeSupport {
    private static final int LEGAL_DISTANCE = 6;
    private static final int REACH_GAP = 4;

    private TreeSupport() {
    }

    /**
     * Grows wood toward the leaves farthest from any, one tendril at a time, until every leaf is close enough to wood
     * not to decay or {@code maxTendrils} have grown. Wood only ever gets added, so each leaf's nearest wood is found
     * once and afterwards only compared against the newest tendril.
     */
    public static void ensureLeavesSupported(TreeBlockCanvas canvas, int maxTendrils) {
        if (canvas.getTrunk().isEmpty()) {
            return;
        }
        List<TreeBlockCanvas.Vec> unsupported = unsupportedLeaves(canvas);
        Map<TreeBlockCanvas.Vec, Nearest> nearestWoodByLeaf = NearestWood.of(canvas.getTrunk()).nearestTo(unsupported);
        for (int tendril = 0; tendril < maxTendrils && !unsupported.isEmpty(); tendril++) {
            TreeBlockCanvas.Vec farthestLeaf = farthestFromWood(unsupported, nearestWoodByLeaf);
            List<TreeBlockCanvas.Vec> tendrilWood = growTendril(canvas, nearestWoodByLeaf.get(farthestLeaf).wood(), farthestLeaf);
            unsupported = unsupportedLeaves(canvas);
            moveTowardNewWood(nearestWoodByLeaf, unsupported, tendrilWood);
        }
    }

    private static List<TreeBlockCanvas.Vec> unsupportedLeaves(TreeBlockCanvas canvas) {
        Map<TreeBlockCanvas.Vec, Integer> distance = TreePlausibility.computeDistances(canvas.getTrunk(), canvas.getLeaf());
        List<TreeBlockCanvas.Vec> unsupported = new ArrayList<>();
        for (TreeBlockCanvas.Vec leaf : canvas.getLeaf()) {
            Integer d = distance.get(leaf);
            if (d == null || d > LEGAL_DISTANCE) {
                unsupported.add(leaf);
            }
        }
        return unsupported;
    }

    private static TreeBlockCanvas.Vec farthestFromWood(List<TreeBlockCanvas.Vec> leaves, Map<TreeBlockCanvas.Vec, Nearest> nearestWoodByLeaf) {
        TreeBlockCanvas.Vec farthest = null;
        int farthestGap = -1;
        for (TreeBlockCanvas.Vec leaf : leaves) {
            int gap = nearestWoodByLeaf.get(leaf).distance();
            if (gap > farthestGap) {
                farthestGap = gap;
                farthest = leaf;
            }
        }
        return farthest;
    }

    private static void moveTowardNewWood(Map<TreeBlockCanvas.Vec, Nearest> nearestWoodByLeaf, List<TreeBlockCanvas.Vec> leaves,
                                          List<TreeBlockCanvas.Vec> newWood) {
        for (TreeBlockCanvas.Vec leaf : leaves) {
            Nearest nearest = nearestWoodByLeaf.get(leaf);
            for (TreeBlockCanvas.Vec wood : newWood) {
                int distance = chebyshev(leaf, wood);
                if (distance < nearest.distance()) {
                    nearest = new Nearest(wood, distance);
                }
            }
            nearestWoodByLeaf.put(leaf, nearest);
        }
    }

    private static List<TreeBlockCanvas.Vec> growTendril(TreeBlockCanvas canvas, TreeBlockCanvas.Vec from, TreeBlockCanvas.Vec to) {
        int dx = to.x() - from.x();
        int dy = to.y() - from.y();
        int dz = to.z() - from.z();
        int length = Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.max(Math.abs(dz), 1));
        int steps = Math.max(1, length - REACH_GAP);
        TreeBlockCanvas.Axis axis = TreeFunctions.logAxis(dx, dy, dz);

        List<TreeBlockCanvas.Vec> grown = new ArrayList<>(steps);
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) length;
            int x = from.x() + (int) Math.round(dx * t);
            int y = from.y() + (int) Math.round(dy * t);
            int z = from.z() + (int) Math.round(dz * t);
            canvas.setTrunk(x, y, z, TreeBlockCanvas.Role.TRUNK, axis);
            grown.add(new TreeBlockCanvas.Vec(x, y, z));
        }
        return grown;
    }

    private static int chebyshev(TreeBlockCanvas.Vec a, TreeBlockCanvas.Vec b) {
        return Math.max(Math.max(Math.abs(a.x() - b.x()), Math.abs(a.y() - b.y())), Math.abs(a.z() - b.z()));
    }

    /**
     * Finds the closest wood block to a leaf, by the largest of the x, y and z gaps, searching outward through a coarse
     * grid of wood one shell of buckets at a time. Ties go to the wood that comes first in the trunk, as a plain scan
     * would pick.
     */
    private static final class NearestWood {
        private static final int BUCKET_SIZE_BLOCKS = 8;

        private final Map<Long, List<Integer>> woodIndicesByBucket = new HashMap<>();
        private final TreeBlockCanvas.Vec[] wood;
        private int lowestBucket = Integer.MAX_VALUE;
        private int highestBucket = Integer.MIN_VALUE;

        private NearestWood(TreeBlockCanvas.Vec[] wood) {
            this.wood = wood;
            for (int index = 0; index < wood.length; index++) {
                TreeBlockCanvas.Vec block = wood[index];
                woodIndicesByBucket.computeIfAbsent(bucketKey(bucket(block.x()), bucket(block.y()), bucket(block.z())), ignored -> new ArrayList<>()).add(index);
                for (int coordinate : new int[]{block.x(), block.y(), block.z()}) {
                    lowestBucket = Math.min(lowestBucket, bucket(coordinate));
                    highestBucket = Math.max(highestBucket, bucket(coordinate));
                }
            }
        }

        static NearestWood of(Set<TreeBlockCanvas.Vec> trunk) {
            return new NearestWood(trunk.toArray(TreeBlockCanvas.Vec[]::new));
        }

        Map<TreeBlockCanvas.Vec, Nearest> nearestTo(List<TreeBlockCanvas.Vec> leaves) {
            Map<TreeBlockCanvas.Vec, Nearest> nearestByLeaf = new HashMap<>();
            for (TreeBlockCanvas.Vec leaf : leaves) {
                nearestByLeaf.put(leaf, nearestTo(leaf));
            }
            return nearestByLeaf;
        }

        private Nearest nearestTo(TreeBlockCanvas.Vec leaf) {
            int leafBucketX = bucket(leaf.x());
            int leafBucketY = bucket(leaf.y());
            int leafBucketZ = bucket(leaf.z());
            Search search = new Search(leaf);
            int lastRing = ringReachingAllWood(leafBucketX, leafBucketY, leafBucketZ);
            for (int ring = 0; ring <= lastRing; ring++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    for (int dy = -ring; dy <= ring; dy++) {
                        boolean onShellAlready = Math.abs(dx) == ring || Math.abs(dy) == ring;
                        int dzStep = onShellAlready || ring == 0 ? 1 : 2 * ring;
                        for (int dz = -ring; dz <= ring; dz += dzStep) {
                            search.consider(woodIndicesByBucket.get(bucketKey(leafBucketX + dx, leafBucketY + dy, leafBucketZ + dz)));
                        }
                    }
                }
                boolean fartherRingsCannotBeCloser = search.found() && search.bestDistance <= ring * BUCKET_SIZE_BLOCKS;
                if (fartherRingsCannotBeCloser) {
                    break;
                }
            }
            return new Nearest(wood[search.bestIndex], search.bestDistance);
        }

        private int ringReachingAllWood(int... leafBuckets) {
            int ring = 0;
            for (int leafBucket : leafBuckets) {
                ring = Math.max(ring, Math.max(Math.abs(leafBucket - lowestBucket), Math.abs(leafBucket - highestBucket)));
            }
            return ring;
        }

        private static int bucket(int coordinate) {
            return Math.floorDiv(coordinate, BUCKET_SIZE_BLOCKS);
        }

        private static long bucketKey(int x, int y, int z) {
            return ((long) x & 0x1FFFFF) | (((long) y & 0x1FFFFF) << 21) | (((long) z & 0x1FFFFF) << 42);
        }

        private final class Search {
            private final TreeBlockCanvas.Vec leaf;
            private int bestDistance = Integer.MAX_VALUE;
            private int bestIndex = -1;

            private Search(TreeBlockCanvas.Vec leaf) {
                this.leaf = leaf;
            }

            void consider(List<Integer> woodIndices) {
                if (woodIndices == null) {
                    return;
                }
                for (int index : woodIndices) {
                    int distance = chebyshev(leaf, wood[index]);
                    if (distance < bestDistance || (distance == bestDistance && index < bestIndex)) {
                        bestDistance = distance;
                        bestIndex = index;
                    }
                }
            }

            boolean found() {
                return bestIndex >= 0;
            }
        }
    }

    private record Nearest(TreeBlockCanvas.Vec wood, int distance) {
    }
}
