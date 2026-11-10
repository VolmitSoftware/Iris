package art.arcane.iris.engine.actuator;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.core.modules.IrisPackResources;
import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.object.material.IrisBlockData;
import art.arcane.iris.core.modules.ModuleSettings;
import art.arcane.iris.builtin.terrain.PaletteLayer;
import art.arcane.iris.modules.terrain.SlabSettings;
import art.arcane.iris.modules.terrain.BiomeLayers;
import art.arcane.iris.modules.terrain.ColumnSlope;
import art.arcane.iris.modules.terrain.LayeredBiome;
import art.arcane.iris.modules.terrain.PaletteExplosion;
import art.arcane.iris.modules.terrain.ResolvedLayer;
import art.arcane.iris.engine.terrain.TerrainTestSettings;
import art.arcane.iris.engine.terrain.TestLayerBlocks;
import art.arcane.iris.engine.terrain.TestLayeredBiomes;
import com.volmit.block.BlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.host.ModuleLogs;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins what biome layers, sea layers, cave ceiling layers, locked layers, slabs and walls pick for a spread of columns
 * with a fixed terrain RNG, so they can move out of {@code IrisBiome} and into the terrain module without changing what
 * a world generates. The transcript hash was taken from {@code IrisBiome.generateLayers} and friends and from
 * {@code IrisBiomePaletteLayer.get} before they moved; block keys list their properties in name order, because
 * the old classes listed them in the order of a hash map and the platform does not tell the two apart.
 */
public class TerrainLayersPinTest {
    private static final long TERRAIN_SEED = 1337L;
    private static final int TRANSCRIPT_LENGTH = 147047;
    private static final int TRANSCRIPT_HASH = 216639495;
    private static final int COLUMN_STEP_X = 61;
    private static final int COLUMN_STEP_Z = 67;
    private static final int COLUMN_RANGE = 400;

    private static final String MESA = """
            {"name": "Mesa", "lockLayers": true, "lockLayersMax": 9,
             "layers": [
               {"minHeight": 1, "maxHeight": 3, "zoom": 0.3, "style": {"style": "SIMPLEX", "zoom": 1},
                "palette": [{"block": "red_sand"}, {"block": "terracotta", "weight": 2}, {"block": "orange_terracotta"}, {"block": "yellow_terracotta"}]},
               {"minHeight": 2, "maxHeight": 5, "palette": [{"block": "white_terracotta"}, {"block": "brown_terracotta", "weight": 3}, {"block": "light_gray_terracotta"}],
                "style": {"style": "PERLIN", "zoom": 1}, "zoom": 0.2},
               {"minHeight": 1, "maxHeight": 2, "slopeCondition": {"minimumSlope": 2, "maximumSlope": 8}, "palette": [{"block": "gravel"}]}
             ],
             "slab": {"style": {"style": "SIMPLEX"}, "zoom": 0.5, "palette": [{"weight": 2, "block": "air"},
                      {"block": "cobblestone_slab", "data": {"waterlogged": false, "type": "bottom"}}, {"block": "stone_slab"}],
                      "slopeCondition": {"maximumSlope": 6}},
             "wall": {"style": {"style": "SIMPLEX"}, "zoom": 0.4, "palette": [{"block": "stone"}, {"block": "andesite", "weight": 2}, {"block": "diorite"}, {"block": "granite"}]}}
            """;
    private static final String MEADOW = """
            {"name": "Meadow",
             "layers": [
               {"minHeight": 1, "maxHeight": 1, "palette": [{"block": "grass_block"}]},
               {"minHeight": 2, "maxHeight": 4, "zoom": 0.25, "style": {"style": "CELLULAR"}, "palette": [{"block": "dirt"}, {"block": "coarse_dirt"}, {"block": "rooted_dirt"}]},
               {"minHeight": 8, "maxHeight": 20, "palette": [{"block": "stone"}]}
             ],
             "caveCeilingLayers": [
               {"minHeight": 1, "maxHeight": 2, "zoom": 0.3, "style": {"style": "SIMPLEX", "zoom": 1}, "palette": [{"block": "moss_block"}, {"block": "dripstone_block"}, {"block": "clay"}, {"block": "glow_lichen"}]},
               {"minHeight": 1, "maxHeight": 3, "palette": [{"block": "deepslate"}, {"block": "tuff", "weight": 2}]},
               {"minHeight": 1, "maxHeight": 1, "palette": [{"block": "calcite"}]},
               {"minHeight": 1, "maxHeight": 1, "palette": [{"block": "basalt"}]}
             ]}
            """;
    private static final String OCEAN = """
            {"name": "Ocean",
             "layers": [{"minHeight": 1, "maxHeight": 4, "zoom": 0.3, "style": {"style": "SIMPLEX", "zoom": 1}, "palette": [{"block": "sand"}, {"block": "gravel", "weight": 2}, {"block": "clay"}, {"block": "suspicious_sand"}]}],
             "seaLayers": [
               {"minHeight": 1, "maxHeight": 3, "zoom": 0.3, "style": {"style": "PERLIN", "zoom": 1}, "palette": [{"block": "kelp_plant"}, {"block": "water"}, {"block": "seagrass"}, {"block": "sea_pickle"}]},
               {"minHeight": 0, "maxHeight": 2, "palette": [{"block": "prismarine"}, {"block": "dark_prismarine"}], "zoom": 2}
             ]}
            """;
    private static final String DEFAULTS = "{\"name\": \"Plain\"}";
    private static final String[] BIOMES = {MESA, MEADOW, OCEAN, DEFAULTS};

