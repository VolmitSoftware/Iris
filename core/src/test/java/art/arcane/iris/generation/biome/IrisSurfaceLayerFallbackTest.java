package art.arcane.iris.generation.biome;

import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisSlopeClip;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class IrisSurfaceLayerFallbackTest {
    private final IrisData data = mock(IrisData.class);
    private final IrisBiomePaletteLayer layer = mock(IrisBiomePaletteLayer.class);
    private final CNG heightGenerator = mock(CNG.class);
    private final NativeBlockState surfaceBlock = mock(NativeBlockState.class);
    private final NativeBlockState secondBlock = mock(NativeBlockState.class);
    private final RNG rng = new RNG(19L);

    @SuppressWarnings("unchecked")
    private final ProceduralStream<Double> slopeStream = mock(ProceduralStream.class);

    private IrisBiome biome() {
        when(layer.getZoom()).thenReturn(1D);
        when(layer.getMinHeight()).thenReturn(1);
        when(layer.getMaxHeight()).thenReturn(2);
        when(layer.getHeightGenerator(any(RNG.class), same(data))).thenReturn(heightGenerator);
        when(heightGenerator.fit(1, 2, 12D, -8D)).thenReturn(2);
        when(layer.get(rng, 0, 12D, 0D, -8D, data)).thenReturn(surfaceBlock);
        when(layer.get(rng, 1, 13D, 1D, -9D, data)).thenReturn(secondBlock);
        when(slopeStream.getDouble(12D, -8D)).thenReturn(9D);
        return new IrisBiome().setLayers(new KList<>(layer));
    }

    private KList<NativeBlockState> generate(IrisBiome biome, IrisDimension dim) {
        return biome.generateLayersWithSlope(dim, 12D, -8D, rng, 4, 32, data, slopeStream);
    }

    @Test
    public void allGatedLayersEmitTopLayerBlockUnderTopLayerFallback() {
        IrisBiome biome = biome();
        when(layer.getSlopeCondition()).thenReturn(new IrisSlopeClip(0D, 4D));

        KList<NativeBlockState> blocks = generate(biome,
                new IrisDimension().setSurfaceLayerFallback(IrisSurfaceLayerFallback.TOP_LAYER));

        assertEquals(1, blocks.size());
        assertSame(surfaceBlock, blocks.get(0));
    }

    @Test
    public void allGatedLayersStayEmptyUnderRockFallback() {
        IrisBiome biome = biome();
        when(layer.getSlopeCondition()).thenReturn(new IrisSlopeClip(0D, 4D));

        KList<NativeBlockState> blocks = generate(biome,
                new IrisDimension().setSurfaceLayerFallback(IrisSurfaceLayerFallback.ROCK));

        assertTrue(blocks.isEmpty());
    }

    @Test
    public void ungatedLayersAreIdenticalUnderBothFallbacks() {
        IrisBiome biome = biome();
        when(layer.getSlopeCondition()).thenReturn(new IrisSlopeClip());

        KList<NativeBlockState> topLayer = generate(biome,
                new IrisDimension().setSurfaceLayerFallback(IrisSurfaceLayerFallback.TOP_LAYER));
        KList<NativeBlockState> rock = generate(biome,
                new IrisDimension().setSurfaceLayerFallback(IrisSurfaceLayerFallback.ROCK));

        assertEquals(2, topLayer.size());
        assertSame(surfaceBlock, topLayer.get(0));
        assertSame(secondBlock, topLayer.get(1));
        assertEquals(topLayer, rock);
    }

    @Test
    public void biomeFallbackOverridesDimensionFallbackInBothDirections() {
        IrisBiome pinnedToTopLayer = biome().setSurfaceLayerFallback(IrisSurfaceLayerFallback.TOP_LAYER);
        when(layer.getSlopeCondition()).thenReturn(new IrisSlopeClip(0D, 4D));

        KList<NativeBlockState> overRock = generate(pinnedToTopLayer,
                new IrisDimension().setSurfaceLayerFallback(IrisSurfaceLayerFallback.ROCK));

        assertEquals(1, overRock.size());
        assertSame(surfaceBlock, overRock.get(0));

        IrisBiome pinnedToRock = biome().setSurfaceLayerFallback(IrisSurfaceLayerFallback.ROCK);

        KList<NativeBlockState> overTopLayer = generate(pinnedToRock,
                new IrisDimension().setSurfaceLayerFallback(IrisSurfaceLayerFallback.TOP_LAYER));

        assertTrue(overTopLayer.isEmpty());
    }

    @Test
    public void lockedLayersHonorInheritedAndOverriddenFallbacks() {
        IrisBiome biome = biome().setLockLayers(true);
        when(layer.getSlopeCondition()).thenReturn(new IrisSlopeClip(0D, 4D));
        IrisDimension dimension = new IrisDimension().setSurfaceLayerFallback(IrisSurfaceLayerFallback.TOP_LAYER);

        KList<NativeBlockState> blocks = generate(biome, dimension);

        assertEquals(1, blocks.size());
        assertSame(surfaceBlock, blocks.get(0));
        assertTrue(generate(biome.setSurfaceLayerFallback(IrisSurfaceLayerFallback.ROCK), dimension).isEmpty());
        assertEquals(blocks, generate(biome.setSurfaceLayerFallback(IrisSurfaceLayerFallback.TOP_LAYER),
                dimension.setSurfaceLayerFallback(IrisSurfaceLayerFallback.ROCK)));
    }

    @Test
    public void lockedFallbackRespectsEmptyLayersAndDepthLimits() {
        IrisBiome biome = biome().setLockLayers(true).setSurfaceLayerFallback(IrisSurfaceLayerFallback.TOP_LAYER);
        when(layer.getSlopeCondition()).thenReturn(new IrisSlopeClip(0D, 4D));
        IrisDimension dimension = new IrisDimension();

        assertTrue(biome.generateLayersWithSlope(dimension, 12D, -8D, rng, 0, 32, data, slopeStream).isEmpty());
        assertTrue(generate(biome.setLockLayersMax(0), dimension).isEmpty());
        assertTrue(generate(biome.setLockLayersMax(7).setLayers(new KList<>()), dimension).isEmpty());
    }

    @Test
    public void dimensionDefaultsToRockFallback() {
        assertEquals(IrisSurfaceLayerFallback.ROCK, new IrisDimension().getSurfaceLayerFallback());
    }
}
