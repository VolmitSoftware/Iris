package art.arcane.iris.modules.objects;

import art.arcane.iris.builtin.IrisSeedPurposes;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.noise.NoiseSampler;
import com.volmit.resource.PackResources;
import com.volmit.world.PackWorld;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Object placing draws its chunk random and its density from the world's seeds alone, not from any host stream. */
public class ObjectPoolStreamsTest {
    private static final long CHUNK_SEED = 4_242L;
    private static final long DENSITY_SEED = 9_999L;

    private PackResources resources;
    private NoiseSampler halfway;

    @Before
    public void buildResources() {
        resources = mock(PackResources.class, RETURNS_DEEP_STUBS);
        halfway = mock(NoiseSampler.class);
        when(halfway.sample(anyDouble(), anyDouble())).thenReturn(0.5);
        when(resources.noise().sampler(any(), eq(DENSITY_SEED))).thenReturn(halfway);
    }

    @Test
    public void aChunkGetsTheSameRandomEveryTimeFromTheSameWorld() {
        ObjectPool pool = ObjectPool.of(world(CHUNK_SEED));

        assertEquals(pool.chunkRandom(5, -9).nextLong(), ObjectPool.of(world(CHUNK_SEED)).chunkRandom(5, -9).nextLong());
        assertEquals(pool.chunkRandom(5, -9).nextLong(), pool.chunkRandom(5, -9).nextLong());
    }

    @Test
    public void neighbouringChunksAndOtherWorldsGetOtherRandoms() {
        ObjectPool pool = ObjectPool.of(world(CHUNK_SEED));
        RNG here = pool.chunkRandom(5, -9);

        assertNotEquals(here.nextLong(), pool.chunkRandom(6, -9).nextLong());
        assertNotEquals(pool.chunkRandom(5, -9).nextLong(), pool.chunkRandom(5, -8).nextLong());
        assertNotEquals(pool.chunkRandom(5, -9).nextLong(), ObjectPool.of(world(CHUNK_SEED + 1)).chunkRandom(5, -9).nextLong());
    }

    @Test
    public void aPlacementWithoutADensityStyleHasItsFixedDensity() {
        ObjectPlacement fixed = TestSettings.of(ObjectPlacement.class, "{\"place\": [\"rock\"], \"density\": 3}");

        assertEquals(3, ObjectPool.of(world(CHUNK_SEED)).density(fixed, 10, 20));
    }

    @Test
    public void aDensityStylePicksTheCountFromTheWorldsDensityNoise() {
        ObjectPlacement styled = TestSettings.of(ObjectPlacement.class,
                "{\"place\": [\"rock\"], \"density\": 1, \"densityStyle\": {\"min\": 2, \"max\": 6, \"style\": {\"style\": \"SIMPLEX\"}}}");

        assertEquals("halfway between 2 and 6", 4, ObjectPool.of(world(CHUNK_SEED)).density(styled, 10, 20));
        verify(halfway).sample(10, 20);
    }

    @Test
    public void aDensityStyleWithEqualBoundsNeedsNoNoise() {
        ObjectPlacement even = TestSettings.of(ObjectPlacement.class,
                "{\"place\": [\"rock\"], \"densityStyle\": {\"min\": 3, \"max\": 3, \"style\": {\"style\": \"SIMPLEX\"}}}");

        assertEquals(3, ObjectPool.of(world(CHUNK_SEED)).density(even, 10, 20));
        verify(halfway, never()).sample(anyDouble(), anyDouble());
    }

    private PackWorld world(long chunkSeed) {
        PackWorld world = mock(PackWorld.class);
        Map<Class<?>, Object> states = new HashMap<>();
        when(world.resources()).thenReturn(resources);
        when(world.seed(IrisSeedPurposes.OBJECT_CHUNK)).thenReturn(chunkSeed);
        when(world.seed(IrisSeedPurposes.OBJECT_DENSITY)).thenReturn(DENSITY_SEED);
        when(world.state(any(), any())).thenAnswer(invocation -> states.computeIfAbsent(invocation.getArgument(0),
                ignored -> invocation.<Function<PackWorld, Object>>getArgument(1).apply(world)));
        return world;
    }
}
