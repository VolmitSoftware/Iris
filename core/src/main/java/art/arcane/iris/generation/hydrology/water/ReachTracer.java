package art.arcane.iris.generation.hydrology.water;

import art.arcane.iris.generation.hydrology.grid.DemGrid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

final class ReachTracer {
    private static final int STATION_TILE = 256;

    private ReachTracer() {
    }

    static List<Chain> trace(DemGrid grid, int[] receivers, BitSet channels, int[] pondAt, BitSet outlets, int seaLevel) {
        BitSet starts = starts(grid, receivers, channels, pondAt, outlets);
        List<Chain> chains = new ArrayList<>(starts.cardinality());
        for (int start = starts.nextSetBit(0); start >= 0; start = starts.nextSetBit(start + 1)) {
            Chain chain = walk(grid, receivers, channels, pondAt, starts, seaLevel, start);
            if (chain.cells().length >= 2) {
                chains.add(chain);
            }
        }
        return chains;
    }

    static int[] starts(List<Chain> chains) {
        int[] starts = new int[chains.size()];
        for (int index = 0; index < starts.length; index++) {
            starts[index] = chains.get(index).start();
        }
        return starts;
    }

    static int indexOf(int[] starts, int cell) {
        return Math.max(-1, Arrays.binarySearch(starts, cell));
    }

    static int[] downstream(List<Chain> chains) {
        int[] starts = starts(chains);
        int[] downstream = new int[chains.size()];
        for (int index = 0; index < downstream.length; index++) {
            Chain chain = chains.get(index);
            downstream[index] = chain.end() == End.CONTINUES ? indexOf(starts, chain.last()) : -1;
        }
        return downstream;
    }

    private static BitSet starts(DemGrid grid, int[] receivers, BitSet channels, int[] pondAt, BitSet outlets) {
        int[] donors = new int[receivers.length];
        BitSet starts = new BitSet(receivers.length);
        for (int cell = channels.nextSetBit(0); cell >= 0; cell = channels.nextSetBit(cell + 1)) {
            int receiver = receivers[cell];
            if (receiver >= 0 && channels.get(receiver)) {
                donors[receiver]++;
                if (pond(pondAt, cell) >= 0 || crossesTile(grid, cell, receiver)) {
                    starts.set(receiver);
                }
            }
        }
        for (int cell = channels.nextSetBit(0); cell >= 0; cell = channels.nextSetBit(cell + 1)) {
            if (donors[cell] != 1 || outlets.get(cell)) {
                starts.set(cell);
            }
        }
        for (int cell = starts.nextSetBit(0); cell >= 0; cell = starts.nextSetBit(cell + 1)) {
            if (pond(pondAt, cell) >= 0) {
                starts.clear(cell);
            }
        }
        return starts;
    }

    private static Chain walk(DemGrid grid, int[] receivers, BitSet channels, int[] pondAt, BitSet starts, int seaLevel, int start) {
        int[] path = new int[16];
        int length = 0;
        path[length++] = start;
        int cell = start;
        while (true) {
            int receiver = receivers[cell];
            if (receiver < 0) {
                return new Chain(Arrays.copyOf(path, length), End.EDGE, -1);
            }
            if (length == path.length) {
                path = Arrays.copyOf(path, 2 * length);
            }
            int pond = pond(pondAt, receiver);
            if (pond >= 0) {
                path[length++] = receiver;
                return new Chain(Arrays.copyOf(path, length), End.POND, pond);
            }
            if (!channels.get(receiver)) {
                if (grid.values()[receiver] > seaLevel) {
                    return new Chain(Arrays.copyOf(path, length), End.EDGE, -1);
                }
                path[length++] = receiver;
                return new Chain(Arrays.copyOf(path, length), End.OCEAN, -1);
            }
            path[length++] = receiver;
            if (starts.get(receiver)) {
                return new Chain(Arrays.copyOf(path, length), End.CONTINUES, -1);
            }
            if (length > receivers.length) {
                throw new IllegalStateException("Drainage receivers loop through cell " + receiver);
            }
            cell = receiver;
        }
    }

    private static boolean crossesTile(DemGrid grid, int from, int to) {
        return Math.floorDiv(grid.blockX(from), STATION_TILE) != Math.floorDiv(grid.blockX(to), STATION_TILE)
                || Math.floorDiv(grid.blockZ(from), STATION_TILE) != Math.floorDiv(grid.blockZ(to), STATION_TILE);
    }

    private static int pond(int[] pondAt, int cell) {
        return pondAt == null ? -1 : pondAt[cell];
    }

    enum End {
        CONTINUES,
        POND,
        OCEAN,
        EDGE
    }

    record Chain(int[] cells, End end, int pond) {
        int start() {
            return cells[0];
        }

        int last() {
            return cells[cells.length - 1];
        }
    }
}
