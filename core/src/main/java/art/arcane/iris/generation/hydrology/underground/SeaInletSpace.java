package art.arcane.iris.generation.hydrology.underground;

import java.util.Arrays;
import java.util.Collection;

public final class SeaInletSpace {
    private static final int SHELL = SeaInletRasterizer.SHELL;
    private static final int NONE = SeaInletRasterizer.NONE;

    private final int originX;
    private final int originZ;
    private final int size;
    private final int[] lows;
    private final int[] highs;

    private SeaInletSpace(int originX, int originZ, int size, int[] lows, int[] highs) {
        this.originX = originX;
        this.originZ = originZ;
        this.size = size;
        this.lows = lows;
        this.highs = highs;
    }

    public static SeaInletSpace of(Collection<SeaInlet> inlets, int originX, int originZ, int size) {
        int[] lows = new int[size * size];
        int[] highs = new int[size * size];
        Arrays.fill(lows, NONE);
        Arrays.fill(highs, NONE);
        int grid = size + 2 * SHELL;
        int[] floors = new int[grid * grid];
        int[] ceilings = new int[grid * grid];
        for (SeaInlet inlet : inlets) {
            if (!SeaInletRasterizer.shape(inlet, originX - SHELL, originZ - SHELL, grid, floors, ceilings)) {
                continue;
            }
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    int cell = (z + SHELL) * grid + x + SHELL;
                    int low = SeaInletRasterizer.nearby(floors, grid, cell, true);
                    if (low == NONE) {
                        continue;
                    }
                    int column = z * size + x;
                    int bottom = low - SHELL;
                    int top = Math.max(inlet.waterLevel(), SeaInletRasterizer.nearby(ceilings, grid, cell, false));
                    lows[column] = lows[column] == NONE ? bottom : Math.min(lows[column], bottom);
                    highs[column] = Math.max(highs[column], top);
                }
            }
        }
        return new SeaInletSpace(originX, originZ, size, lows, highs);
    }

    public boolean contains(int x, int y, int z) {
        int localX = x - originX;
        int localZ = z - originZ;
        if (localX < 0 || localX >= size || localZ < 0 || localZ >= size) {
            return false;
        }
        int column = localZ * size + localX;
        return lows[column] != NONE && y >= lows[column] && y <= highs[column];
    }
}
