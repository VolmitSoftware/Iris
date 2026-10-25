package art.arcane.iris.generation.hydrology.grid;

import java.util.BitSet;
import java.util.function.IntPredicate;

public final class ChannelHeads {
    private static final int RELIEF_REACH = 64;
    private static final int RELIEF_CELLS = 4;
    private static final float POOLED_DEPTH = 0.5f;

    private ChannelHeads() {
    }

    public static BitSet candidates(DemGrid surface, float[] accumulation, byte[] permission, float threshold, int seaLevel) {
        float[] filled = surface.values();
        if (accumulation.length != filled.length || permission.length < filled.length) {
            throw new IllegalArgumentException("Channel inputs do not match the " + filled.length + "-cell grid: accumulation="
                    + accumulation.length + " permission=" + permission.length);
        }
        BitSet candidates = new BitSet(filled.length);
        for (int cell = 0; cell < filled.length; cell++) {
            if (accumulation[cell] >= threshold && filled[cell] > seaLevel && (permission[cell] & HydrologySampleBuffer.ALLOW_STREAMS) != 0) {
                candidates.set(cell);
            }
        }
        return candidates;
    }

    public static BitSet keep(DemGrid surface, float[] heights, int[] receivers, BitSet candidates, float relief, IntPredicate anchored,
                              IntPredicate keepHead) {
        int count = receivers.length;
        if (heights.length < count || surface.values().length != count) {
            throw new IllegalArgumentException("Channel heads need " + count + " heights and receivers, got heights=" + heights.length);
        }
        boolean[] fed = new boolean[count];
        for (int cell = candidates.nextSetBit(0); cell >= 0; cell = candidates.nextSetBit(cell + 1)) {
            int receiver = receivers[cell];
            if (receiver >= 0 && candidates.get(receiver)) {
                fed[receiver] = true;
            }
        }
        BitSet kept = new BitSet(count);
        for (int cell = candidates.nextSetBit(0); cell >= 0; cell = candidates.nextSetBit(cell + 1)) {
            if (anchored.test(cell) || (!fed[cell] && relieved(surface, heights, receivers, cell, relief) && keepHead.test(cell))) {
                for (int next = cell; next >= 0 && candidates.get(next) && !kept.get(next); next = receivers[next]) {
                    kept.set(next);
                }
            }
        }
        return kept;
    }

    private static boolean relieved(DemGrid surface, float[] heights, int[] receivers, int head, float relief) {
        if (!(relief > 0f) || surface.values()[head] - heights[head] > POOLED_DEPTH) {
            return true;
        }
        int width = surface.width();
        double reach = Math.max(RELIEF_REACH, RELIEF_CELLS * surface.cell());
        double diagonal = surface.cell() * Math.sqrt(2D);
        double travelled = 0D;
        int cell = head;
        while (true) {
            int next = receivers[cell];
            if (next < 0) {
                return false;
            }
            boolean straight = next % width == cell % width || next / width == cell / width;
            travelled += straight ? surface.cell() : diagonal;
            if (travelled > reach) {
                return false;
            }
            if (heights[head] - heights[next] >= relief) {
                return true;
            }
            cell = next;
        }
    }
}
