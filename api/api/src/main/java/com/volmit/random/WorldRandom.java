package com.volmit.random;

import com.volmit.resource.ResourceId;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.RandomAccess;

/** A random stream whose every value is defined here, so the same seed gives the same draws on
 * every machine and every JDK. World generation draws from it: stages through
 * {@link com.volmit.stage.StageContext#random()}.
 *
 * <p>It deliberately does not implement {@link java.util.random.RandomGenerator}: that interface's
 * default methods, and any a later JDK adds, use algorithms the JDK may change, which would change
 * existing worlds. Only the methods below exist, and each is specified exactly.
 *
 * <p><b>Algorithm.</b> The state is one {@code long}, initially the seed. {@link #nextLong()} is
 * SplitMix64 (Steele, Lea and Flood, 2014): add {@code 0x9e3779b97f4a7c15} to the state, then return
 * {@code mix(state)}, where {@code mix} is the finalizer
 * {@code z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9; z = (z ^ (z >>> 27)) * 0x94d049bb133111eb; z ^ (z >>> 31)}.
 * Every other method is defined on {@code nextLong()} values:
 * <ul>
 *   <li>{@code nextInt()}: the high 32 bits of one value;</li>
 *   <li>{@code nextBoolean()}: whether one value is negative (its highest bit);</li>
 *   <li>{@code nextFloat()}: the high 24 bits of one value times 2<sup>-24</sup>;</li>
 *   <li>{@code nextDouble()}: the high 53 bits of one value times 2<sup>-53</sup>;</li>
 *   <li>bounded integers: Lemire's multiply-and-reject method, which is unbiased. For a range of
 *       {@code n} values, {@code ints} take the high 32 bits {@code x} of one value and compute the
 *       64-bit product {@code x * n}, {@code longs} take one value {@code x} and compute the
 *       128-bit product; when the product's low half is below 2<sup>32</sup> (or 2<sup>64</sup>)
 *       {@code mod n}, they draw again; the result is the product's high half plus the origin;</li>
 *   <li>bounded doubles: {@code origin + nextDouble() * (bound - origin)}, a result that rounds up
 *       to {@code bound} replaced by the largest double below it (see {@link #nextDouble(double, double)}
 *       for ranges wider than the largest double);</li>
 *   <li>{@code nextGaussian()}: Marsaglia's polar method on pairs of {@code nextDouble()} values,
 *       using {@link StrictMath}, whose results are fixed;</li>
 *   <li>{@code chance(p)}: {@code nextDouble() < p};</li>
 *   <li>{@code pick(items)}: {@code items.get(nextInt(items.size()))};</li>
 *   <li>{@code shuffle(items)}: Fisher-Yates, for {@code i} from {@code size - 1} down to {@code 1}
 *       swapping index {@code i} with {@code nextInt(i + 1)}.</li>
 * </ul>
 * Methods without rejection draw exactly one value. {@code nextInt(0, n)} equals {@code nextInt(n)}
 * and {@code nextLong(0, n)} equals {@code nextLong(n)}.
 *
 * <p>Not thread-safe. One stage uses its stream within one invocation; do not share or retain it.
 */
public final class WorldRandom {
    private static final long GOLDEN_GAMMA = 0x9e3779b97f4a7c15L;
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final int INT_BITS = 32;
    private static final long UNSIGNED_INT_MASK = 0xffff_ffffL;
    private static final long INT_RANGE = 1L << INT_BITS;
    private static final int FLOAT_SHIFT = Long.SIZE - 24;
    private static final float FLOAT_UNIT = 0x1.0p-24f;
    private static final int DOUBLE_SHIFT = Long.SIZE - 53;
    private static final double DOUBLE_UNIT = 0x1.0p-53;

    private long state;

    public WorldRandom(long seed) {
        state = seed;
    }

    /**
     * The seed for {@code purpose} of a world whose seed is {@code worldSeed}: {@code mix(worldSeed ^ mix(h))}, where
     * {@code mix} is the finalizer above and {@code h} the 64-bit FNV-1a hash (offset basis {@code 0xcbf29ce484222325},
     * prime {@code 0x100000001b3}) of the UTF-8 bytes of {@code purpose.toString()}. Different purposes give independent
     * seeds; see {@link com.volmit.world.PackWorld#seed(ResourceId)}.
     */
    public static long purposeSeed(long worldSeed, ResourceId purpose) {
        long hash = FNV_OFFSET_BASIS;
        for (byte octet : purpose.toString().getBytes(StandardCharsets.UTF_8)) {
            hash = (hash ^ (octet & 0xff)) * FNV_PRIME;
        }
        return mix(worldSeed ^ mix(hash));
    }

