package art.arcane.iris.generation.hydrology.grid;

import java.util.Arrays;

public final class FlowAccumulator {
    private FlowAccumulator() {
    }

    public static float[] accumulate(int[] receivers, float[] filled, float[] weights, float saturation) {
        int count = receivers.length;
        if (filled.length != count || weights.length != count) {
            throw new IllegalArgumentException("Accumulation inputs differ in size: receivers=" + count
                    + " filled=" + filled.length + " weights=" + weights.length);
        }
        long[] order = new long[count];
        for (int cell = 0; cell < count; cell++) {
            order[cell] = ((long) ~CellHeap.sortable(filled[cell]) << 32) | cell;
        }
        Arrays.sort(order);
        float[] accumulation = weights.clone();
        for (long key : order) {
            int cell = (int) key;
            float total = Math.min(accumulation[cell], saturation);
            accumulation[cell] = total;
            int receiver = receivers[cell];
            if (receiver < 0) {
                continue;
            }
            if (!(filled[receiver] < filled[cell])) {
                throw new IllegalArgumentException("Receiver " + receiver + " of cell " + cell + " is not strictly lower");
            }
            accumulation[receiver] += total;
        }
        return accumulation;
    }
}
