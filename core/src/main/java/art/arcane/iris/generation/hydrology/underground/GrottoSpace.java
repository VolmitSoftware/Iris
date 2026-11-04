package art.arcane.iris.generation.hydrology.underground;

import java.util.Arrays;
import java.util.Collection;

public final class GrottoSpace {
    private static final int NONE = Integer.MIN_VALUE;

    private final int originX;
    private final int originZ;
    private final int size;
    private final int[] lows;
    private final int[] highs;

    private GrottoSpace(int originX, int originZ, int size, int[] lows, int[] highs) {
        this.originX = originX;
        this.originZ = originZ;
        this.size = size;
        this.lows = lows;
        this.highs = highs;
    }

    public static GrottoSpace of(Collection<Grotto> grottos, int originX, int originZ, int size) {
        int[] lows = new int[size * size];
        int[] highs = new int[size * size];
        Arrays.fill(lows, NONE);
        Arrays.fill(highs, NONE);
        int shell = GrottoRasterizer.SHELL;
        for (Grotto grotto : grottos) {
            GrottoShape shape = new GrottoShape(grotto).scoped(originX, originZ, originX + size - 1, originZ + size - 1);
            int bottom = shape.minY() - shell;
            int top = shape.maxY() + shell;
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    if (shape.near(originX + x, originZ + z)) {
                        int column = z * size + x;
                        lows[column] = lows[column] == NONE ? bottom : Math.min(lows[column], bottom);
                        highs[column] = Math.max(highs[column], top);
                    }
                }
            }
        }
        return new GrottoSpace(originX, originZ, size, lows, highs);
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
