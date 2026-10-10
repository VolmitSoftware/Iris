package art.arcane.iris.engine.actuator;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.core.modules.IrisPackResources;
import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.object.material.IrisBlockData;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.stream.ProceduralStream;
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
 * Pins the blocks the dimension's rock and fluid palettes pick for a spread of columns, so they can move out of
 * {@code IrisComplex} into the terrain module without changing what a world generates. The hash was taken from the streams
 * {@code IrisComplex} built from {@code IrisDimension.getRockPalette()} and {@code getFluidPalette()} before they moved.
 */
public class TerrainPalettesPinTest {
    private static final long COMPLEX_SEED = 424242L;
    private static final long UPPER_DIMENSION_SEED_OFFSET = "upper".hashCode();
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
            IrisDimension dimension = new Gson().fromJson(json, IrisDimension.class);
            record(transcript, dimension, new RNG(COMPLEX_SEED));
            record(transcript, dimension, new RNG(COMPLEX_SEED ^ UPPER_DIMENSION_SEED_OFFSET));
        }

        for (String picked : List.of("granite", "andesite", "diorite", "tuff", "lava", "packed_ice", "netherrack")) {
            assertTrue(picked + " is picked", transcript.indexOf(picked) >= 0);
        }
        assertEquals("transcript " + transcript.length() + " / " + transcript.toString().hashCode(), TRANSCRIPT_LENGTH, transcript.length());
        assertEquals(TRANSCRIPT_HASH, transcript.toString().hashCode());
    }

    private void record(StringBuilder transcript, IrisDimension dimension, RNG complexRng) {
        ProceduralStream<PlatformBlockState> rock = dimension.getRockPalette().getLayerGenerator(complexRng.nextParallelRNG(45), data).stream()
                .select(dimension.getRockPalette().getBlockData(data));
        ProceduralStream<PlatformBlockState> fluid = dimension.getFluidPalette().getLayerGenerator(complexRng.nextParallelRNG(78), data).stream()
                .select(dimension.getFluidPalette().getBlockData(data));
        for (int x = -COLUMN_RANGE; x < COLUMN_RANGE; x += COLUMN_STEP_X) {
            for (int z = -COLUMN_RANGE; z < COLUMN_RANGE; z += COLUMN_STEP_Z) {
                transcript.append(rock.get(x, z).key().replace("minecraft:", "")).append(',')
                        .append(fluid.get(x, z).key().replace("minecraft:", "")).append(' ');
            }
        }
    }
}
