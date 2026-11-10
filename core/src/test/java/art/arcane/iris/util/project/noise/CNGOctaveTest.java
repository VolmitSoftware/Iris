package art.arcane.iris.util.project.noise;

import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * A CNG built with an octave count samples its generator with that many octaves. Noise types are created behind an
 * offset wrapper; the count has to reach the noise inside it, or every multi-octave style samples one octave.
 */
public class CNGOctaveTest {
    private static final long SEED = 1337L;
    private static final int OCTAVES = 10;
    private static final int SINGLE_OCTAVE = 1;
    private static final double X = 123.4D;
    private static final double Y = 56.7D;
    private static final double Z = -89.1D;
    private static final List<NoiseType> OCTAVE_TYPES = List.of(
            NoiseType.SIMPLEX,
            NoiseType.PERLIN,
            NoiseType.FRACTAL_FBM_SIMPLEX,
            NoiseType.FRACTAL_BILLOW_SIMPLEX,
            NoiseType.FRACTAL_BILLOW_PERLIN,
            NoiseType.FRACTAL_RIGID_MULTI_SIMPLEX
    );

    @Test
    public void theGeneratorOfAnOctaveNoiseTypeSamplesTheOctavesTheCngWasBuiltWith() {
        for (NoiseType type : OCTAVE_TYPES) {
            NoiseGenerator generator = new CNG(new RNG(SEED), type, 1D, OCTAVES).getGenerator();
            assertTrue(type + " lost its octave setting when it was created", generator instanceof OctaveNoise);
            OctaveNoise octaves = (OctaveNoise) generator;

            double asBuilt = generator.noise(X, Y, Z);
            octaves.setOctaves(OCTAVES);
            assertEquals(type + " was not built with " + OCTAVES + " octaves", asBuilt, generator.noise(X, Y, Z), 0D);
            octaves.setOctaves(SINGLE_OCTAVE);
            assertNotEquals(type + " ignores its octave setting", asBuilt, generator.noise(X, Y, Z), 0D);
        }
    }
}
