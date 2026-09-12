package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimensionCarvingResolver;
import art.arcane.volmlib.util.math.BlockPosition;
import art.arcane.volmlib.util.math.RNG;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class IrisCarveModifierBiomeBlendTest {
    private static final long SEED = 371924L;

    @Test
    public void everyRollPreservesAllNeighborIdentityAndNullCombinations() throws Exception {
        IrisBiome center = new IrisBiome();
        IrisBiome first = new IrisBiome();
        IrisBiome second = new IrisBiome();
        IrisBiome[] choices = {null, center, first, second};
        AtomicReference<IrisBiome[]> samples = new AtomicReference<>();
        int[] position = new int[3];
        Engine engine = mock(Engine.class);
        doAnswer(call -> {
            int x = call.getArgument(0);
            int z = call.getArgument(2);
            return samples.get()[sampleIndex(x - position[0], z - position[2])];
        }).when(engine).getCaveBiome(anyInt(), anyInt(), anyInt(), any(IrisDimensionCarvingResolver.State.class));
        RNG parent = new RNG(SEED);
        RNG control = new RNG(SEED);
        assertEquals(control.nextInt(), parent.nextInt());
        IrisCarveModifier modifier = modifier(engine, parent);
        for (int sign : new int[]{1, -1}) {
            for (int roll = 0; roll < 8; roll++) {
                int[] coordinate = coordinate(roll, sign);
                System.arraycopy(coordinate, 0, position, 0, position.length);
                for (int pattern = 0; pattern < 256; pattern++) {
                    IrisBiome[] values = {center, choices[pattern & 3], choices[(pattern >> 2) & 3],
                            choices[(pattern >> 4) & 3], choices[(pattern >> 6) & 3]};
                    samples.set(values);
                    IrisBiome selected = roll < 4 ? center : values[roll - 3];
                    assertSame(selected == null ? center : selected,
                            resolve(modifier, coordinate, new Long2ObjectOpenHashMap<>()));
                }
                samples.set(new IrisBiome[]{null, first, second, first, second});
                assertNull(resolve(modifier, coordinate, new Long2ObjectOpenHashMap<>()));
            }
        }
        assertEquals(control.nextLong(), parent.nextLong());
    }

    @Test
    public void onlyCenterAndChosenNeighborAreQueriedInThatOrder() throws Exception {
        List<Long> queries = new ArrayList<>();
        IrisBiome center = new IrisBiome();
        Engine engine = mock(Engine.class);
        doAnswer(call -> {
            queries.add(BlockPosition.toLong(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
            return center;
        }).when(engine).getCaveBiome(anyInt(), anyInt(), anyInt(), any(IrisDimensionCarvingResolver.State.class));
        IrisCarveModifier modifier = modifier(engine, new RNG(SEED));
        for (int roll = 0; roll < 8; roll++) {
            int[] coordinate = coordinate(roll, -1);
            queries.clear();
            assertSame(center, resolve(modifier, coordinate, new Long2ObjectOpenHashMap<>()));
            List<Long> expected = new ArrayList<>();
            expected.add(BlockPosition.toLong(coordinate[0], coordinate[1], coordinate[2]));
            if (roll >= 4) {
                int[] neighbor = neighbor(coordinate, roll - 4);
                expected.add(BlockPosition.toLong(neighbor[0], neighbor[1], neighbor[2]));
            }
            assertEquals(expected, queries);
        }
    }

    @Test
    public void sharedCacheTraversalMatchesEagerSampling() throws Exception {
        IrisBiome[] biomes = {new IrisBiome(), new IrisBiome(), null, new IrisBiome()};
        Engine engine = mock(Engine.class);
        doAnswer(call -> biomeAt(biomes, call.getArgument(0), call.getArgument(1), call.getArgument(2)))
                .when(engine).getCaveBiome(anyInt(), anyInt(), anyInt(), any(IrisDimensionCarvingResolver.State.class));
        IrisCarveModifier modifier = modifier(engine, new RNG(SEED));
        Long2ObjectOpenHashMap<IrisBiome> cache = new Long2ObjectOpenHashMap<>();
        Long2ObjectOpenHashMap<IrisBiome> eagerCache = new Long2ObjectOpenHashMap<>();
        for (int x = -16; x < 0; x++) {
            for (int z = -32; z < -16; z++) {
                for (int y : new int[]{-12, 3, 17, 40}) {
                    int[] coordinate = {x, y, z};
                    assertSame(eager(biomes, coordinate, eagerCache), resolve(modifier, coordinate, cache));
                }
            }
        }
    }

    @Test
    public void selectedNeighborFailureKeepsOriginalCause() throws Exception {
        int[] coordinate = coordinate(4, -1);
        IllegalStateException failure = new IllegalStateException("selected cave query failed");
        Engine engine = mock(Engine.class);
        doAnswer(call -> {
            if ((int) call.getArgument(0) != coordinate[0]) {
                throw failure;
            }
            return new IrisBiome();
        }).when(engine).getCaveBiome(anyInt(), anyInt(), anyInt(), any(IrisDimensionCarvingResolver.State.class));
        IrisCarveModifier modifier = modifier(engine, new RNG(SEED));
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> resolve(modifier, coordinate, new Long2ObjectOpenHashMap<>())));
    }

    private static IrisCarveModifier modifier(Engine engine, RNG rng) throws ReflectiveOperationException {
        IrisCarveModifier modifier = mock(IrisCarveModifier.class, CALLS_REAL_METHODS);
        doReturn(engine).when(modifier).getEngine();
        doReturn(mock(IrisComplex.class)).when(modifier).getComplex();
        Field field = IrisCarveModifier.class.getDeclaredField("rng");
        field.setAccessible(true);
        field.set(modifier, rng);
        return modifier;
    }

    private static IrisBiome resolve(IrisCarveModifier modifier, int[] coordinate,
                                     Long2ObjectOpenHashMap<IrisBiome> cache) {
        return modifier.resolveCaveBoundaryBiome(null, coordinate[0], coordinate[1], coordinate[2],
                new IrisDimensionCarvingResolver.State(), cache, new HashMap<>());
    }

    private static int[] coordinate(int roll, int sign) {
        for (int y = 1; y < 10_000; y++) {
            int[] coordinate = {sign * 31, sign * y, sign * 23};
            if (roll(coordinate) == roll) {
                return coordinate;
            }
        }
        throw new AssertionError("Missing cave blend roll " + roll);
    }

    private static int roll(int[] coordinate) {
        return Math.floorMod(new RNG(SEED).nextParallelRNG(
                BlockPosition.toLong(coordinate[0], coordinate[1], coordinate[2])).nextInt(), 8);
    }

    private static int[] neighbor(int[] coordinate, int index) {
        return new int[]{coordinate[0] + (index == 0 ? 3 : index == 1 ? -3 : 0), coordinate[1],
                coordinate[2] + (index == 2 ? 3 : index == 3 ? -3 : 0)};
    }

    private static int sampleIndex(int x, int z) {
        if (x == 3) {
            return 1;
        }
        if (x == -3) {
            return 2;
        }
        return z == 3 ? 3 : z == -3 ? 4 : 0;
    }

    private static IrisBiome biomeAt(IrisBiome[] biomes, int x, int y, int z) {
        return biomes[Math.floorMod(x * 13 + y * 7 + z * 5, biomes.length)];
    }

    private static IrisBiome eager(IrisBiome[] biomes, int[] coordinate, Long2ObjectOpenHashMap<IrisBiome> cache) {
        IrisBiome center = cached(biomes, coordinate, cache);
        if (center == null) {
            return null;
        }
        IrisBiome[] neighbors = new IrisBiome[4];
        boolean identical = true;
        for (int index = 0; index < neighbors.length; index++) {
            neighbors[index] = cached(biomes, neighbor(coordinate, index), cache);
            identical &= neighbors[index] == center;
        }
        int roll = roll(coordinate);
        IrisBiome selected = identical || roll < 4 ? center : neighbors[roll - 4];
        return selected == null ? center : selected;
    }

    private static IrisBiome cached(IrisBiome[] biomes, int[] coordinate, Long2ObjectOpenHashMap<IrisBiome> cache) {
        long key = BlockPosition.toLong(coordinate[0], coordinate[1], coordinate[2]);
        IrisBiome value = cache.get(key);
        if (value == null) {
            value = biomeAt(biomes, coordinate[0], coordinate[1], coordinate[2]);
            if (value != null) {
                cache.put(key, value);
            }
        }
        return value;
    }
}
