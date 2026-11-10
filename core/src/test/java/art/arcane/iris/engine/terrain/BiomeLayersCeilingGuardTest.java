package art.arcane.iris.engine.terrain;

import art.arcane.iris.modules.terrain.BiomeLayers;
import art.arcane.iris.modules.terrain.LayeredBiome;
import art.arcane.iris.modules.terrain.PaletteExplosion;
import art.arcane.iris.modules.terrain.CaveCeilingLayersSettings;
import art.arcane.iris.builtin.terrain.LayersSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.IrisPackResources;
import com.volmit.block.BlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import com.google.gson.Gson;
import com.volmit.host.ModuleLogs;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BiomeLayersCeilingGuardTest {
    private static final String EMPTY_LAYER = "{\"palette\": []}";
    private static final String EMPTY_CEILING_LAYER = "{\"palette\": [], \"minHeight\": 0, \"maxHeight\": 0}";

    private static LayeredBiome biome(int layers, int ceilingLayers) {
        Gson gson = new Gson();
        return new LayeredBiome(
                gson.fromJson("{\"layers\": " + layerList(EMPTY_LAYER, layers) + "}", LayersSettings.class), null,
                gson.fromJson("{\"layers\": " + layerList(EMPTY_CEILING_LAYER, ceilingLayers) + "}", CaveCeilingLayersSettings.class), null, null, null);
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
        LayeredBiome biome = biome(1, 3);

        KList<BlockState> result = layersOf(data()).generateCeilingLayers(
                biome, PaletteExplosion.NONE, 0, 0, new RNG(1), 8);

        assertTrue("zeroed palettes with zero heights produce no blocks", result.isEmpty());
    }

    @Test
    public void inRangeCeilingLayersKeepWorking() {
        LayeredBiome biome = biome(3, 2);

        KList<BlockState> result = layersOf(data()).generateCeilingLayers(
                biome, PaletteExplosion.NONE, 0, 0, new RNG(1), 8);

        assertTrue("zeroed palettes with zero heights produce no blocks", result.isEmpty());
    }

    @Test
    public void omittedCeilingLayersLeaveCavesUnchanged() {
        KList<BlockState> result = layersOf(data()).generateCeilingLayers(
                LayeredBiome.UNDECLARED, PaletteExplosion.NONE, 0, 0, new RNG(1), 8);

        assertTrue(result.isEmpty());
    }

    private static BiomeLayers layersOf(IrisData data) {
        return new BiomeLayers(TestLayerBlocks.of(data.getPackResources()), () -> data.getPackResources().noise(), ModuleLogs.discarding());
    }
}
