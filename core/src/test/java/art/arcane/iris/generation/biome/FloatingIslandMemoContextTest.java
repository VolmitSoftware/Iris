package art.arcane.iris.generation.biome;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.collection.KList;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FloatingIslandMemoContextTest {
    @After
    public void clearMemo() {
        FloatingIslandSample.clearThreadCaches();
    }

    @Test
    public void rejectedColumnsAreCachedOnlyForTheirParentAndContext() {
        FloatingIslandSample.clearThreadCaches();
        IrisBiome first = parent();
        IrisBiome second = parent();
        IrisData data = mock(IrisData.class);
        Engine engine = mock(Engine.class);
        FloatingIslandBoundarySampler boundary = new FloatingIslandBoundarySampler((x, z) -> first);
        sample(first, 320, 7L, data, engine, boundary);
        sample(first, 320, 7L, data, engine, boundary);
        sample(second, 320, 7L, data, engine, boundary);
        verify(first, times(1)).getFloatingChildBiomes();
        verify(second, times(1)).getFloatingChildBiomes();
        sample(first, 320, 9L, data, engine, boundary);
        sample(first, 128, 9L, data, engine, boundary);
        sample(first, 128, 9L, data, mock(Engine.class), boundary);
        sample(first, 128, 9L, mock(IrisData.class), engine, boundary);
        sample(first, 128, 9L, data, engine, new FloatingIslandBoundarySampler((x, z) -> first));
        verify(first, times(6)).getFloatingChildBiomes();
    }

    private static void sample(IrisBiome parent, int height, long seed, IrisData data, Engine engine,
                               FloatingIslandBoundarySampler boundary) {
        assertNull(FloatingIslandSample.sampleMemoized(parent, -16, 32, height, seed, data, engine, boundary));
    }

    private static IrisBiome parent() {
        IrisBiome parent = mock(IrisBiome.class);
        when(parent.getFloatingChildBiomes()).thenReturn(new KList<>());
        return parent;
    }
}
