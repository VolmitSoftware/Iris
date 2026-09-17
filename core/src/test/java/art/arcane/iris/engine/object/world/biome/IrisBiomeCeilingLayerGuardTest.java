package art.arcane.iris.engine.object.world.biome;

import art.arcane.iris.engine.object.terrain.IrisBiomePaletteLayer;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class IrisBiomeCeilingLayerGuardTest {
    private static IrisBiome biome(int layers, int ceilingLayers) {
        IrisBiome biome = new IrisBiome();
        KList<IrisBiomePaletteLayer> surface = new KList<>();
        for (int i = 0; i < layers; i++) {
            surface.add(new IrisBiomePaletteLayer().zero());
        }
        KList<IrisBiomePaletteLayer> ceiling = new KList<>();
        for (int i = 0; i < ceilingLayers; i++) {
            IrisBiomePaletteLayer layer = new IrisBiomePaletteLayer().zero();
            layer.setMinHeight(0);
            layer.setMaxHeight(0);
            ceiling.add(layer);
        }
        biome.setLayers(surface);
        biome.setCaveCeilingLayers(ceiling);
        return biome;
    }

    @Test
    public void moreCeilingLayersThanLayersMustNotThrow() {
        IrisBiome biome = biome(1, 3);

        KList<PlatformBlockState> result = biome.generateCeilingLayers(
                new IrisDimension(), 0, 0, new RNG(1), 8, 64, mock(IrisData.class), null);

        assertTrue("extra ceiling layers are skipped, not crashed on", result.isEmpty());
    }

    @Test
    public void inRangeCeilingLayersKeepWorking() {
        IrisBiome biome = biome(3, 2);

        KList<PlatformBlockState> result = biome.generateCeilingLayers(
                new IrisDimension(), 0, 0, new RNG(1), 8, 64, mock(IrisData.class), null);

        assertTrue("zeroed palettes with zero heights produce no blocks", result.isEmpty());
    }

    @Test
    public void omittedCeilingLayersLeaveCavesUnchanged() {
        assertTrue(new IrisBiome().getCaveCeilingLayers().isEmpty());
    }
}
