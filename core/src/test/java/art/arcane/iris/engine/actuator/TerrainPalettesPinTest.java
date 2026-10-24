package art.arcane.iris.engine.actuator;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.core.modules.IrisPackResources;
import art.arcane.iris.core.modules.ModuleSettings;
import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.object.material.IrisBlockData;
import art.arcane.iris.modules.terrain.TerrainPalettes;
import art.arcane.iris.engine.terrain.TerrainTestSettings;
import art.arcane.iris.engine.terrain.TestPackWorld;
import com.volmit.config.FeatureSettings;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.world.UpperDimension;
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
 * Pins the blocks the dimension's rock and fluid palettes pick for a spread of columns, so they could move out of
 * {@code IrisComplex} into the terrain module without changing what a world generates. The hash was taken from the streams
 * {@code IrisComplex} built from {@code IrisDimension.getRockPalette()} and {@code getFluidPalette()} before they moved,
 * for the world's complex seed and for the seed an upper dimension derives from it.
 */
public class TerrainPalettesPinTest {
    private static final long COMPLEX_SEED = 424242L;
    private static final String UPPER_DIMENSION_KEY = "upper";
    private static final long UPPER_DIMENSION_SEED = COMPLEX_SEED ^ UPPER_DIMENSION_KEY.hashCode();
    private static final int TRANSCRIPT_LENGTH = 154196;
    private static final int TRANSCRIPT_HASH = 1568450945;
    private static final int COLUMN_RANGE = 1200;
    private static final int COLUMN_STEP_X = 53;
    private static final int COLUMN_STEP_Z = 59;

    private static final String STYLED = """
            {"rockPalette": {"style": {"style": "SIMPLEX", "zoom": 0.5}, "zoom": 9,
                             "palette": [{"block": "stone", "weight": 3}, {"block": "granite"}, {"block": "andesite", "weight": 2}, {"block": "diorite"}, {"block": "tuff"}]},
             "fluidPalette": {"style": {"style": "PERLIN", "zoom": 0.3},
                              "palette": [{"block": "water"}, {"block": "lava", "weight": 2}, {"block": "packed_ice"}]}}
            """;
    private static final String DEFAULTS = "{}";
    private static final String SINGLE = "{\"rockPalette\": {\"palette\": [{\"block\": \"netherrack\"}]}, \"fluidPalette\": {\"palette\": [{\"block\": \"lava\"}]}}";

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
    public void rockAndFluidPickAsTheyDidInTheComplex() {
        StringBuilder transcript = new StringBuilder();
        for (String json : List.of(STYLED, DEFAULTS, SINGLE)) {
            ModuleSettings dimension = TerrainTestSettings.of(json);
            record(transcript, TerrainPalettes.of(world(dimension, COMPLEX_SEED)));
            record(transcript, TerrainPalettes.of(world(dimension, UPPER_DIMENSION_SEED)));
        }

        for (String picked : List.of("granite", "andesite", "diorite", "tuff", "lava", "packed_ice", "netherrack")) {
            assertTrue(picked + " is picked", transcript.indexOf(picked) >= 0);
        }
        assertEquals("transcript " + transcript.length() + " / " + transcript.toString().hashCode(), TRANSCRIPT_LENGTH, transcript.length());
        assertEquals(TRANSCRIPT_HASH, transcript.toString().hashCode());
    }

    @Test
    public void anUpperDimensionPicksItsRockFromTheSeedItDerivesFromTheWorldsAndItsOwnKey() {
        ModuleSettings upperDimension = TerrainTestSettings.of(STYLED);
        UpperDimension upper = upper(false, upperDimension);
        TerrainPalettes world = TerrainPalettes.of(world(TerrainTestSettings.of(DEFAULTS), COMPLEX_SEED));
        TerrainPalettes derived = TerrainPalettes.of(world(upperDimension, UPPER_DIMENSION_SEED));
        for (int x = -COLUMN_RANGE; x < COLUMN_RANGE; x += COLUMN_STEP_X) {
            for (int z = -COLUMN_RANGE; z < COLUMN_RANGE; z += COLUMN_STEP_Z) {
                assertEquals(derived.rockAt(x, z).key(), world.upperRockAt(upper, x, z).key());
            }
        }
    }

    @Test
    public void aDimensionThatIsItsOwnUpperDimensionSharesItsRock() {
        TerrainPalettes world = TerrainPalettes.of(world(TerrainTestSettings.of(STYLED), COMPLEX_SEED));
        UpperDimension upper = upper(true, TerrainTestSettings.of(DEFAULTS));
        assertEquals(world.rockAt(17, -90).key(), world.upperRockAt(upper, 17, -90).key());
    }

    private TestPackWorld world(ModuleSettings dimension, long complexSeed) {
        return new TestPackWorld(data.getPackResources(), dimension, complexSeed);
    }

    private UpperDimension upper(boolean thisDimension, ModuleSettings dimension) {
        PackResources resources = data.getPackResources();
        return new UpperDimension() {
            @Override
            public boolean isThisDimension() {
                return thisDimension;
            }

            @Override
            public ResourceId id() {
                return resources.idOf(UPPER_DIMENSION_KEY);
            }

            @Override
            public FeatureSettings settings() {
                return TestPackWorld.dimensionLevel(dimension);
            }

            @Override
            public PackResources resources() {
                return resources;
            }

            @Override
            public java.util.Optional<com.volmit.block.BlockState> surfaceBlock(int worldX, int worldZ) {
                return java.util.Optional.empty();
            }
        };
    }

    private static void record(StringBuilder transcript, TerrainPalettes palettes) {
        for (int x = -COLUMN_RANGE; x < COLUMN_RANGE; x += COLUMN_STEP_X) {
            for (int z = -COLUMN_RANGE; z < COLUMN_RANGE; z += COLUMN_STEP_Z) {
                transcript.append(palettes.rockAt(x, z).key().replace("minecraft:", "")).append(',')
                        .append(palettes.fluidAt(x, z).key().replace("minecraft:", "")).append(' ');
            }
        }
    }
}