    private IrisData data;
    private final ColumnSlope slope = (x, z) -> Math.abs((x * 3 + z * 5) % 11);

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

    private static final PaletteLayer NO_LAYER = PaletteLayer.withoutBlocks();

    @After
    public void unbindPlatform() {
        UsableBlockPlatform.unbind();
    }

    @Test
    public void layersPickAsTheyDidInTheBiome() {
        StringBuilder transcript = new StringBuilder();
        for (String json : BIOMES) {
            for (boolean exploded : new boolean[]{false, true}) {
                record(transcript, json, exploded);
            }
        }

        for (String picked : List.of("terracotta", "prismarine", "moss_block", "cobblestone_slab", "andesite", "barrier")) {
            assertTrue(picked + " is picked: " + transcript.length(), transcript.indexOf(picked) >= 0);
        }
        assertEquals("transcript " + transcript.length() + " / " + transcript.toString().hashCode(), TRANSCRIPT_LENGTH, transcript.length());
        assertEquals(TRANSCRIPT_HASH, transcript.toString().hashCode());
    }

    private void record(StringBuilder transcript, String biomeJson, boolean exploded) {
        PaletteExplosion explosion = new PaletteExplosion(exploded, 2);
        RNG rng = new RNG(TERRAIN_SEED);
        ModuleSettings settings = TerrainTestSettings.of(biomeJson);
        LayeredBiome biome = TestLayeredBiomes.of(settings);
        BiomeLayers layers = new BiomeLayers(TestLayerBlocks.of(data.getPackResources()), () -> data.getPackResources().noise(), ModuleLogs.discarding());
        for (int x = -COLUMN_RANGE; x < COLUMN_RANGE; x += COLUMN_STEP_X) {
            for (int z = -COLUMN_RANGE; z < COLUMN_RANGE; z += COLUMN_STEP_Z) {
                transcript.append('|').append(keys(layers.generateLayers(biome, explosion, x, z, rng, 14, 70 + Math.floorMod(x + z, 9), slope)));
                transcript.append('|').append(keys(layers.generateCeilingLayers(biome, explosion, x, z, rng, 12)));
                transcript.append('|').append(keys(layers.generateSeaLayers(biome, x, z, rng, 7)));
                transcript.append('|').append(smoothing(layers, settings, biome, rng, x, z));
            }
        }
    }

    /** The block a slab and a wall place in post processing at the column, as the smoothing stage asks the biome. */
    private String smoothing(BiomeLayers layers, ModuleSettings settings, LayeredBiome biome, RNG rng, int x, int z) {
        int height = 60 + Math.floorMod(x - z, 7);
        double columnSlope = slope.at(x, z);
        ResolvedLayer slabLayer = layers.resolve(settings.find(SlabSettings.class).map(SlabSettings::layer).orElse(NO_LAYER));
        ResolvedLayer wallLayer = layers.wall(biome);
        String slab = slabLayer.slopeCondition().isValid(columnSlope) ? key(slabLayer.get(rng, x, height, z)) : "-";
        String wall = !wallLayer.hasPalette() ? "-" : key(wallLayer.get(rng, x + height, height + height, z + height));
        return slab + "," + wall;
    }

    private static String keys(KList<BlockState> states) {
        StringBuilder keys = new StringBuilder();
        for (BlockState state : states) {
            keys.append(key(state)).append(' ');
        }
        return keys.toString();
    }

    /** The block's key without its namespace and with its properties in name order, which is how the platform identifies a state whatever order a pack lists them in. */
    private static String key(BlockState state) {
        if (state == null) {
            return "null";
        }
        String key = state.key().replace("minecraft:", "");
        int properties = key.indexOf('[');
        if (properties < 0) {
            return key;
        }
        String[] sorted = key.substring(properties + 1, key.length() - 1).split(",");
        java.util.Arrays.sort(sorted);
        return key.substring(0, properties) + "[" + String.join(",", sorted) + "]";
    }
}
