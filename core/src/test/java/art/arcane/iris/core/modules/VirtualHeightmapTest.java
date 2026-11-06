package art.arcane.iris.core.modules;

import art.arcane.iris.builtin.objects.HeightmapNoise;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.iris.engine.object.noise.IrisNoiseGenerator;
import art.arcane.iris.modules.placement.VirtualHeightmap;
import com.google.gson.Gson;
import com.volmit.noise.NoiseFactory;
import com.volmit.noise.NoiseSampler;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleBinaryOperator;
import java.util.function.LongFunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The heights of a placement's virtual heightmap: the settings shape the API noise, and the heights are those Iris's own noise generator gave. */
public class VirtualHeightmapTest {
    private static final long SUPER_SEED = 1_000L;
    private static final long GENERATOR_SEED_SALT = 33_955_677L;
    private static final long FIRST_FRACTURE_OFFSET = 33L;
    private static final long FRACTURE_STEP = 819L;

    private final List<Long> sampledSeeds = new ArrayList<>();

    @Test
    public void aDisabledHeightmapIsFlatAtItsOffset() {
        VirtualHeightmap heightmap = heightmap("{\"enabled\": false, \"offsetY\": 70}", seed -> (x, z) -> 999);

        assertEquals(70, heightmap.surface(12, -40));
        assertTrue("no noise is built for it", sampledSeeds.isEmpty());
    }

    @Test
    public void theNoiseIsSeededFromTheSuperSeedAndTheSettingsOwnSeed() {
        heightmap("{\"seed\": 7}", seed -> (x, z) -> 0);

        assertEquals(List.of(SUPER_SEED + GENERATOR_SEED_SALT - 7), sampledSeeds);
    }

    @Test
    public void zoomAndOffsetsMoveTheColumnAndOpacityAndOffsetYShapeTheHeight() {
        VirtualHeightmap heightmap = heightmap("{\"zoom\": 2, \"offsetX\": 10, \"opacity\": 0.5, \"offsetY\": 60}", seed -> (x, z) -> x + 2 * z);

        double sampleX = 40 / 2D + 10;
        double sampleZ = 6 / 2D;
        assertEquals(Math.round((sampleX + 2 * sampleZ) * 0.5 + 60), heightmap.surface(40, 6));
    }

    @Test
    public void aNegativeHeightmapIsTurnedOverItsOpacity() {
        VirtualHeightmap heightmap = heightmap("{\"negative\": true, \"opacity\": 0.5, \"offsetY\": 60}", seed -> (x, z) -> 0.25);

        assertEquals(Math.round(-(0.25 * 0.5) + 0.5 + 60), heightmap.surface(0, 0));
    }

    @Test
    public void anExponentRaisesTheHeightBeforeItsOffset() {
        VirtualHeightmap heightmap = heightmap("{\"exponent\": 2, \"offsetY\": 3}", seed -> (x, z) -> 4);

        assertEquals(19, heightmap.surface(0, 0));
    }

    @Test
    public void aFractureMovesTheColumnByItsHeightAroundHalfItsOpacity() {
        long fractureSeed = SUPER_SEED + FIRST_FRACTURE_OFFSET + GENERATOR_SEED_SALT - 3;
        VirtualHeightmap heightmap = heightmap("{\"fracture\": [{\"seed\": 3}]}",
                seed -> seed == fractureSeed ? (x, z) -> 1.0 : (x, z) -> x);

        assertEquals("10 + (1.0 - 0.5)", 11, heightmap.surface(10, 0));
        assertTrue(sampledSeeds.contains(fractureSeed));
    }

    @Test
    public void aDisabledFractureIsSkippedButStillCountsForTheSeedsOfTheOnesAfterIt() {
        long secondFractureSeed = SUPER_SEED + FIRST_FRACTURE_OFFSET + FRACTURE_STEP + GENERATOR_SEED_SALT - 5;

        heightmap("{\"fracture\": [{\"enabled\": false}, {\"seed\": 5}]}", seed -> (x, z) -> 0);

        assertEquals(2, sampledSeeds.size());
        assertTrue(sampledSeeds.contains(secondFractureSeed));
    }

    @Test
    public void theHeightsAreThoseOfIrisGeneratorsForTheSameSettingsAndSeed() {
        IrisData data = mock(IrisData.class);
        when(data.getDataFolder()).thenReturn(new File("test-pack"));
        IrisNoiseFactory noise = new IrisNoiseFactory(data, new IrisPackResources(data, List::of));
        String[] settings = {
                "{\"style\": {\"style\": \"SIMPLEX\", \"zoom\": 0.5}, \"zoom\": 40, \"offsetY\": 60, \"opacity\": 0.5, \"seed\": 9}",
                "{\"style\": {\"style\": \"CELLULAR\"}, \"zoom\": 17, \"negative\": true, \"exponent\": 1.5, \"offsetX\": 33, \"offsetZ\": -8}",
                "{\"style\": {\"style\": \"SIMPLEX\"}, \"zoom\": 25, \"bezier\": true, \"fracture\": [{\"style\": {\"style\": \"PERLIN\"}, \"zoom\": 6, \"seed\": 2},"
                        + "{\"enabled\": false}, {\"style\": {\"style\": \"SIMPLEX\"}, \"zoom\": 11, \"opacity\": 0.7, \"seed\": 4}]}",
                "{\"style\": {\"style\": \"SIMPLEX\"}, \"zoom\": 20, \"sinCentered\": true, \"octaves\": 4}",
        };

        long mismatches = 0;
        for (long superSeed : new long[]{1L, 7235L, -3_999_281L}) {
            for (String json : settings) {
                VirtualHeightmap heightmap = VirtualHeightmap.of(TestSettings.of(HeightmapNoise.class, json), noise, superSeed);
                IrisNoiseGenerator legacy = new Gson().fromJson(json, IrisNoiseGenerator.class);
                for (int x = -300; x < 300; x += 23) {
                    for (int z = -300; z < 300; z += 29) {
                        mismatches += heightmap.surface(x, z) == (int) Math.round(legacy.getNoise(superSeed, x, z, data)) ? 0 : 1;
                    }
                }
            }
        }
        assertEquals(0, mismatches);
    }

    private VirtualHeightmap heightmap(String json, LongFunction<DoubleBinaryOperator> noiseOfSeed) {
        NoiseFactory noise = (style, seed) -> {
            sampledSeeds.add(seed);
            return planar(noiseOfSeed.apply(seed));
        };
        return VirtualHeightmap.of(TestSettings.of(HeightmapNoise.class, json), noise, SUPER_SEED);
    }

    private static NoiseSampler planar(DoubleBinaryOperator height) {
        return new NoiseSampler() {
            @Override
            public double sample(double worldX, double worldZ) {
                return height.applyAsDouble(worldX, worldZ);
            }

            @Override
            public double sample(double worldX, double y, double worldZ) {
                throw new UnsupportedOperationException("A heightmap is planar");
            }
        };
    }
}