    /** A uniformly distributed {@code long}: the next SplitMix64 output. */
    public long nextLong() {
        state += GOLDEN_GAMMA;
        return mix(state);
    }

    /** A uniformly distributed {@code long} in {@code [0, bound)}. Throws if {@code bound <= 0}. */
    public long nextLong(long bound) {
        if (bound <= 0) throw new IllegalArgumentException("bound must be positive: " + bound);
        return boundedLong(bound);
    }

    /** A uniformly distributed {@code long} in {@code [origin, bound)}, for any {@code origin < bound},
     * including ranges wider than {@code Long.MAX_VALUE}. Throws if {@code origin >= bound}.
     */
    public long nextLong(long origin, long bound) {
        if (origin >= bound) throw invalidRange(origin, bound);
        return origin + boundedLong(bound - origin);
    }

    /** A uniformly distributed {@code int}: the high 32 bits of {@link #nextLong()}. */
    public int nextInt() {
        return (int) (nextLong() >>> INT_BITS);
    }

    /** A uniformly distributed {@code int} in {@code [0, bound)}. Throws if {@code bound <= 0}. */
    public int nextInt(int bound) {
        if (bound <= 0) throw new IllegalArgumentException("bound must be positive: " + bound);
        return (int) boundedInt(bound);
    }

    /** A uniformly distributed {@code int} in {@code [origin, bound)}, for any {@code origin < bound},
     * including ranges wider than {@code Integer.MAX_VALUE}. Throws if {@code origin >= bound}.
     */
    public int nextInt(int origin, int bound) {
        if (origin >= bound) throw invalidRange(origin, bound);
        return (int) (origin + boundedInt((long) bound - origin));
    }

    /** {@code true} or {@code false} with equal probability: whether {@link #nextLong()} is negative. */
    public boolean nextBoolean() {
        return nextLong() < 0;
    }

    /** A uniformly distributed {@code float} in {@code [0, 1)}, a multiple of 2<sup>-24</sup>. */
    public float nextFloat() {
        return (nextLong() >>> FLOAT_SHIFT) * FLOAT_UNIT;
    }

    /** A uniformly distributed {@code double} in {@code [0, 1)}, a multiple of 2<sup>-53</sup>. */
    public double nextDouble() {
        return (nextLong() >>> DOUBLE_SHIFT) * DOUBLE_UNIT;
    }

    /** A {@code double} in {@code [0, bound)}: {@code nextDouble() * bound}, never {@code bound} itself.
     * Throws unless {@code bound} is positive and finite.
     */
    public double nextDouble(double bound) {
        if (!(bound > 0 && bound < Double.POSITIVE_INFINITY)) {
            throw new IllegalArgumentException("bound must be positive and finite: " + bound);
        }
        return belowBound(nextDouble() * bound, bound);
    }

    /** A {@code double} in {@code [origin, bound)}, never {@code bound} itself:
     * {@code origin + nextDouble() * (bound - origin)}. When {@code bound - origin} exceeds the largest
     * double, the range is halved first: {@code 2 * (origin / 2 + nextDouble() * (bound / 2 - origin / 2))}.
     * Throws unless both are finite and {@code origin < bound}.
     */
    public double nextDouble(double origin, double bound) {
        if (!(origin < bound && Double.isFinite(origin) && Double.isFinite(bound))) {
            throw new IllegalArgumentException("origin and bound must be finite with origin < bound: [" + origin + ", " + bound + ")");
        }
        double unit = nextDouble();
        double width = bound - origin;
        double value = Double.isFinite(width) ? origin + unit * width : 2 * (origin / 2 + unit * (bound / 2 - origin / 2));
        return belowBound(value, bound);
    }

