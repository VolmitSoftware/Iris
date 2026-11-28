package art.arcane.iris.generation.biome;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.PackRuntime;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FloatingIslandMemoContextTest {
    @Test
    public void rejectedColumnsAreCachedOnlyForTheirParentContextAndRuntime() {
        IrisBiome first = parent();
        IrisBiome second = parent();
        IrisData data = mock(IrisData.class);
        PackRuntime runtime = PackRuntime.forEngine(mock(Engine.class), data);
        FloatingIslandBoundarySampler boundary = new FloatingIslandBoundarySampler((x, z) -> first);
        sample(first, 320, 7L, runtime, boundary);
        sample(first, 320, 7L, runtime, boundary);
        sample(second, 320, 7L, runtime, boundary);
        verify(first, times(1)).getFloatingChildBiomes();
        verify(second, times(1)).getFloatingChildBiomes();
        sample(first, 320, 9L, runtime, boundary);
        sample(first, 128, 9L, runtime, boundary);
        sample(first, 128, 9L, PackRuntime.forEngine(mock(Engine.class), data), boundary);
        sample(first, 128, 9L, runtime.scopeFor(mock(IrisData.class)), boundary);
        sample(first, 128, 9L, runtime, new FloatingIslandBoundarySampler((x, z) -> first));
        verify(first, times(6)).getFloatingChildBiomes();
    }

    @Test
    public void clearingTheChunkMemoSamplesAgain() {
        IrisBiome parent = parent();
        PackRuntime runtime = PackRuntime.forEngine(mock(Engine.class), mock(IrisData.class));
        FloatingIslandBoundarySampler boundary = new FloatingIslandBoundarySampler((x, z) -> parent);
        sample(parent, 320, 7L, runtime, boundary);
        FloatingIslandSample.clearChunkMemo(runtime);
        sample(parent, 320, 7L, runtime, boundary);
        verify(parent, times(2)).getFloatingChildBiomes();
    }

    private static void sample(IrisBiome parent, int height, long seed, PackRuntime runtime,
                               FloatingIslandBoundarySampler boundary) {
        assertNull(FloatingIslandSample.sampleMemoized(parent, -16, 32, height, seed, runtime, boundary));
    }

    private static IrisBiome parent() {
        IrisBiome parent = mock(IrisBiome.class);
        when(parent.getFloatingChildBiomes()).thenReturn(new KList<>());
        return parent;
    }
}
