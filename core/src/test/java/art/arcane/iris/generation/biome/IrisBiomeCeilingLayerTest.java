package art.arcane.iris.generation.biome;

import art.arcane.iris.generation.terrain.IrisDimension;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class IrisBiomeCeilingLayerTest {
    @Test
    public void emitsEveryCeilingLayerWithoutSurfaceLayers() {
        PlatformBlockState first = mock(PlatformBlockState.class);
        PlatformBlockState second = mock(PlatformBlockState.class);
        PlatformBlockState third = mock(PlatformBlockState.class);
        IrisBiome biome = new IrisBiome().setLayers(new KList<>()).setCaveCeilingLayers(
                new KList<>(layer(first, 2), layer(second, 1), layer(third, 2)));

        KList<PlatformBlockState> result = biome.generateCeilingLayers(
                new IrisDimension(), 13, -27, new RNG(1), 8, 64, null, null);

        assertEquals(List.of(first, first, second, third, third), result);
        assertNull(biome.getLayerHeightGenerators().getIfPresent());
    }

    @Test
    public void ceilingThicknessDoesNotDependOnSurfaceDefinitionOrWarmOrder() {
        IrisBiome first = variableBiome(1, 1);
        IrisBiome second = variableBiome(7, 31);
        RNG rng = new RNG(37);
        second.getLayerHeightGenerators(rng, null);
        for (int x = -200; x <= 200; x += 7) {
            KList<PlatformBlockState> firstCeiling = first.generateCeilingLayers(
                    new IrisDimension(), x, -x, rng, 100, 120, null, null);
            KList<PlatformBlockState> secondCeiling = second.generateCeilingLayers(
                    new IrisDimension(), x, -x, rng, 100, 120, null, null);
            assertEquals(firstCeiling.size(), secondCeiling.size());
        }
        first.getLayerHeightGenerators(rng, null);
        assertNotSame(first.getLayerHeightGenerators().getIfPresent().get(0),
                first.getLayerCeilingHeightGenerators().getIfPresent().get(0));
    }

    @Test
    public void capsCeilingLayersAtAvailableDepth() {
        PlatformBlockState first = mock(PlatformBlockState.class);
        PlatformBlockState second = mock(PlatformBlockState.class);
        IrisBiome biome = new IrisBiome().setCaveCeilingLayers(new KList<>(layer(first, 2), layer(second, 3)));
        assertEquals(List.of(first, first, second), biome.generateCeilingLayers(
                new IrisDimension(), 0, 0, new RNG(1), 3, 64, null, null));
        assertTrue(biome.generateCeilingLayers(
                new IrisDimension(), 0, 0, new RNG(1), 0, 64, null, null).isEmpty());
    }

    @Test
    public void omittedCeilingLayersLeaveCavesUnchanged() {
        assertTrue(new IrisBiome().getCaveCeilingLayers().isEmpty());
    }

    static IrisBiomePaletteLayer layer(PlatformBlockState block, int thickness) {
        IrisBiomePaletteLayer layer = new IrisBiomePaletteLayer().setMinHeight(thickness).setMaxHeight(thickness);
        layer.getBlockData().aquire(() -> new KList<>(block));
        return layer;
    }

    private static IrisBiome variableBiome(int surfaceMinimum, int surfaceMaximum) {
        PlatformBlockState block = mock(PlatformBlockState.class);
        return new IrisBiome()
                .setLayers(new KList<>(layer(block, surfaceMinimum).setMaxHeight(surfaceMaximum)))
                .setCaveCeilingLayers(new KList<>(layer(block, 1).setMaxHeight(9)));
    }
}
