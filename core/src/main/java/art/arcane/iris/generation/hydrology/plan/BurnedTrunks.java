package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.hydrology.grid.DemGrid;
import art.arcane.iris.generation.hydrology.grid.StreamBurner;
import art.arcane.iris.generation.hydrology.water.Reach;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class BurnedTrunks {
    private final BitSet cells;
    private final int[] next;
    private final float[] catchment;
    private final float epsilon;
    private int[] order;
    private BitSet entries;

    private BurnedTrunks(int count, float epsilon) {
        this.cells = new BitSet(count);
        this.next = new int[count];
        this.catchment = new float[count];
        this.epsilon = epsilon;
        Arrays.fill(next, -1);
    }

    static BurnedTrunks burn(DemGrid raw, List<Reach> reaches, float depth, float epsilon) {
        BurnedTrunks trunks = new BurnedTrunks(raw.values().length, epsilon);
        Map<Long, Reach> byId = new HashMap<>();
        for (Reach reach : reaches) {
            byId.putIfAbsent(reach.id(), reach);
        }
        List<int[]> paths = new ArrayList<>();
        for (Reach reach : reaches) {
            for (int[] path : TrunkRouter.route(raw, reach, byId.get(reach.downstreamId()))) {
                trunks.claim(path, reach.catchment());
                paths.add(path);
            }
        }
        StreamBurner.burn(raw, paths, depth);
        trunks.sort();
        trunks.descend(raw.values());
        return trunks;
    }

    BitSet cells() {
        return (BitSet) cells.clone();
    }

    boolean contains(int cell) {
        return cells.get(cell);
    }

    void descend(float[] heights) {
        for (int cell : order) {
            int successor = next[cell];
            if (successor >= 0) {
                heights[successor] = Math.min(heights[successor], Math.min(heights[cell] - epsilon, Math.nextDown(heights[cell])));
            }
        }
    }

    void route(int[] receivers) {
        for (int cell = cells.nextSetBit(0); cell >= 0; cell = cells.nextSetBit(cell + 1)) {
            if (next[cell] >= 0) {
                receivers[cell] = next[cell];
            }
        }
    }

    void inject(float[] weights) {
        for (int cell = entries.nextSetBit(0); cell >= 0; cell = entries.nextSetBit(cell + 1)) {
            weights[cell] += catchment[cell];
        }
    }

    void pin(float[] accumulation) {
        for (int cell = cells.nextSetBit(0); cell >= 0; cell = cells.nextSetBit(cell + 1)) {
            accumulation[cell] = catchment[cell];
        }
    }

    private void claim(int[] path, float reachCatchment) {
        for (int index = 0; index < path.length; index++) {
            int cell = path[index];
            cells.set(cell);
            catchment[cell] = Math.max(catchment[cell], reachCatchment);
            int previous = index > 0 ? path[index - 1] : -1;
            if (previous >= 0 && next[previous] < 0 && !leadsTo(cell, previous)) {
                next[previous] = cell;
            }
        }
    }

    private boolean leadsTo(int from, int target) {
        for (int cell = from; cell >= 0; cell = next[cell]) {
            if (cell == target) {
                return true;
            }
        }
        return false;
    }

    private void sort() {
        order = topological();
        entries = new BitSet(next.length);
        entries.or(cells);
        for (int cell = cells.nextSetBit(0); cell >= 0; cell = cells.nextSetBit(cell + 1)) {
            if (next[cell] >= 0) {
                entries.clear(next[cell]);
            }
        }
    }

    private int[] topological() {
        int[] incoming = new int[next.length];
        for (int cell = cells.nextSetBit(0); cell >= 0; cell = cells.nextSetBit(cell + 1)) {
            if (next[cell] >= 0) {
                incoming[next[cell]]++;
            }
        }
        int[] sorted = new int[cells.cardinality()];
        int tail = 0;
        for (int cell = cells.nextSetBit(0); cell >= 0; cell = cells.nextSetBit(cell + 1)) {
            if (incoming[cell] == 0) {
                sorted[tail++] = cell;
            }
        }
        for (int head = 0; head < tail; head++) {
            int successor = next[sorted[head]];
            if (successor >= 0 && --incoming[successor] == 0) {
                sorted[tail++] = successor;
            }
        }
        return Arrays.copyOf(sorted, tail);
    }
}
