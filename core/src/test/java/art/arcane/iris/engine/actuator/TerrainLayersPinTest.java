package art.arcane.iris.engine.actuator;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.core.modules.IrisPackResources;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.object.material.IrisBlockData;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import com.google.gson.Gson;
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
 * {@code IrisBiomePaletteLayer.get} before they moved.
 */
public class TerrainLayersPinTest {
    private static final long TERRAIN_SEED = 1337L;
    private static final int TRANSCRIPT_LENGTH = 143080;
    private static final int TRANSCRIPT_HASH = -1399100638;
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
    private IrisComplex complex;

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
        complex = mock(IrisComplex.class);
        when(complex.getSlopeStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> Math.abs((x * 3 + z * 5) % 11)));
    }

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

        try { java.nio.file.Files.writeString(java.nio.file.Path.of("/tmp/iris-wt/3t/transcript.txt"), transcript.toString().replace("|", "\n")); } catch (java.io.IOException e) { throw new RuntimeException(e); }
        for (String picked : List.of("terracotta", "prismarine", "moss_block", "cobblestone_slab", "andesite", "barrier")) {
            assertTrue(picked + " is picked: " + transcript.length(), transcript.indexOf(picked) >= 0);
        }
        assertEquals("transcript " + transcript.length() + " / " + transcript.toString().hashCode(), TRANSCRIPT_LENGTH, transcript.length());
        assertEquals(TRANSCRIPT_HASH, transcript.toString().hashCode());
    }

    private void record(StringBuilder transcript, String biomeJson, boolean exploded) {
        IrisDimension dimension = new IrisDimension();
        dimension.setExplodeBiomePalettes(exploded);
        dimension.setExplodeBiomePaletteSize(2);
        RNG rng = new RNG(TERRAIN_SEED);
        IrisBiome biome = new Gson().fromJson(biomeJson, IrisBiome.class);
        for (int x = -COLUMN_RANGE; x < COLUMN_RANGE; x += COLUMN_STEP_X) {
            for (int z = -COLUMN_RANGE; z < COLUMN_RANGE; z += COLUMN_STEP_Z) {
                transcript.append('|').append(keys(biome.generateLayers(dimension, x, z, rng, 14, 70 + Math.floorMod(x + z, 9), data, complex)));
                transcript.append('|').append(keys(biome.generateCeilingLayers(dimension, x, z, rng, 12, 40, data, complex)));
                transcript.append('|').append(keys(biome.generateSeaLayers(x, z, rng, 7, data)));
                transcript.append('|').append(smoothing(biome, rng, x, z));
            }
        }
    }

    /** The block a slab and a wall place in post processing at the column, as the smoothing stage asks the biome. */
    private String smoothing(IrisBiome biome, RNG rng, int x, int z) {
        int height = 60 + Math.floorMod(x - z, 7);
        double slope = complex.getSlopeStream().get(x, z);
        String slab = biome.getSlab().getSlopeCondition().isValid(slope) ? key(biome.getSlab().get(rng, x, height, z, data)) : "-";
        String wall = biome.getWall().getPalette().isEmpty() ? "-" : key(biome.getWall().get(rng, x + height, height + height, z + height, data));
        return slab + "," + wall;
    }

    private static String keys(KList<PlatformBlockState> states) {
        StringBuilder keys = new StringBuilder();
        for (PlatformBlockState state : states) {
            keys.append(key(state)).append(' ');
        }
        return keys.toString();
    }

    private static String key(PlatformBlockState state) {
        return state == null ? "null" : state.key().replace("minecraft:", "");
    }
}
