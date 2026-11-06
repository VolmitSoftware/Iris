package com.volmit.random;

import com.volmit.resource.ResourceId;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Pins every method to values computed outside Java from the specification in {@link WorldRandom}'s
 * documentation ({@code src/test/reference/world_random.py}), so any change to an algorithm fails here
 * instead of silently changing existing worlds.
 */
class WorldRandomTest {
    private static final long SEED = 42;
    private static final int REPEATS = 1_000;

    private static <T> List<T> draws(int count, Function<WorldRandom, T> method) {
        var random = new WorldRandom(SEED);
        return Stream.generate(() -> method.apply(random)).limit(count).toList();
    }

    @Test
    void longsAreSplitMix64Outputs() {
        assertEquals(List.of(-4767286540954276203L, 2949826092126892291L, 5139283748462763858L,
                6349198060258255764L, 701532786141963250L, -2430762948046562554L), draws(6, WorldRandom::nextLong));
    }

    @Test
    void purposeSeedsFollowTheirSpecification() {
        assertEquals(4999169706209116627L, WorldRandom.purposeSeed(42, ResourceId.parse("example:moss/patches")));
        assertEquals(1770219120571522516L, WorldRandom.purposeSeed(42, ResourceId.parse("example:moss/caves")));
        assertEquals(1140829150526819945L, WorldRandom.purposeSeed(-7, ResourceId.parse("example:moss/patches")));
    }

    @Test
    void intsAreTheHighHalfOfALong() {
        assertEquals(List.of(-1109970394, 686809907, 1196582743, 1478287871, 163338330, -565956102),
                draws(6, WorldRandom::nextInt));
    }

