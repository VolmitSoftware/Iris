package art.arcane.iris.generation.decoration;

import art.arcane.iris.generation.runtime.PackRuntime;
import art.arcane.iris.pack.value.IrisRange;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;

import java.util.Arrays;

/**
 * One ore list (a biome's, region's or dimension's surface or underground ores) indexed by Y. Each Y
 * holds the indices of the generators whose range contains it, in list order, so resolving a block tries exactly
 * the generators that could place there and still returns the first one that does. The seeded chance fields live in
 * an {@link OreBandsRuntime}, aligned with {@link #generators()}.
 */
public final class IrisOreBands {
    public static final IrisOreBands EMPTY = new IrisOreBands(new IrisOreGenerator[0], 0, -1, null);
    private static final int MAXIMUM_INDEXED_HEIGHT = 8192;

    private final IrisOreGenerator[] generators;
    private final int[] everyGenerator;
    private final int minY;
    private final int maxY;
    private final int[][] byY;

    private IrisOreBands(IrisOreGenerator[] generators, int minY, int maxY, int[][] byY) {
        this.generators = generators;
        this.everyGenerator = new int[generators.length];
        Arrays.setAll(everyGenerator, index -> index);
        this.minY = minY;
        this.maxY = maxY;
        this.byY = byY;
    }

    public static IrisOreBands of(KList<IrisOreGenerator> ores) {
        if (ores == null || ores.isEmpty()) {
            return EMPTY;
        }

        IrisOreGenerator[] generators = ores.toArray(new IrisOreGenerator[0]);
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (IrisOreGenerator generator : generators) {
            IrisRange range = generator.getRange();
            min = Math.min(min, range.getMin());
            max = Math.max(max, range.getMax());
        }
        if (!(min <= max)) {
            return EMPTY;
        }

        int minY = (int) Math.ceil(min);
        int maxY = (int) Math.floor(max);
        if (minY > maxY) {
            return EMPTY;
        }
        long height = (long) maxY - minY + 1L;
        return new IrisOreBands(generators, minY, maxY,
                height > MAXIMUM_INDEXED_HEIGHT ? null : index(generators, minY, (int) height));
    }

    private static int[][] index(IrisOreGenerator[] generators, int minY, int height) {
        int[][] byY = new int[height][];
        int[] scratch = new int[generators.length];
        int[] previous = null;
        for (int offset = 0; offset < height; offset++) {
            int y = minY + offset;
            int count = 0;
            for (int index = 0; index < generators.length; index++) {
                if (generators[index].getRange().contains(y)) {
                    scratch[count++] = index;
                }
            }
            int[] candidates = Arrays.copyOf(scratch, count);
            byY[offset] = previous != null && Arrays.equals(previous, candidates) ? previous : candidates;
            previous = byY[offset];
        }
        return byY;
    }

    public boolean hasOres() {
        return minY <= maxY;
    }

    public int lowestY() {
        return minY;
    }

    public int highestY() {
        return maxY;
    }

    public boolean contains(int y) {
        return y >= minY && y <= maxY;
    }

    /** The banded generators in list order; chance fields built for them must keep this order. */
    public IrisOreGenerator[] generators() {
        return generators.clone();
    }

    /**
     * The ore placed at a Y inside {@link #contains(int)}, or null when no generator places one.
     */
    NativeBlockState generate(int x, int y, int z, RNG rng, IrisOreGenerator.OreChance[] chances, PackRuntime runtime) {
        int[] candidates = byY == null ? everyGenerator : byY[y - minY];
        for (int index : candidates) {
            NativeBlockState ore = generators[index].generate(x, y, z, rng, chances[index], runtime);
            if (ore != null) {
                return ore;
            }
        }
        return null;
    }
}
