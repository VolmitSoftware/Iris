package art.arcane.iris.engine.object;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class IrisBiomeLockedLayerTest {
    @Test
    public void repeatsLockedLayersAcrossTheReferenceHeight() {
        PlatformBlockState first = mock(PlatformBlockState.class);
        PlatformBlockState second = mock(PlatformBlockState.class);
        PlatformBlockState third = mock(PlatformBlockState.class);
        IrisBiome biome = new IrisBiome().setLockLayers(true).setLockLayersMax(8).setLayers(new KList<>(
                IrisBiomeCeilingLayerTest.layer(first, 1),
                IrisBiomeCeilingLayerTest.layer(second, 1),
                IrisBiomeCeilingLayerTest.layer(third, 1)));
        List<PlatformBlockState> cycle = List.of(first, second, third);
        int[] heights = {1, 509, 512, 513, 1000, Integer.MAX_VALUE};
        int[][] expectedCycles = {{1, 0, 2}, {0, 2, 1}, {0, 2, 1}, {2, 1, 0}, {1, 0, 2}, {1, 0, 2}};
        for (int index = 0; index < heights.length; index++) {
            KList<PlatformBlockState> blocks = biome.generateLockedLayers(
                    13, -27, new RNG(37), 8, heights[index], null, null);
            assertEquals(8, blocks.size());
            for (int depth = 0; depth < blocks.size(); depth++) {
                assertEquals(cycle.get(expectedCycles[index][depth % 3]), blocks.get(depth));
            }
        }
    }
}
