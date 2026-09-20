package art.arcane.iris.generation.biome;

import art.arcane.iris.generation.noise.NoiseStyle;
import art.arcane.iris.generation.terrain.IrisSlopeClip;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class IrisBiomeLockedLayerTest {
    @Test
    public void repeatsLockedLayersAcrossTheReferenceHeight() {
        NativeBlockState first = mock(NativeBlockState.class);
        NativeBlockState second = mock(NativeBlockState.class);
        NativeBlockState third = mock(NativeBlockState.class);
        IrisBiome biome = new IrisBiome().setLockLayers(true).setLockLayersMax(8).setLayers(new KList<>(
                IrisBiomeCeilingLayerTest.layer(first, 1),
                IrisBiomeCeilingLayerTest.layer(second, 1),
                IrisBiomeCeilingLayerTest.layer(third, 1)));
        List<NativeBlockState> cycle = List.of(first, second, third);
        int[] heights = {1, 509, 512, 513, 1000, Integer.MAX_VALUE};
        int[][] expectedCycles = {{1, 0, 2}, {0, 2, 1}, {0, 2, 1}, {2, 1, 0}, {1, 0, 2}, {1, 0, 2}};
        for (int index = 0; index < heights.length; index++) {
            KList<NativeBlockState> blocks = biome.generateLockedLayers(
                    13, -27, new RNG(37), 8, heights[index], null, null);
            assertEquals(8, blocks.size());
            for (int depth = 0; depth < blocks.size(); depth++) {
                assertEquals(cycle.get(expectedCycles[index][depth % 3]), blocks.get(depth));
            }
        }
    }

    @Test
    public void sampledBandsMatchExpandedVariableMixedPalettes() {
        NativeBlockState first = mock(NativeBlockState.class);
        NativeBlockState second = mock(NativeBlockState.class);
        NativeBlockState third = mock(NativeBlockState.class);
        long[] seeds = {1L, 37L, -291L, Long.MAX_VALUE};
        int[] heights = {Integer.MIN_VALUE, -1000, -1, 0, 509, 512, 513, 1000, Integer.MAX_VALUE};
        int[] depths = {0, 1, 7, 39};
        double[] coordinates = {-71.5D, -1D, 0D, 13.25D, 127D};
        for (long seed : seeds) {
            RNG rng = new RNG(seed);
            IrisBiome biome = new IrisBiome().setLockLayers(true).setLockLayersMax(31).setLayers(new KList<>(
                    palette(0, 0, 1D, first),
                    palette(2, 5, 0.6D, first, second, second),
                    palette(0, 3, 3D, third, first),
                    palette(0, 0, 1D, second),
                    palette(1, 4, 1.5D, second, third, first, third),
                    palette(0, 0, 1D, third)));
            for (double x : coordinates) {
                for (int height : heights) {
                    for (int depth : depths) {
                        assertEquals(expandedBands(biome, x, -x, rng, depth, height, 0D),
                                biome.generateLockedLayers(x, -x, rng, depth, height, null, null));
                    }
                }
            }
        }
    }

    @Test
    public void nullEntriesAndRepeatedCyclesMatchExpandedBands() {
        NativeBlockState block = mock(NativeBlockState.class);
        IrisBiome biome = new IrisBiome().setLockLayers(true).setLockLayersMax(30).setLayers(new KList<>(
                palette(1, 1, 1D, block), palette(2, 2, 1D, (NativeBlockState) null)));
        RNG rng = new RNG(37L);
        assertEquals(expandedBands(biome, 3D, -11D, rng, 30, 1000, 0D),
                biome.generateLockedLayers(3D, -11D, rng, 30, 1000, null, null));
    }

    @Test
    public void zeroThicknessAndEmptyPalettesProduceNoBands() {
        IrisBiome biome = new IrisBiome().setLockLayers(true).setLayers(new KList<>());
        RNG rng = new RNG(37L);
        assertTrue(biome.generateLockedLayers(0D, 0D, rng, 7, 64, null, null).isEmpty());
        biome.setLayers(new KList<>(palette(0, 0, 1D, mock(NativeBlockState.class))));
        assertTrue(biome.generateLockedLayers(0D, 0D, rng, 7, 64, null, null).isEmpty());
        assertTrue(biome.generateLockedLayers(0D, 0D, rng, -1, 64, null, null).isEmpty());
    }

    @Test
    public void resolvesOnlyRequestedPositionsOfLongBands() {
        IrisBiomePaletteLayer layer = spy(palette(1000, 1000, 0.6D,
                mock(NativeBlockState.class), mock(NativeBlockState.class)));
        IrisBiome biome = new IrisBiome().setLockLayers(true).setLayers(new KList<>(layer));
        RNG rng = new RNG(37L);
        assertEquals(7, biome.generateLockedLayers(13D, -27D, rng, 7, 64, null, null).size());
        verify(layer, times(7)).get(same(rng), anyInt(), anyDouble(), anyDouble(), anyDouble(), isNull());
    }

    @Test
    public void repeatedCyclesResolveEachPositionOnce() {
        IrisBiomePaletteLayer layer = spy(palette(3, 3, 0.6D,
                mock(NativeBlockState.class), mock(NativeBlockState.class)));
        IrisBiome biome = new IrisBiome().setLockLayers(true).setLockLayersMax(100).setLayers(new KList<>(layer));
        RNG rng = new RNG(37L);
        assertEquals(100, biome.generateLockedLayers(13D, -27D, rng, 100, 64, null, null).size());
        verify(layer, times(3)).get(same(rng), anyInt(), anyDouble(), anyDouble(), anyDouble(), isNull());
    }

    @Test
    public void slopeFilteredBandsMatchExpandedCycle() {
        IrisBiome biome = new IrisBiome().setLockLayers(true).setLockLayersMax(30).setLayers(new KList<>(
                palette(2, 4, 0.6D, mock(NativeBlockState.class)),
                palette(3, 8, 1D, mock(NativeBlockState.class)).setSlopeCondition(new IrisSlopeClip(3D, 255D)),
                palette(1, 5, 2D, mock(NativeBlockState.class)).setSlopeCondition(new IrisSlopeClip(0D, 3D))));
        RNG rng = new RNG(37L);
        double[] slopes = {0D, 3D, 8D};
        for (double slope : slopes) {
            ProceduralStream<Double> slopeStream = ProceduralStream.ofDouble((x, z) -> slope);
            assertEquals(expandedBands(biome, 13D, -27D, rng, 30, 64, slope),
                    biome.generateLayersWithSlope(null, 13D, -27D, rng, 30, 64, null, slopeStream));
        }
    }

    @Test
    public void sampledPaletteFailuresAreReported() {
        IrisBiomePaletteLayer layer = spy(palette(1, 1, 1D, mock(NativeBlockState.class)));
        IrisBiome biome = new IrisBiome().setLockLayers(true).setLayers(new KList<>(layer));
        RNG rng = new RNG(37L);
        IllegalStateException failure = new IllegalStateException("Palette unavailable");
        doThrow(failure).when(layer).get(same(rng), anyInt(), anyDouble(), anyDouble(), anyDouble(), isNull());
        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            assertTrue(biome.generateLockedLayers(0D, 0D, rng, 7, 64, null, null).isEmpty());
            logging.verify(() -> IrisLogging.reportError(failure));
        }
    }

    private static IrisBiomePaletteLayer palette(int minimum, int maximum, double zoom, NativeBlockState... blocks) {
        IrisBiomePaletteLayer layer = new IrisBiomePaletteLayer().setMinHeight(minimum).setMaxHeight(maximum)
                .setZoom(zoom).setStyle(NoiseStyle.NOWHERE.style());
        layer.getBlockData().aquire(() -> new KList<>(blocks));
        return layer;
    }

    private static KList<NativeBlockState> expandedBands(IrisBiome biome, double x, double z, RNG rng,
                                                          int maxDepth, int height, double slope) {
        KList<NativeBlockState> expanded = new KList<>();
        KList<NativeBlockState> result = new KList<>();
        KList<CNG> heights = biome.getLayerHeightGenerators(rng, null);
        for (int index = 0; index < biome.getLayers().size(); index++) {
            IrisBiomePaletteLayer layer = biome.getLayers().get(index);
            double zoom = layer.getZoom();
            int thickness = heights.get(index).fit(layer.getMinHeight(), layer.getMaxHeight(), x / zoom, z / zoom);
            if (!layer.getSlopeCondition().isValid(slope)) {
                continue;
            }
            for (int offset = 0; offset < thickness; offset++) {
                expanded.add(layer.get(rng, index + offset, (x + offset) / zoom, offset, (z - offset) / zoom, null));
            }
        }
        if (!expanded.isEmpty()) {
            for (int depth = 0; depth < Math.min(maxDepth, biome.getLockLayersMax()); depth++) {
                result.add(expanded.get(Math.floorMod(512L - height - depth, expanded.size())));
            }
        }
        return result;
    }

}
