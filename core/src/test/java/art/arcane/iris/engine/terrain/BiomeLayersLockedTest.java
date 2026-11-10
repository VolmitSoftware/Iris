package art.arcane.iris.engine.terrain;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.core.modules.IrisPackResources;
import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.object.material.IrisBlockData;
import art.arcane.iris.modules.terrain.BiomeLayers;
import art.arcane.iris.modules.terrain.LayeredBiome;
import art.arcane.iris.modules.terrain.PaletteExplosion;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.block.BlockState;
import com.volmit.host.ModuleLogs;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BiomeLayersLockedTest {
    private static final int LOCKED_DEPTH = 8;
    private static final String LOCKED_STRATA = """
            {"lockLayers": true, "lockLayersMax": 8,
             "layers": [
               {"minHeight": 1, "maxHeight": 1, "palette": [{"block": "red_sand"}]},
               {"minHeight": 1, "maxHeight": 1, "palette": [{"block": "terracotta"}]},
               {"minHeight": 1, "maxHeight": 1, "palette": [{"block": "gravel"}]}
             ]}
            """;
    private static final List<String> STRATA = List.of("red_sand", "terracotta", "gravel");
    private static final int[] HEIGHTS = {1, 509, 512, 513, 1000, Integer.MAX_VALUE};
    private static final int[][] STRATA_FROM_TOP_AT_EACH_HEIGHT = {{1, 0, 2}, {0, 2, 1}, {0, 2, 1}, {2, 1, 0}, {1, 0, 2}, {1, 0, 2}};

    private IrisData data;

    @Before
    public void bindPlatform() {
        UsableBlockPlatform.bind();
        data = mock(IrisData.class);
        @SuppressWarnings("unchecked")
        ResourceLoader<IrisBlockData> blocks = mock(ResourceLoader.class);
        when(data.getBlockLoader()).thenReturn(blocks);
        when(data.getDataFolder()).thenReturn(new File("pack"));
        IrisPackResources resources = new IrisPackResources(data);
        when(data.getPackResources()).thenReturn(resources);
    }

    @After
    public void unbindPlatform() {
        UsableBlockPlatform.unbind();
    }

    @Test
    public void lockedStrataRepeatAcrossTheReferenceHeight() {
        LayeredBiome biome = TestLayeredBiomes.of(TerrainTestSettings.of(LOCKED_STRATA));
        BiomeLayers layers = new BiomeLayers(TestLayerBlocks.of(data.getPackResources()), () -> data.getPackResources().noise(), ModuleLogs.discarding());

        for (int heightIndex = 0; heightIndex < HEIGHTS.length; heightIndex++) {
            KList<BlockState> blocks = layers.generateLayers(biome, PaletteExplosion.NONE, 13, -27, new RNG(37), LOCKED_DEPTH, HEIGHTS[heightIndex], (x, z) -> 0);

            assertEquals(LOCKED_DEPTH, blocks.size());
            for (int depth = 0; depth < blocks.size(); depth++) {
                String expected = STRATA.get(STRATA_FROM_TOP_AT_EACH_HEIGHT[heightIndex][depth % STRATA.size()]);
                assertEquals("height " + HEIGHTS[heightIndex] + " depth " + depth, expected, blocks.get(depth).key().replace("minecraft:", ""));
            }
        }
    }
}
