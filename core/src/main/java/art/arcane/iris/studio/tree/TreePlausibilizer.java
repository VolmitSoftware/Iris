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

package art.arcane.iris.studio.tree;

import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.generation.decoration.IrisProceduralBlocks;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.block.VectorMap;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.volmlib.util.math.RNG;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TreePlausibilizer {
    public static final int MAX_DISTANCE = 6;
    public static final int DEFAULT_REACH = 12;
    public static final int STOP_SHORT = 3;
    private static final int MAX_TENDRILS_PER_CLUSTER = 24;
    private static final int MAX_STALLS_PER_CLUSTER = 2;
    private static final int SCATTER_CLUSTER_LIMIT = 256;
    private static final double MAX_HELIX_AMPLITUDE = 1.6D;
    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;
    private static final int[][] NEIGHBORS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
    };
    private static final Set<String> STEM_WOOD = Set.of(
            "minecraft:bamboo_block",
            "minecraft:stripped_bamboo_block",
            "minecraft:crimson_stem",
            "minecraft:stripped_crimson_stem",
            "minecraft:warped_stem",
            "minecraft:stripped_warped_stem"
    );

    private TreePlausibilizer() {
    }

    public record Result(
            int totalLeaves,
            int unreachableBefore,
            int clusters,
            int branchesGrown,
            int woodPlaced,
            int leavesConvertedToWood,
            int leavesPinnedPersistent,
            int distancesRewritten,
            int unreachableAfter
    ) {
        public boolean mutated() {
            return woodPlaced + leavesConvertedToWood + distancesRewritten > 0;
        }
    }

    public static long seedOf(String key) {
        long hash = 0xCBF29CE484222325L;
        for (int i = 0; i < key.length(); i++) {
            hash ^= key.charAt(i);
            hash *= 0x100000001B3L;
        }
        return hash;
    }

    public static Result analyze(IrisObject obj, long seed, int reach) {
        return run(obj, false, seed, reach);
    }

    public static Result apply(IrisObject obj, long seed, int reach) {
        return run(obj, true, seed, reach);
    }

    private static Result run(IrisObject obj, boolean mutate, long seed, int reach) {
        VectorMap<PlatformBlockState> blocks = obj.getBlocks();
        Workspace ws = new Workspace(blocks);

        int totalLeaves = ws.leaves.size();
        if (totalLeaves == 0) {
            return new Result(0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        ws.template = pickBranchTemplate(ws.positions, ws.wood);
        ws.distances = seedDistances(ws.wood, ws.leaves);
        ws.unreached = new HashSet<>(ws.leaves);
        ws.unreached.removeAll(ws.distances.keySet());
        int unreachableBefore = ws.unreached.size();

        List<List<Long>> clusters = clusterize(ws.unreached);
        int clusterCount = clusters.size();

        if (ws.template != null && clusters.size() <= SCATTER_CLUSTER_LIMIT) {
            int ordinal = 0;
            for (List<Long> cluster : clusters) {
                long clusterSeed = seed ^ (ordinal * GOLDEN_GAMMA);
                ordinal++;
                GapTracker tracker = new GapTracker(cluster, ws.wood);
                if (reach > 0 && tracker.minGap() > reach) {
                    continue;
                }
                ws.growCluster(cluster, clusterSeed, tracker);
            }
        }

        Map<Long, Integer> finalDistances = seedDistances(ws.wood, ws.leaves);
        List<LeafRewrite> rewrites = new ArrayList<>();
        int pinned = 0;
        int unreachableAfter = 0;
        List<Long> sortedLeaves = new ArrayList<>(ws.leaves);
        Collections.sort(sortedLeaves);
        for (long leaf : sortedLeaves) {
            PlatformBlockState state = ws.positions.get(leaf);
            Integer d = finalDistances.get(leaf);
            PlatformBlockState next;
            if (d != null) {
                next = state.withProperty("persistent", "false")
                        .withProperty("distance", String.valueOf(Math.max(1, Math.min(MAX_DISTANCE, d))));
            } else {
                next = state.withProperty("persistent", "true").withProperty("distance", "7");
                pinned++;
                unreachableAfter++;
            }
            if (!next.key().equals(state.key())) {
                rewrites.add(new LeafRewrite(leaf, next));
            }
        }

        if (mutate) {
            for (WoodPlacement placement : ws.placements) {
                blocks.put(unpackKey(placement.key()), placement.state());
            }
            for (LeafRewrite rewrite : rewrites) {
                blocks.put(unpackKey(rewrite.key()), rewrite.state());
            }
        }

        return new Result(
                totalLeaves,
                unreachableBefore,
                clusterCount,
                ws.branches,
                ws.woodPlaced,
                ws.converted,
                pinned,
                rewrites.size(),
                unreachableAfter
        );
    }

    private static PlatformBlockState pickBranchTemplate(Map<Long, PlatformBlockState> positions, Set<Long> wood) {
        if (wood.isEmpty()) {
            return null;
        }
        Map<String, Integer> materialCounts = new HashMap<>();
        for (long key : wood) {
            materialCounts.merge(IrisProceduralBlocks.materialKey(positions.get(key)), 1, Integer::sum);
        }
        String dominant = null;
        int dominantCount = -1;
        for (Map.Entry<String, Integer> e : materialCounts.entrySet()) {
            if (e.getValue() > dominantCount
                    || (e.getValue() == dominantCount && e.getKey().compareTo(dominant) < 0)) {
                dominantCount = e.getValue();
                dominant = e.getKey();
            }
        }
        Map<String, Integer> stateCounts = new HashMap<>();
        Map<String, PlatformBlockState> statesByKey = new HashMap<>();
        for (long key : wood) {
            PlatformBlockState state = positions.get(key);
            if (!IrisProceduralBlocks.materialKey(state).equals(dominant)) {
                continue;
            }
            stateCounts.merge(state.key(), 1, Integer::sum);
            statesByKey.putIfAbsent(state.key(), state);
        }
        String best = null;
        int bestCount = -1;
        for (Map.Entry<String, Integer> e : stateCounts.entrySet()) {
            if (e.getValue() > bestCount
                    || (e.getValue() == bestCount && e.getKey().compareTo(best) < 0)) {
                bestCount = e.getValue();
                best = e.getKey();
            }
        }
        return statesByKey.get(best);
    }

    private static List<List<Long>> clusterize(Set<Long> unreached) {
        List<List<Long>> clusters = new ArrayList<>();
        List<Long> sorted = new ArrayList<>(unreached);
        Collections.sort(sorted);
        Set<Long> assigned = new HashSet<>();
        for (long seedKey : sorted) {
            if (assigned.contains(seedKey)) {
                continue;
            }
            List<Long> cluster = new ArrayList<>();
            Deque<Long> queue = new ArrayDeque<>();
            queue.add(seedKey);
            assigned.add(seedKey);
            while (!queue.isEmpty()) {
                long pos = queue.poll();
                cluster.add(pos);
                int[] xyz = unpack(pos);
                for (int[] n : NEIGHBORS) {
                    long nk = packXYZ(xyz[0] + n[0], xyz[1] + n[1], xyz[2] + n[2]);
                    if (unreached.contains(nk) && assigned.add(nk)) {
                        queue.add(nk);
                    }
                }
            }
            Collections.sort(cluster);
            clusters.add(cluster);
        }
        return clusters;
    }

    private static final class GapTracker {
        private final Map<Long, int[]> leafCells = new HashMap<>();
        private final Map<Long, Integer> gaps = new HashMap<>();
        private final Map<Long, Long> anchors = new HashMap<>();
        private final Map<Long, Long> anchorDistances = new HashMap<>();

        private GapTracker(List<Long> cluster, Set<Long> wood) {
            for (long leaf : cluster) {
                leafCells.put(leaf, unpack(leaf));
                gaps.put(leaf, Integer.MAX_VALUE);
                anchors.put(leaf, -1L);
                anchorDistances.put(leaf, Long.MAX_VALUE);
            }
            for (long w : wood) {
                onWoodPlaced(w);
            }
        }

        private void onWoodPlaced(long woodKey) {
            int[] wx = unpack(woodKey);
            for (Map.Entry<Long, int[]> entry : leafCells.entrySet()) {
                long leaf = entry.getKey();
                int[] lx = entry.getValue();
                int cheb = chebyshev(lx, wx);
                if (cheb < gaps.get(leaf)) {
                    gaps.put(leaf, cheb);
                }
                long dx = lx[0] - wx[0];
                long dy = lx[1] - wx[1];
                long dz = lx[2] - wx[2];
                long d2 = dx * dx + dy * dy + dz * dz;
                long bestD2 = anchorDistances.get(leaf);
                long bestAnchor = anchors.get(leaf);
                if (d2 < bestD2 || (d2 == bestD2 && woodKey < bestAnchor)) {
                    anchorDistances.put(leaf, d2);
                    anchors.put(leaf, woodKey);
                }
            }
        }

        private void drop(long leaf) {
            leafCells.remove(leaf);
            gaps.remove(leaf);
            anchors.remove(leaf);
            anchorDistances.remove(leaf);
        }

        private int minGap() {
            int best = Integer.MAX_VALUE;
            for (int gap : gaps.values()) {
                if (gap < best) {
                    best = gap;
                }
            }
            return best;
        }

        private long worst(Set<Long> unreached) {
            long worst = -1L;
            int worstGap = -1;
            for (Map.Entry<Long, Integer> entry : gaps.entrySet()) {
                long leaf = entry.getKey();
                if (!unreached.contains(leaf)) {
                    continue;
                }
                int gap = entry.getValue();
                if (gap > worstGap || (gap == worstGap && leaf < worst)) {
                    worstGap = gap;
                    worst = leaf;
                }
            }
            return worst;
        }

        private long anchor(long leaf) {
            Long anchor = anchors.get(leaf);
            return anchor == null ? -1L : anchor;
        }

        private int gapOf(long leaf) {
            Integer gap = gaps.get(leaf);
            return gap == null ? Integer.MAX_VALUE : gap;
        }
    }

    private static Map<Long, Integer> seedDistances(Set<Long> wood, Set<Long> leaves) {
        Map<Long, Integer> dist = new HashMap<>();
        Deque<Long> queue = new ArrayDeque<>();
        for (long leaf : leaves) {
            int[] xyz = unpack(leaf);
            for (int[] n : NEIGHBORS) {
                long nk = packXYZ(xyz[0] + n[0], xyz[1] + n[1], xyz[2] + n[2]);
                if (wood.contains(nk)) {
                    dist.put(leaf, 1);
                    queue.add(leaf);
                    break;
                }
            }
        }
        while (!queue.isEmpty()) {
            long pos = queue.poll();
            int d = dist.get(pos);
            if (d >= MAX_DISTANCE) {
                continue;
            }
            int[] xyz = unpack(pos);
            for (int[] n : NEIGHBORS) {
                long nk = packXYZ(xyz[0] + n[0], xyz[1] + n[1], xyz[2] + n[2]);
                if (leaves.contains(nk) && !dist.containsKey(nk)) {
                    dist.put(nk, d + 1);
                    queue.add(nk);
                }
            }
        }
        return dist;
    }

    private static boolean isWood(PlatformBlockState state) {
        String material = IrisProceduralBlocks.materialKey(state);
        return material.endsWith("_log")
                || material.endsWith("_wood")
                || material.endsWith("_hyphae")
                || STEM_WOOD.contains(material);
    }

    private static boolean isDecayableLeaf(PlatformBlockState state) {
        return IrisProceduralBlocks.hasProperty(state, "distance")
                && IrisProceduralBlocks.hasProperty(state, "persistent");
    }

    private static PlatformBlockState axisVariant(PlatformBlockState template, int dx, int dy, int dz) {
        if (!IrisProceduralBlocks.hasProperty(template, "axis")) {
            return template;
        }
        double weightedY = Math.abs(dy) * 1.8D;
        String axis;
        if (weightedY >= Math.abs(dx) && weightedY >= Math.abs(dz)) {
            axis = "y";
        } else if (Math.abs(dx) >= Math.abs(dz)) {
            axis = "x";
        } else {
            axis = "z";
        }
        return template.withProperty("axis", axis);
    }

    private static int chebyshev(int[] a, int[] b) {
        return Math.max(Math.max(Math.abs(a[0] - b[0]), Math.abs(a[1] - b[1])), Math.abs(a[2] - b[2]));
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]
        };
    }

    private static double lengthOf(double[] a) {
        return Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
    }

    private static double[] normalize(double[] a) {
        double len = lengthOf(a);
        return new double[]{a[0] / len, a[1] / len, a[2] / len};
    }

    private static long packKey(IrisBlockVector pos) {
        return packXYZ(pos.getBlockX(), pos.getBlockY(), pos.getBlockZ());
    }

    private static long packXYZ(int x, int y, int z) {
        long lx = (x + 32768L) & 0xFFFFL;
        long ly = (y + 32768L) & 0xFFFFL;
        long lz = (z + 32768L) & 0xFFFFL;
        return (lx << 32) | (ly << 16) | lz;
    }

    private static int[] unpack(long key) {
        int x = (int) ((key >> 32) & 0xFFFFL) - 32768;
        int y = (int) ((key >> 16) & 0xFFFFL) - 32768;
        int z = (int) (key & 0xFFFFL) - 32768;
        return new int[]{x, y, z};
    }

    private static IrisBlockVector unpackKey(long key) {
        int[] xyz = unpack(key);
        return new IrisBlockVector(xyz[0], xyz[1], xyz[2]);
    }

    private record WoodPlacement(long key, PlatformBlockState state) {
    }

    private record LeafRewrite(long key, PlatformBlockState state) {
    }

    private static final class Workspace {
        private final Map<Long, PlatformBlockState> positions;
        private final Set<Long> wood = new HashSet<>();
        private final Set<Long> leaves = new HashSet<>();
        private final List<WoodPlacement> placements = new ArrayList<>();
        private Map<Long, Integer> distances = new HashMap<>();
        private Set<Long> unreached = new HashSet<>();
        private PlatformBlockState template;
        private GapTracker tracker;
        private int minX = Integer.MAX_VALUE;
        private int minY = Integer.MAX_VALUE;
        private int minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int maxY = Integer.MIN_VALUE;
        private int maxZ = Integer.MIN_VALUE;
        private int woodPlaced;
        private int converted;
        private int branches;

        private Workspace(VectorMap<PlatformBlockState> blocks) {
            positions = new HashMap<>(blocks.size() * 2);
            for (Map.Entry<IrisBlockVector, PlatformBlockState> entry : blocks) {
                IrisBlockVector pos = entry.getKey();
                PlatformBlockState state = entry.getValue();
                long key = packKey(pos);
                positions.put(key, state);
                int x = pos.getBlockX();
                int y = pos.getBlockY();
                int z = pos.getBlockZ();
                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (z < minZ) minZ = z;
                if (x > maxX) maxX = x;
                if (y > maxY) maxY = y;
                if (z > maxZ) maxZ = z;
                if (isWood(state)) {
                    wood.add(key);
                } else if (isDecayableLeaf(state)) {
                    leaves.add(key);
                }
            }
        }

        private void growCluster(List<Long> cluster, long clusterSeed, GapTracker gapTracker) {
            tracker = gapTracker;
            try {
                int stalls = 0;
                int budget = Math.max(MAX_TENDRILS_PER_CLUSTER, cluster.size() / 8);
                for (int tendril = 0; tendril < budget; tendril++) {
                    long target = gapTracker.worst(unreached);
                    if (target == -1L) {
                        return;
                    }
                    long anchor = gapTracker.anchor(target);
                    if (anchor == -1L) {
                        return;
                    }
                    int placedBefore = placements.size();
                    int unreachedBefore = unreachedInCluster(cluster);
                    RNG rng = new RNG(clusterSeed).nextParallelRNG(tendril);
                    growTendril(anchor, target, rng);
                    boolean progressed = placements.size() > placedBefore || unreachedInCluster(cluster) < unreachedBefore;
                    if (placements.size() > placedBefore) {
                        branches++;
                    }
                    if (progressed) {
                        stalls = 0;
                        continue;
                    }
                    stalls++;
                    if (stalls >= MAX_STALLS_PER_CLUSTER) {
                        break;
                    }
                }
                directConnect(cluster);
            } finally {
                tracker = null;
            }
        }

        private void growTendril(long anchorKey, long targetKey, RNG rng) {
            int[] anchor = unpack(anchorKey);
            int[] target = unpack(targetKey);
            double[] delta = new double[]{target[0] - anchor[0], target[1] - anchor[1], target[2] - anchor[2]};
            double length = lengthOf(delta);
            if (length < 1.0D) {
                return;
            }
            double[] direction = normalize(delta);
            double[] side = cross(direction, new double[]{0.0D, 1.0D, 0.0D});
            if (lengthOf(side) < 0.05D) {
                side = cross(direction, new double[]{1.0D, 0.0D, 0.0D});
            }
            side = normalize(side);
            double[] up = cross(direction, side);

            double amplitudeMax = Math.min(MAX_HELIX_AMPLITUDE, length / 5.0D);
            double totalTwist = (1.25D + rng.d()) * Math.PI;
            double phase = rng.d() * 2.0D * Math.PI;
            double handedness = rng.b() ? 1.0D : -1.0D;
            double drawLength = Math.max(1.0D, length - STOP_SHORT);
            int steps = (int) Math.ceil(drawLength * 2.0D);

            int[] previous = anchor;
            for (int i = 1; i <= steps; i++) {
                double arc = drawLength * i / steps;
                double fraction = arc / drawLength;
                double amplitude = amplitudeMax * Math.sin(Math.PI * fraction);
                double theta = phase + handedness * totalTwist * (arc / length);
                double offsetSide = amplitude * Math.cos(theta);
                double offsetUp = amplitude * Math.sin(theta);
                int cx = clamp((int) Math.round(anchor[0] + direction[0] * arc + side[0] * offsetSide + up[0] * offsetUp), minX, maxX);
                int cy = clamp((int) Math.round(anchor[1] + direction[1] * arc + side[1] * offsetSide + up[1] * offsetUp), minY, maxY);
                int cz = clamp((int) Math.round(anchor[2] + direction[2] * arc + side[2] * offsetSide + up[2] * offsetUp), minZ, maxZ);
                int[] cell = new int[]{cx, cy, cz};
                bridge(previous, cell);
                previous = cell;
            }
        }

        private void directConnect(List<Long> cluster) {
            List<Long> ordered = new ArrayList<>(cluster);
            if (tracker != null) {
                GapTracker gapTracker = tracker;
                ordered.sort((Long a, Long b) -> {
                    int ga = gapTracker.gapOf(a);
                    int gb = gapTracker.gapOf(b);
                    if (ga != gb) {
                        return Integer.compare(ga, gb);
                    }
                    return Long.compare(a, b);
                });
            }
            for (long target : ordered) {
                if (!unreached.contains(target)) {
                    continue;
                }
                long anchor = tracker == null ? -1L : tracker.anchor(target);
                if (anchor == -1L) {
                    return;
                }
                int placedBefore = placements.size();
                int[] pos = unpack(anchor);
                int[] t = unpack(target);
                int guard = chebyshev(pos, t) * 4 + 8;
                while (guard-- > 0 && unreached.contains(target) && !distances.containsKey(target)) {
                    int dx = Integer.signum(t[0] - pos[0]);
                    int dy = Integer.signum(t[1] - pos[1]);
                    int dz = Integer.signum(t[2] - pos[2]);
                    if (dx == 0 && dy == 0 && dz == 0) {
                        break;
                    }
                    boolean landsOnTarget = pos[0] + dx == t[0] && pos[1] + dy == t[1] && pos[2] + dz == t[2];
                    boolean diagonal = Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 1;
                    if (landsOnTarget && diagonal) {
                        if (dx != 0) {
                            dy = 0;
                            dz = 0;
                        } else if (dy != 0) {
                            dz = 0;
                        }
                    }
                    int[] step = new int[]{dx, dy, dz};
                    int[][] candidates = {{dx, dy, dz}, {dx, 0, 0}, {0, dy, 0}, {0, 0, dz}};
                    for (int[] candidate : candidates) {
                        if (candidate[0] == 0 && candidate[1] == 0 && candidate[2] == 0) {
                            continue;
                        }
                        int nx = pos[0] + candidate[0];
                        int ny = pos[1] + candidate[1];
                        int nz = pos[2] + candidate[2];
                        if (nx == t[0] && ny == t[1] && nz == t[2]) {
                            continue;
                        }
                        if (leaves.contains(packXYZ(nx, ny, nz))) {
                            step = candidate;
                            break;
                        }
                    }
                    pos = new int[]{pos[0] + step[0], pos[1] + step[1], pos[2] + step[2]};
                    placeCell(pos[0], pos[1], pos[2], step[0], step[1], step[2]);
                }
                if (placements.size() > placedBefore) {
                    branches++;
                }
            }
        }

        private void bridge(int[] from, int[] to) {
            int[] pos = from;
            int guard = chebyshev(from, to) * 3 + 4;
            while (guard-- > 0 && (pos[0] != to[0] || pos[1] != to[1] || pos[2] != to[2])) {
                int dx = Integer.signum(to[0] - pos[0]);
                int dy = Integer.signum(to[1] - pos[1]);
                int dz = Integer.signum(to[2] - pos[2]);
                pos = new int[]{pos[0] + dx, pos[1] + dy, pos[2] + dz};
                placeCell(pos[0], pos[1], pos[2], dx, dy, dz);
            }
        }

        private void placeCell(int x, int y, int z, int dx, int dy, int dz) {
            long key = packXYZ(x, y, z);
            if (wood.contains(key)) {
                return;
            }
            PlatformBlockState existing = positions.get(key);
            boolean convertLeaf = existing != null && leaves.contains(key);
            if (existing != null && !convertLeaf) {
                return;
            }
            PlatformBlockState state = axisVariant(template, dx, dy, dz);
            positions.put(key, state);
            wood.add(key);
            if (convertLeaf) {
                leaves.remove(key);
                unreached.remove(key);
                distances.remove(key);
                converted++;
                if (tracker != null) {
                    tracker.drop(key);
                }
            } else {
                woodPlaced++;
            }
            placements.add(new WoodPlacement(key, state));
            if (tracker != null) {
                tracker.onWoodPlaced(key);
            }
            propagate(key);
        }

        private void propagate(long woodKey) {
            int[] center = unpack(woodKey);
            Deque<Long> queue = new ArrayDeque<>();
            for (int[] n : NEIGHBORS) {
                long nk = packXYZ(center[0] + n[0], center[1] + n[1], center[2] + n[2]);
                if (leaves.contains(nk)) {
                    Integer current = distances.get(nk);
                    if (current == null || current > 1) {
                        if (current == null) {
                            unreached.remove(nk);
                        }
                        distances.put(nk, 1);
                        queue.add(nk);
                    }
                }
            }
            while (!queue.isEmpty()) {
                long pos = queue.poll();
                int d = distances.get(pos);
                if (d >= MAX_DISTANCE) {
                    continue;
                }
                int[] xyz = unpack(pos);
                for (int[] n : NEIGHBORS) {
                    long nk = packXYZ(xyz[0] + n[0], xyz[1] + n[1], xyz[2] + n[2]);
                    if (leaves.contains(nk)) {
                        Integer current = distances.get(nk);
                        if (current == null || current > d + 1) {
                            if (current == null) {
                                unreached.remove(nk);
                            }
                            distances.put(nk, d + 1);
                            queue.add(nk);
                        }
                    }
                }
            }
        }

        private int unreachedInCluster(List<Long> cluster) {
            int count = 0;
            for (long leaf : cluster) {
                if (unreached.contains(leaf)) {
                    count++;
                }
            }
            return count;
        }

        private static int clamp(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
