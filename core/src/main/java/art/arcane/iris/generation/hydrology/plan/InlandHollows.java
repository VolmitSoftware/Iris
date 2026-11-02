package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.grid.DemGrid;
import art.arcane.iris.generation.hydrology.grid.HeightLattice;
import art.arcane.iris.generation.hydrology.grid.HydrologySampleBuffer;
import art.arcane.iris.generation.hydrology.grid.PriorityFlood;
import art.arcane.iris.generation.hydrology.water.Pond;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class InlandHollows {
    private static final int MAX_HOLLOW_CELLS = 1 << 14;
    private static final long MAX_BASIN_CELLS = 1L << 20;
    private static final int BASIN_MARGIN = 64;
    private static final int[] AROUND_X = {-1, 0, 1, -1, 1, -1, 0, 1};
    private static final int[] AROUND_Z = {-1, -1, -1, 0, 0, 1, 1, 1};

    private final HeightLattice fine;
    private final DemGrid natural;
    private final boolean[] ocean;
    private final int seaLevel;
    private final int stride;
    private final int originX;
    private final int originZ;
    private final Map<Long, Float> heights = new HashMap<>();
    private final Set<Long> oceans = new HashSet<>();

    private InlandHollows(HeightLattice fine, DemGrid natural, boolean[] ocean, int seaLevel) {
        this.fine = fine;
        this.natural = natural;
        this.ocean = ocean;
        this.seaLevel = seaLevel;
        this.stride = natural.cell() / fine.cell();
        this.originX = Math.floorDiv(natural.originX(), natural.cell());
        this.originZ = Math.floorDiv(natural.originZ(), natural.cell());
    }

    static Hollows classify(HeightLattice fine, DemGrid natural, boolean[] ocean, int seaLevel, float minDepth, float epsilon,
                            List<Pond> lakes) {
        return new InlandHollows(fine, natural, ocean, seaLevel).classify(minDepth, epsilon, lakes);
    }

    private Hollows classify(float minDepth, float epsilon, List<Pond> lakes) {
        int count = natural.values().length;
        BitSet inland = new BitSet(count);
        BitSet basins = new BitSet(count);
        boolean[] seen = new boolean[count];
        for (int start = 0; start < count; start++) {
            if (seen[start] || !belowSea(natural.values()[start], ocean[start])) {
                continue;
            }
            List<Long> local = localComponent(start, seen);
            if (underLake(local, lakes)) {
                continue;
            }
            Hollow hollow = enclosed(local);
            see(hollow.members(), seen);
            if (hollow.open()) {
                continue;
            }
            mark(hollow.members(), inland);
            mark(basin(hollow, minDepth, epsilon), basins);
        }
        return new Hollows(inland, basins);
    }

    private List<Long> localComponent(int start, boolean[] seen) {
        int width = natural.width();
        int depth = natural.depth();
        List<Long> cells = new ArrayList<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        seen[start] = true;
        while (!queue.isEmpty()) {
            int cell = queue.poll();
            cells.add(key(originX + cell % width, originZ + cell / width));
            for (int around = 0; around < AROUND_X.length; around++) {
                int i = cell % width + AROUND_X[around];
                int j = cell / width + AROUND_Z[around];
                int next = j * width + i;
                if (i >= 0 && j >= 0 && i < width && j < depth && !seen[next] && belowSea(natural.values()[next], ocean[next])) {
                    seen[next] = true;
                    queue.add(next);
                }
            }
        }
        return cells;
    }

    private Hollow enclosed(List<Long> local) {
        Set<Long> members = new HashSet<>(local);
        Set<Long> checked = new HashSet<>(local);
        List<Long> frontier = new ArrayList<>(local);
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (long cell : local) {
            minX = Math.min(minX, x(cell));
            minZ = Math.min(minZ, z(cell));
            maxX = Math.max(maxX, x(cell));
            maxZ = Math.max(maxZ, z(cell));
        }
        while (!frontier.isEmpty()) {
            List<Long> around = new ArrayList<>();
            for (long cell : frontier) {
                for (int index = 0; index < AROUND_X.length; index++) {
                    long next = key(x(cell) + AROUND_X[index], z(cell) + AROUND_Z[index]);
                    if (checked.add(next)) {
                        around.add(next);
                    }
                }
            }
            sample(around);
            frontier = new ArrayList<>();
            for (long cell : around) {
                if (oceans.contains(cell)) {
                    return new Hollow(members, minX, minZ, maxX, maxZ, true);
                }
                if (belowSea(heights.get(cell), false)) {
                    members.add(cell);
                    frontier.add(cell);
                    minX = Math.min(minX, x(cell));
                    minZ = Math.min(minZ, z(cell));
                    maxX = Math.max(maxX, x(cell));
                    maxZ = Math.max(maxZ, z(cell));
                }
            }
            if (members.size() > MAX_HOLLOW_CELLS || basinCells(minX, minZ, maxX, maxZ) > MAX_BASIN_CELLS) {
                return new Hollow(members, minX, minZ, maxX, maxZ, true);
            }
        }
        return new Hollow(members, minX, minZ, maxX, maxZ, false);
    }

    private Set<Long> basin(Hollow hollow, float minDepth, float epsilon) {
        int boxX = hollow.minX() - BASIN_MARGIN;
        int boxZ = hollow.minZ() - BASIN_MARGIN;
        int width = hollow.maxX() - hollow.minX() + 1 + 2 * BASIN_MARGIN;
        int depth = hollow.maxZ() - hollow.minZ() + 1 + 2 * BASIN_MARGIN;
        HydrologySampleBuffer samples = new HydrologySampleBuffer(width * depth);
        fine.fill(boxX * stride, boxZ * stride, width, depth, stride, samples);
        DemGrid box = new DemGrid(boxX * natural.cell(), boxZ * natural.cell(), natural.cell(), width, depth);
        System.arraycopy(samples.height(), 0, box.values(), 0, width * depth);
        BitSet seeds = new BitSet(width * depth);
        for (int cell = 0; cell < width * depth; cell++) {
            int i = cell % width;
            int j = cell / width;
            if (samples.ocean()[cell] || i == 0 || j == 0 || i == width - 1 || j == depth - 1) {
                seeds.set(cell);
            }
        }
        float[] filled = PriorityFlood.fill(box, seeds, epsilon);
        float[] values = box.values();
        Set<Long> basin = new HashSet<>(hollow.members());
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        boolean[] seen = new boolean[width * depth];
        for (long cell : hollow.members()) {
            int index = (z(cell) - boxZ) * width + x(cell) - boxX;
            seen[index] = true;
            queue.add(index);
        }
        while (!queue.isEmpty()) {
            int cell = queue.poll();
            for (int around = 0; around < AROUND_X.length; around++) {
                int i = cell % width + AROUND_X[around];
                int j = cell / width + AROUND_Z[around];
                int next = j * width + i;
                if (i >= 0 && j >= 0 && i < width && j < depth && !seen[next] && filled[next] - values[next] > minDepth) {
                    seen[next] = true;
                    queue.add(next);
                    basin.add(key(boxX + i, boxZ + j));
                }
            }
        }
        return basin;
    }

    private void sample(List<Long> cells) {
        List<Long> missing = new ArrayList<>();
        for (long cell : cells) {
            int index = local(cell);
            if (index >= 0) {
                heights.put(cell, natural.values()[index]);
                if (ocean[index]) {
                    oceans.add(cell);
                }
            } else if (!heights.containsKey(cell)) {
                missing.add(cell);
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        int[] nodeX = new int[missing.size()];
        int[] nodeZ = new int[missing.size()];
        for (int index = 0; index < nodeX.length; index++) {
            nodeX[index] = x(missing.get(index)) * stride;
            nodeZ[index] = z(missing.get(index)) * stride;
        }
        HydrologySampleBuffer samples = new HydrologySampleBuffer(nodeX.length);
        fine.fill(nodeX, nodeZ, samples);
        for (int index = 0; index < nodeX.length; index++) {
            heights.put(missing.get(index), samples.height()[index]);
            if (samples.ocean()[index]) {
                oceans.add(missing.get(index));
            }
        }
    }

    private boolean underLake(List<Long> cells, List<Pond> lakes) {
        for (Pond lake : lakes) {
            for (long cell : cells) {
                if (lake.mask().grownContains(x(cell) * natural.cell(), z(cell) * natural.cell())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void see(Set<Long> cells, boolean[] seen) {
        for (long cell : cells) {
            int index = local(cell);
            if (index >= 0) {
                seen[index] = true;
            }
        }
    }

    private void mark(Set<Long> cells, BitSet out) {
        for (long cell : cells) {
            int index = local(cell);
            if (index >= 0) {
                out.set(index);
            }
        }
    }

    private int local(long cell) {
        int i = x(cell) - originX;
        int j = z(cell) - originZ;
        return i < 0 || j < 0 || i >= natural.width() || j >= natural.depth() ? -1 : j * natural.width() + i;
    }

    private boolean belowSea(float height, boolean sea) {
        return !sea && Math.round(height) < seaLevel;
    }

    private static long basinCells(int minX, int minZ, int maxX, int maxZ) {
        return (long) (maxX - minX + 1 + 2 * BASIN_MARGIN) * (maxZ - minZ + 1 + 2 * BASIN_MARGIN);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int x(long key) {
        return (int) (key >> 32);
    }

    private static int z(long key) {
        return (int) key;
    }

    record Hollows(BitSet inland, BitSet basins) {
    }

    private record Hollow(Set<Long> members, int minX, int minZ, int maxX, int maxZ, boolean open) {
    }
}
