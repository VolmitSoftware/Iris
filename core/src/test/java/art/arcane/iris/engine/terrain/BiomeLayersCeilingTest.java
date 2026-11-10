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
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BiomeLayersCeilingTest {
    private static final int CEILING_DEPTH = 8;
    private static final int RNG_SEED = 37;
    private static final int COLUMN_SPAN = 200;
    private static final int COLUMN_STEP = 7;
    private static final String CEILING = """
            "caveCeilingLayers": [
              {"minHeight": 2, "maxHeight": 2, "palette": [{"block": "moss_block"}]},
              {"minHeight": 1, "maxHeight": 1, "palette": [{"block": "calcite"}]},
              {"minHeight": 2, "maxHeight": 2, "palette": [{"block": "basalt"}]}
            ]""";
    private static final String VARIABLE_CEILING = """
            "caveCeilingLayers": [
              {"minHeight": 0, "maxHeight": 6, "zoom": 0.3, "style": {"style": "SIMPLEX", "zoom": 1}, "palette": [{"block": "moss_block"}]},
              {"minHeight": 0, "maxHeight": 6, "palette": [{"block": "calcite"}]}
            ]""";

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
    public void emitsEveryCeilingLayerBeyondTheSurfaceLayers() {
        LayeredBiome biome = biome("{" + CEILING + "}");

        KList<BlockState> blocks = layers().generateCeilingLayers(biome, PaletteExplosion.NONE, 13, -27, new RNG(RNG_SEED), CEILING_DEPTH);

        assertEquals(List.of("moss_block", "moss_block", "calcite", "basalt", "basalt"), keys(blocks));
    }

    @Test
    public void ceilingLayerBlockPicksWhatGenerationStacks() {
        LayeredBiome biome = biome("{" + CEILING + "}");
        BiomeLayers layers = layers();

        List<String> picked = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            for (int depth = 0; depth < 2; depth++) {
                BlockState block = layers.ceilingLayerBlock(biome, index, depth, 13, -27, new RNG(RNG_SEED));
                if (block != null) {
                    picked.add(key(block));
                }
            }
        }

        assertEquals(List.of("moss_block", "moss_block", "calcite", "basalt", "basalt"), picked);
    }

    @Test
    public void ceilingThicknessDoesNotDependOnSurfaceLayers() {
        LayeredBiome bare = biome("{" + VARIABLE_CEILING + "}");
        LayeredBiome layered = biome("""
                {"layers": [
                   {"minHeight": 1, "maxHeight": 3, "zoom": 5, "style": {"style": "PERLIN", "zoom": 1}, "palette": [{"block": "dirt"}]},
                   {"minHeight": 1, "maxHeight": 3, "zoom": 9, "style": {"style": "CELLULAR", "zoom": 1}, "palette": [{"block": "stone"}]}],
                 %s}""".formatted(VARIABLE_CEILING));
        BiomeLayers bareLayers = layers();
        BiomeLayers layeredLayers = layers();

        for (int x = -COLUMN_SPAN; x <= COLUMN_SPAN; x += COLUMN_STEP) {
            RNG rng = new RNG(RNG_SEED);
            assertEquals("column " + x,
                    keys(bareLayers.generateCeilingLayers(bare, PaletteExplosion.NONE, x, -x, rng, CEILING_DEPTH)),
                    keys(layeredLayers.generateCeilingLayers(layered, PaletteExplosion.NONE, x, -x, rng, CEILING_DEPTH)));
        }
    }

    private LayeredBiome biome(String json) {
        return TestLayeredBiomes.of(TerrainTestSettings.of(json));
    }

    private BiomeLayers layers() {
        return new BiomeLayers(TestLayerBlocks.of(data.getPackResources()), () -> data.getPackResources().noise(), ModuleLogs.discarding());
    }

    private static List<String> keys(KList<BlockState> blocks) {
        List<String> keys = new ArrayList<>();
        for (BlockState block : blocks) {
            keys.add(key(block));
        }
        return keys;
    }

    private static String key(BlockState block) {
        return block.key().replace("minecraft:", "");
    }
}