    /** A normally distributed {@code double} with mean 0 and standard deviation 1, by Marsaglia's polar
     * method: draw {@code u = 2 * nextDouble() - 1} and {@code v = 2 * nextDouble() - 1} until
     * {@code s = u * u + v * v} is in {@code (0, 1)}, then return
     * {@code u * StrictMath.sqrt(-2 * StrictMath.log(s) / s)}. The second value of the pair is discarded.
     */
    public double nextGaussian() {
        double u;
        double s;
        do {
            u = 2 * nextDouble() - 1;
            double v = 2 * nextDouble() - 1;
            s = u * u + v * v;
        } while (s >= 1 || s == 0);
        return u * StrictMath.sqrt(-2 * StrictMath.log(s) / s);
    }

    /** Whether an event of the given probability happens: {@code nextDouble() < probability}. Always
     * draws one value, so {@code chance(0)} is never and {@code chance(1)} always true. Throws unless
     * {@code probability} is in {@code [0, 1]}.
     */
    public boolean chance(double probability) {
        if (!(probability >= 0 && probability <= 1)) {
            throw new IllegalArgumentException("probability must be in [0, 1]: " + probability);
        }
        return nextDouble() < probability;
    }

    /** One element chosen uniformly: {@code items.get(nextInt(items.size()))}, so it draws exactly what
     * {@link #nextInt(int)} draws. Throws if {@code items} is empty.
     */
    public <T> T pick(List<T> items) {
        if (items.isEmpty()) throw new IllegalArgumentException("cannot pick from an empty list");
        return items.get(nextInt(items.size()));
    }

    /** Shuffles {@code items} in place by Fisher-Yates: for {@code i} from {@code size - 1} down to
     * {@code 1}, swap the elements at {@code i} and {@code nextInt(i + 1)}. A list of {@code n >= 2}
     * elements takes {@code n - 1} {@code nextInt} calls; a shorter list draws nothing and is not touched.
     *
     * <p>{@link RandomAccess} lists are swapped through {@link List#set}. Other lists, such as
     * {@link java.util.LinkedList}, are copied to an array, shuffled there and written back through
     * their {@link ListIterator}, like {@link Collections#shuffle}; the result is the same. A list that
     * does not support {@code set} throws its {@link UnsupportedOperationException} before any element
     * changes.
     */
    public void shuffle(List<?> items) {
        if (items.size() < 2) return;
        if (items instanceof RandomAccess) {
            fisherYates(items.size(), (i, j) -> Collections.swap(items, i, j));
            return;
        }
        shuffleThroughArray(items);
    }

    private void shuffleThroughArray(List<?> items) {
        Object[] copy = items.toArray();
        fisherYates(copy.length, (i, j) -> {
            Object swapped = copy[i];
            copy[i] = copy[j];
            copy[j] = swapped;
        });
        @SuppressWarnings("unchecked")
        ListIterator<Object> writer = (ListIterator<Object>) items.listIterator();
        for (Object item : copy) {
            writer.next();
            writer.set(item);
        }
    }

    /** The one place the shuffle order is defined, so both list kinds give the same result. */
    private void fisherYates(int size, IndexSwap swap) {
        for (int i = size - 1; i > 0; i--) swap.swap(i, nextInt(i + 1));
    }

    /** Lemire's method for {@code range} in {@code [1, 2^32]}: high half of a 32-by-32-bit product. */
    private long boundedInt(long range) {
        long product = (nextLong() >>> INT_BITS) * range;
        long low = product & UNSIGNED_INT_MASK;
        if (low < range) {
            long threshold = (INT_RANGE - range) % range;
            while (low < threshold) {
                product = (nextLong() >>> INT_BITS) * range;
                low = product & UNSIGNED_INT_MASK;
            }
        }
        return product >>> INT_BITS;
    }

    /** Lemire's method for {@code range} in {@code [1, 2^64)}, read as unsigned: high half of a
     * 64-by-64-bit product.
     */
    private long boundedLong(long range) {
        long value = nextLong();
        long low = value * range;
        if (Long.compareUnsigned(low, range) < 0) {
            long threshold = Long.remainderUnsigned(-range, range);
            while (Long.compareUnsigned(low, threshold) < 0) {
                value = nextLong();
                low = value * range;
            }
        }
        return Math.unsignedMultiplyHigh(value, range);
    }

    private static double belowBound(double value, double bound) {
        return value < bound ? value : Math.nextDown(bound);
    }

    private static IllegalArgumentException invalidRange(long origin, long bound) {
        return new IllegalArgumentException("origin must be below bound: [" + origin + ", " + bound + ")");
    }

    @FunctionalInterface
    private interface IndexSwap {
        void swap(int i, int j);
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
