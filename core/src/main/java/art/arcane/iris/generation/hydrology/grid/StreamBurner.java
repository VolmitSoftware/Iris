package art.arcane.iris.generation.hydrology.grid;

import java.util.BitSet;
import java.util.List;

public final class StreamBurner {
    private StreamBurner() {
    }

    public static void burn(DemGrid raw, List<int[]> cellPaths, float depth) {
        float[] heights = raw.values();
        BitSet burned = new BitSet(heights.length);
        for (int[] path : cellPaths) {
            for (int cell : path) {
                if (burned.get(cell)) {
                    continue;
                }
                burned.set(cell);
                heights[cell] -= depth;
            }
        }
    }
}