    @Test
    void boundedIntsMatchTheirSpecification() {
        assertEquals(List.of(7, 1, 2, 3, 0, 8), draws(6, random -> random.nextInt(10)));
        assertEquals(List.of(2, -4, -3, -2, -5, 3), draws(6, random -> random.nextInt(-5, 5)));
        assertEquals(List.of(1037513253, -1460673742, -950900906, -669195778, -1984145319, 1581527545),
                draws(6, random -> random.nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE)));
    }

    /** A range of 3 * 2^30 values rejects one draw in four; these twelve values take fourteen draws. */
    @Test
    void boundedIntsRejectDrawsThatWouldBias() {
        assertEquals(List.of(241264028, -1632376218, -1250046591, -1038767745, -2024979901, 649274747,
                        431532146, -1052489127, -1487448649, -559454326, -493719002, -472403562),
                draws(12, random -> random.nextInt(Integer.MIN_VALUE, 1 << 30)));
    }

    @Test
    void boundedLongsMatchTheirSpecification() {
        assertEquals(List.of(741L, 159L, 278L, 344L, 38L, 868L), draws(6, random -> random.nextLong(1000)));
        assertEquals(List.of(483L, -681L, -443L, -312L, -924L, 736L), draws(6, random -> random.nextLong(-1000, 1000)));
        assertEquals(List.of(4456085495900499604L, -6273545944727883518L, -4084088288392011951L,
                        -2874173976596520045L, -8521839250712812559L, 6792609088808213253L),
                draws(6, random -> random.nextLong(Long.MIN_VALUE, Long.MAX_VALUE)));
    }

    /** A range of 3 * 2^62 values rejects one draw in four; these twelve values take fourteen draws. */
    @Test
    void boundedLongsRejectDrawsThatWouldBias() {
        assertEquals(List.of(1036221112711680751L, -7011002467759606590L, -5368909225507702915L,
                        -8697222447248303371L, 2788613807392465988L, -6201723502271557115L,
                        -4520406378645414305L, -666636742666774078L, -6388543298285055153L,
                        -2402838030758921074L, -2120506962181442260L, -2028957847325109687L),
                draws(12, random -> random.nextLong(Long.MIN_VALUE, 1L << 62)));
    }

    @Test
    void booleansAreTheHighestBitOfALong() {
        assertEquals(List.of(true, false, false, false, false, true, false, true, false, true, false, false),
                draws(12, WorldRandom::nextBoolean));
    }

    @Test
    void floatsAndDoublesAreTheHighBitsOfALong() {
        assertEquals(List.of(0.7415648698806763f, 0.1599103808403015f, 0.27860110998153687f,
                0.34419065713882446f, 0.038030147552490234f, 0.868228018283844f), draws(6, WorldRandom::nextFloat));
        assertEquals(List.of(0.7415648787718233, 0.1599103928769201, 0.27860113025513866,
                0.34419071652363753, 0.03803016854024621, 0.8682280765465323), draws(6, WorldRandom::nextDouble));
    }

    @Test
    void boundedDoublesMatchTheirSpecification() {
        assertEquals(List.of(7.415648787718233, 1.599103928769201, 2.786011302551387, 3.4419071652363753,
                0.3803016854024621, 8.682280765465324), draws(6, random -> random.nextDouble(10.0)));
        assertEquals(List.of(4.915648787718233, -0.900896071230799, 0.28601130255138685, 0.9419071652363753,
                -2.119698314597538, 6.182280765465324), draws(6, random -> random.nextDouble(-2.5, 7.5)));
    }

    @Test
    void doubleRangesWiderThanTheLargestDoubleAreHalvedFirst() {
        assertEquals(List.of(8.685190483839085e+307, -1.2227535039263658e+308, -7.960144564132267e+307,
                        -5.601945585065466e+307, -1.6609599890574008e+308, 1.3239221705425129e+308),
                draws(6, random -> random.nextDouble(-Double.MAX_VALUE, Double.MAX_VALUE)));
    }

    /** Most draws in a range one ulp wide round up to the bound and are replaced by the origin. */
    @Test
    void boundedDoublesNeverReturnTheBound() {
        double bound = Math.nextUp(1.0);
        assertEquals(List.of(1.0), draws(REPEATS, random -> random.nextDouble(1.0, bound)).stream().distinct().toList());
    }

    @Test
    void gaussiansUseThePolarMethodWithStrictMath() {
        assertEquals(List.of(0.49295065581737485, -1.2810773478777024, -0.6018779810957331,
                -1.5423606818352653, -1.4515103855695575, 1.9375059271254913), draws(6, WorldRandom::nextGaussian));
    }

    @Test
    void chanceIsADoubleBelowTheProbability() {
        assertEquals(List.of(false, true, false, false, true, false, true, false, false, false, true, false),
                draws(12, random -> random.chance(0.25)));
        assertEquals(List.of(false), draws(REPEATS, random -> random.chance(0)).stream().distinct().toList());
        assertEquals(List.of(true), draws(REPEATS, random -> random.chance(1)).stream().distinct().toList());
    }

    @Test
    void pickTakesTheElementAtNextInt() {
        assertEquals(List.of("h", "b", "c", "d", "a", "i"),
                draws(6, random -> random.pick(List.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"))));
    }

    @Test
    void shuffleIsFisherYatesFromTheLastIndexDown() {
        assertEquals(List.of(), shuffled(0));
        assertEquals(List.of(0), shuffled(1));
        assertEquals(List.of(0, 1), shuffled(2));
        assertEquals(List.of(8, 3, 6, 5, 4, 0, 9, 2, 1, 7), shuffled(10));
    }

    /** The first {@code nextInt(2)} is 1, which keeps the pair; the second is 0, which swaps it. */
    @Test
    void shuffleSwapsAPairWhenNextIntIsZero() {
        var random = new WorldRandom(SEED);
        var pair = new ArrayList<>(List.of(0, 1));

        random.shuffle(pair);
        assertEquals(List.of(0, 1), pair);
        random.shuffle(pair);
        assertEquals(List.of(1, 0), pair);
    }

    @Test
    void shuffleGivesTheSameResultForListsWithoutRandomAccess() {
        var linked = new LinkedList<>(range(10));
        new WorldRandom(SEED).shuffle(linked);

        assertEquals(shuffled(10), linked);
    }

    @Test
    void shuffleOfAListWithoutSetThrowsAndLeavesItUnchanged() {
        var fixed = List.of(0, 1, 2);
        var unmodifiableLinked = Collections.unmodifiableList(new LinkedList<>(fixed));

        assertThrows(UnsupportedOperationException.class, () -> new WorldRandom(SEED).shuffle(fixed));
        assertThrows(UnsupportedOperationException.class, () -> new WorldRandom(SEED).shuffle(unmodifiableLinked));
        assertEquals(fixed, unmodifiableLinked);
    }

    @Test
    void shuffleOfFewerThanTwoElementsTouchesNothing() {
        assertDoesNotThrow(() -> new WorldRandom(SEED).shuffle(List.of(0)));
        assertDoesNotThrow(() -> new WorldRandom(SEED).shuffle(Collections.unmodifiableList(new LinkedList<>(List.of(0)))));
    }

    @Test
    void pickFromAnEmptyListThrows() {
        assertThrows(IllegalArgumentException.class, () -> new WorldRandom(SEED).pick(List.of()));
    }

    /** {@code pick} draws what {@code nextInt(size)} draws; a shuffle of {@code n >= 2} elements draws
     * {@code n - 1} values (no rejection at these bounds and seed), a shorter one nothing.
     */
    @Test
    void listHelpersDrawAFixedNumberOfValues() {
        var longs = draws(10, WorldRandom::nextLong);
        var picked = new WorldRandom(SEED);
        picked.pick(List.of("a", "b", "c"));
        assertEquals(longs.get(1), picked.nextLong());

        for (int size : new int[] {0, 1, 2, 10}) {
            var random = new WorldRandom(SEED);
            random.shuffle(new ArrayList<>(range(size)));
            assertEquals(longs.get(Math.max(size - 1, 0)), random.nextLong(), "shuffle of " + size);
        }
    }

    @Test
    void zeroOriginRangesEqualTheSingleBoundMethods() {
        assertEquals(draws(REPEATS, random -> random.nextInt(37)), draws(REPEATS, random -> random.nextInt(0, 37)));
        assertEquals(draws(REPEATS, random -> random.nextLong(37)), draws(REPEATS, random -> random.nextLong(0, 37)));
        assertEquals(List.of(0), draws(REPEATS, random -> random.nextInt(1)).stream().distinct().toList());
        assertEquals(List.of(0L), draws(REPEATS, random -> random.nextLong(1)).stream().distinct().toList());
    }

    /** How many values a method draws is part of the sequence: later draws shift if it changes. */
    @Test
    void methodsWithoutRejectionDrawExactlyOneValue() {
        List<Consumer<WorldRandom>> singleDraws = List.of(WorldRandom::nextInt, WorldRandom::nextBoolean,
                WorldRandom::nextFloat, WorldRandom::nextDouble, random -> random.nextDouble(3.0),
                random -> random.nextDouble(-1.0, 1.0), random -> random.chance(0.5));
        long second = draws(2, WorldRandom::nextLong).getLast();

        for (var method : singleDraws) {
            var random = new WorldRandom(SEED);
            method.accept(random);
            assertEquals(second, random.nextLong());
        }
    }

    @Test
    void invalidBoundsThrow() {
        var random = new WorldRandom(SEED);
        List<Runnable> invalid = List.of(
                () -> random.nextInt(0), () -> random.nextInt(-1), () -> random.nextInt(5, 5), () -> random.nextInt(5, 4),
                () -> random.nextLong(0), () -> random.nextLong(-1), () -> random.nextLong(5, 5), () -> random.nextLong(5, 4),
                () -> random.nextDouble(0), () -> random.nextDouble(-1), () -> random.nextDouble(Double.NaN),
                () -> random.nextDouble(Double.POSITIVE_INFINITY), () -> random.nextDouble(1, 1),
                () -> random.nextDouble(2, 1), () -> random.nextDouble(Double.NaN, 1),
                () -> random.nextDouble(0, Double.POSITIVE_INFINITY), () -> random.nextDouble(Double.NEGATIVE_INFINITY, 0),
                () -> random.chance(-0.1), () -> random.chance(1.1), () -> random.chance(Double.NaN));

        for (var call : invalid) assertThrows(IllegalArgumentException.class, call::run);
    }

    /** No method may fall through to an algorithm the JDK defines. A new public method needs pinned
     * values above before it joins this list.
     */
    @Test
    void exposesOnlyTheMethodsPinnedHere() {
        assertFalse(RandomGenerator.class.isAssignableFrom(WorldRandom.class));
        assertTrue(Modifier.isFinal(WorldRandom.class.getModifiers()));
        var methods = Arrays.stream(WorldRandom.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(WorldRandomTest::signature)
                .collect(Collectors.toSet());

        assertEquals(Set.of("nextLong()", "nextLong(long)", "nextLong(long,long)", "nextInt()", "nextInt(int)",
                "nextInt(int,int)", "nextBoolean()", "nextFloat()", "nextDouble()", "nextDouble(double)",
                "nextDouble(double,double)", "nextGaussian()", "chance(double)", "pick(java.util.List)",
                "shuffle(java.util.List)", "purposeSeed(long,com.volmit.resource.ResourceId)"), methods);
    }

    private static List<Integer> shuffled(int size) {
        var items = new ArrayList<>(range(size));
        new WorldRandom(SEED).shuffle(items);
        return items;
    }

    private static List<Integer> range(int size) {
        return Stream.iterate(0, i -> i + 1).limit(size).toList();
    }

    private static String signature(Method method) {
        return method.getName() + Arrays.stream(method.getParameterTypes()).map(Class::getName)
                .collect(Collectors.joining(",", "(", ")"));
    }
}
