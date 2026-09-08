package art.arcane.iris.engine.object;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public final class IrisBiomeLayerSlopeSourceTest {
    @Parameters(name = "lockLayers={0}")
    public static Collection<Object[]> layerLocks() {
        return List.of(
                new Object[]{false},
                new Object[]{true}
        );
    }

    private final boolean lockLayers;

    public IrisBiomeLayerSlopeSourceTest(boolean lockLayers) {
        this.lockLayers = lockLayers;
    }

    @Test
    public void lowerLedgeSlopeControlsSurfacePaletteSelection() {
        IrisData data = mock(IrisData.class);
        IrisComplex complex = mock(IrisComplex.class);
        IrisBiomePaletteLayer grass = mock(IrisBiomePaletteLayer.class);
        CNG heightGenerator = mock(CNG.class);
        PlatformBlockState block = mock(PlatformBlockState.class);
        RNG rng = new RNG(19L);
        IrisBiome biome = new IrisBiome().setLockLayers(lockLayers).setLayers(new KList<>(grass));
        when(complex.hasTerrain3D()).thenReturn(true);
        when(complex.terrainSurfaceSlope(12, 32, -8)).thenReturn(0D);
        when(complex.terrainSurfaceSlope(12, 96, -8)).thenReturn(5D);
        when(grass.getZoom()).thenReturn(1D);
        when(grass.getMinHeight()).thenReturn(1);
        when(grass.getMaxHeight()).thenReturn(1);
        when(grass.getSlopeCondition()).thenReturn(new IrisSlopeClip(0D, 2.6D));
        when(grass.getHeightGenerator(any(RNG.class), same(data))).thenReturn(heightGenerator);
        when(heightGenerator.fit(1, 1, 12D, -8D)).thenReturn(1);
        when(grass.get(rng, 0, 12D, 0D, -8D, data)).thenReturn(block);

        KList<PlatformBlockState> ledge = biome.generateLayers(
                new IrisDimension(), 12D, -8D, rng, 1, 32, data, complex);
        KList<PlatformBlockState> cap = biome.generateLayers(
                new IrisDimension(), 12D, -8D, rng, 1, 96, data, complex);

        assertEquals(1, ledge.size());
        assertSame(block, ledge.get(0));
        assertTrue(cap.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void explicitSlopeStreamControlsSurfacePaletteSelection() {
        IrisData data = mock(IrisData.class);
        IrisComplex hostComplex = mock(IrisComplex.class);
        ProceduralStream<Double> hostSlope = mock(ProceduralStream.class);
        ProceduralStream<Double> sourceSlope = mock(ProceduralStream.class);
        IrisBiomePaletteLayer paletteLayer = mock(IrisBiomePaletteLayer.class);
        CNG heightGenerator = mock(CNG.class);
        PlatformBlockState surfaceBlock = mock(PlatformBlockState.class);
        RNG rng = new RNG(19L);

        IrisBiome biome = new IrisBiome();
        biome.setLockLayers(lockLayers);
        KList<IrisBiomePaletteLayer> paletteLayers = new KList<>();
        paletteLayers.add(paletteLayer);
        biome.setLayers(paletteLayers);

        when(hostComplex.getSlopeStream()).thenReturn(hostSlope);
        when(hostSlope.getDouble(12D, -8D)).thenReturn(0D);
        when(sourceSlope.getDouble(12D, -8D)).thenReturn(5D);
        when(paletteLayer.getZoom()).thenReturn(1D);
        when(paletteLayer.getMinHeight()).thenReturn(1);
        when(paletteLayer.getMaxHeight()).thenReturn(1);
        when(paletteLayer.getSlopeCondition()).thenReturn(new IrisSlopeClip(5D, 5D));
        when(paletteLayer.getHeightGenerator(any(RNG.class), same(data))).thenReturn(heightGenerator);
        when(heightGenerator.fit(1, 1, 12D, -8D)).thenReturn(1);
        when(paletteLayer.get(rng, 0, 12D, 0D, -8D, data)).thenReturn(surfaceBlock);

        KList<PlatformBlockState> hostLayers = biome.generateLayers(
                new IrisDimension(), 12D, -8D, rng, 1, 32, data, hostComplex);
        KList<PlatformBlockState> sourceLayers = biome.generateLayersWithSlope(
                new IrisDimension(), 12D, -8D, rng, 1, 32, data, sourceSlope);

        assertTrue(hostLayers.isEmpty());
        assertEquals(1, sourceLayers.size());
        assertSame(surfaceBlock, sourceLayers.get(0));
    }
}
