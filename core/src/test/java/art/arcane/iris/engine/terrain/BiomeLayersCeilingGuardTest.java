package art.arcane.iris.engine.terrain;

import art.arcane.iris.builtin.terrain.CaveCeilingLayersSettings;
import art.arcane.iris.builtin.terrain.LayersSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.BuiltInPack;
import art.arcane.iris.core.modules.IrisPackResources;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import com.google.gson.Gson;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BiomeLayersCeilingGuardTest {
    private static final String EMPTY_LAYER = "{\"palette\": []}";
    private static final String EMPTY_CEILING_LAYER = "{\"palette\": [], \"minHeight\": 0, \"maxHeight\": 0}";

    private static IrisBiome biome(int layers, int ceilingLayers) {
        Gson gson = new Gson();
        IrisBiome biome = new IrisBiome();
        biome.setModuleSettings(BuiltInPack.settings(
                gson.fromJson("{\"layers\": " + layerList(EMPTY_LAYER, layers) + "}", LayersSettings.class),
                gson.fromJson("{\"layers\": " + layerList(EMPTY_CEILING_LAYER, ceilingLayers) + "}", CaveCeilingLayersSettings.class)));
        return biome;
    }

    private static String layerList(String layer, int count) {
        return "[" + String.join(",", java.util.Collections.nCopies(count, layer)) + "]";
    }

    private static IrisData data() {
        IrisData data = mock(IrisData.class);
        when(data.getDataFolder()).thenReturn(new File("pack"));
        IrisPackResources resources = new IrisPackResources(data);
        when(data.getPackResources()).thenReturn(resources);
        return data;
    }

    @Test
    public void moreCeilingLayersThanLayersMustNotThrow() {
        IrisBiome biome = biome(1, 3);

        KList<PlatformBlockState> result = layersOf(data()).generateCeilingLayers(
                biome, new IrisDimension(), 0, 0, new RNG(1), 8, 64, data(), null);

        assertTrue("extra ceiling layers are skipped, not crashed on", result.isEmpty());
    }

    @Test
    public void inRangeCeilingLayersKeepWorking() {
        IrisBiome biome = biome(3, 2);

        KList<PlatformBlockState> result = layersOf(data()).generateCeilingLayers(
                biome, new IrisDimension(), 0, 0, new RNG(1), 8, 64, data(), null);

        assertTrue("zeroed palettes with zero heights produce no blocks", result.isEmpty());
    }

    @Test
    public void omittedCeilingLayersLeaveCavesUnchanged() {
        KList<PlatformBlockState> result = layersOf(data()).generateCeilingLayers(
                new IrisBiome(), new IrisDimension(), 0, 0, new RNG(1), 8, 64, data(), null);

        assertTrue(result.isEmpty());
    }

    private static BiomeLayers layersOf(IrisData data) {
        return new BiomeLayers(TestLayerBlocks.of(data.getPackResources()));
    }
}
