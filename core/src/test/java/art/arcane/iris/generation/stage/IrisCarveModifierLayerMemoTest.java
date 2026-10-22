package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomePaletteLayer;
import art.arcane.iris.generation.biome.IrisSurfaceLayerFallback;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisSlopeClip;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisCarveModifierLayerMemoTest {
    private static final Map<Integer, NativeBlockState> BLOCKS = new HashMap<>();

    @Test
    public void heightFreeFloorLayersIgnoreTheZoneFloor() {
        IrisData data = mock(IrisData.class);
        IrisComplex complex = mock(IrisComplex.class);
        when(complex.hasTerrain3D()).thenReturn(true);
        when(complex.terrainSurfaceSlope(anyInt(), anyInt(), anyInt())).thenAnswer(call -> (double) (int) call.getArgument(1));
        RNG rng = new RNG(31L);
        IrisBiomePaletteLayer top = layer(data, 2, new IrisSlopeClip());
        IrisBiomePaletteLayer under = layer(data, 3, new IrisSlopeClip());
        IrisBiome biome = new IrisBiome().setLayers(new KList<IrisBiomePaletteLayer>().qadd(top).qadd(under));
        IrisDimension dimension = new IrisDimension().setSurfaceLayerFallback(IrisSurfaceLayerFallback.TOP_LAYER);

        assertTrue(IrisCarveModifier.hasHeightFreeFloorLayers(biome));
        KList<NativeBlockState> reference = biome.generateLayers(dimension, 14D, -9D, rng, 3, 0, data, complex);
        for (int floor = -64; floor < 320; floor += 7) {
            assertEquals(reference, biome.generateLayers(dimension, 14D, -9D, rng, 3, floor, data, complex));
        }

        biome.setLockLayers(true);
        assertFalse(IrisCarveModifier.hasHeightFreeFloorLayers(biome));
        biome.setLockLayers(false);
        biome.setLayers(new KList<IrisBiomePaletteLayer>().qadd(top).qadd(layer(data, 1, new IrisSlopeClip(0D, 2D))));
        assertFalse(IrisCarveModifier.hasHeightFreeFloorLayers(biome));
    }

    @Test
    public void memoAnswersOnlyTheBiomesOfTheCurrentColumn() {
        CaveLayerMemo memo = new CaveLayerMemo();
        IrisBiome first = new IrisBiome();
        IrisBiome second = new IrisBiome();
        KList<NativeBlockState> floor = new KList<>();
        KList<NativeBlockState> ceiling = new KList<>();
        memo.rememberFloor(first, floor);
        memo.rememberCeiling(second, ceiling);

        assertSame(floor, memo.floor(first));
        assertNull(memo.floor(second));
        assertSame(ceiling, memo.ceiling(second));
        assertNull(memo.ceiling(first));
        memo.reset();
        assertNull(memo.floor(first));
        assertNull(memo.ceiling(second));
    }

    private static IrisBiomePaletteLayer layer(IrisData data, int thickness, IrisSlopeClip slope) {
        IrisBiomePaletteLayer layer = mock(IrisBiomePaletteLayer.class);
        CNG height = mock(CNG.class);
        when(layer.getZoom()).thenReturn(1D);
        when(layer.getMinHeight()).thenReturn(thickness);
        when(layer.getMaxHeight()).thenReturn(thickness);
        when(layer.getSlopeCondition()).thenReturn(slope);
        when(layer.getHeightGenerator(any(RNG.class), same(data))).thenReturn(height);
        when(height.fit(anyInt(), anyInt(), anyDouble(), anyDouble())).thenReturn(thickness);
        when(layer.get(any(RNG.class), anyInt(), anyDouble(), anyDouble(), anyDouble(), same(data)))
                .thenAnswer(call -> block((int) call.getArgument(1)));
        return layer;
    }

    private static NativeBlockState block(int signature) {
        return BLOCKS.computeIfAbsent(signature, key -> mock(NativeBlockState.class));
    }
}
