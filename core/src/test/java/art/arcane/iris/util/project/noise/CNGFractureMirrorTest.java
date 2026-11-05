package art.arcane.iris.util.project.noise;

import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class CNGFractureMirrorTest {
    private static final int GRID_SIZE = 40;
    private static final double GRID_STEP = 7.3D;
    private static final double MIRRORED = 1.0E-12D;
    private static final int ALLOWED_MIRRORED_SAMPLES = 2;

    @Test
    public void fracturedNoiseIsNotMirroredAcrossTheDiagonal() {
        assertNotMirrored("2D fast", (noise, a, b) -> noise.noise(a, b));
        assertNotMirrored("2D signed", (noise, a, b) -> noise.noiseFastSigned2D(a, b));
        assertNotMirrored("generic", (noise, a, b) -> noise.noise(new double[]{a, b}));
    }

    private void assertNotMirrored(String path, Sampler sampler) {
        CNG noise = fracturedSymmetricNoise();
        int mirroredSamples = 0;
        for (int i = 0; i < GRID_SIZE; i++) {
            for (int j = i + 1; j < GRID_SIZE; j++) {
                double a = i * GRID_STEP;
                double b = j * GRID_STEP + 0.5D;
                if (Math.abs(sampler.sample(noise, a, b) - sampler.sample(noise, b, a)) < MIRRORED) {
                    mirroredSamples++;
                }
            }
        }
        assertTrue(path + " fracture mirrors the noise across x = z at " + mirroredSamples + " samples",
                mirroredSamples <= ALLOWED_MIRRORED_SAMPLES);
    }

    private static CNG fracturedSymmetricNoise() {
        CNG fracture = new CNG(new RNG(91L), NoiseType.SIMPLEX, 1D, 1).scale(0.03D);
        return new CNG(new RNG(17L), new DiagonalSymmetricGenerator(), 1D, 1).fractureWith(fracture, 40D);
    }

    @FunctionalInterface
    private interface Sampler {
        double sample(CNG noise, double a, double b);
    }

    private static final class DiagonalSymmetricGenerator implements NoiseGenerator {
        @Override
        public double noise(double x) {
            return noise(x, 0D, 0D);
        }

        @Override
        public double noise(double x, double z) {
            return noise(x, z, 0D);
        }

        @Override
        public double noise(double x, double y, double z) {
            return 0.5D + (Math.sin(x * 0.11D) + Math.sin(y * 0.11D) + Math.sin(z * 0.11D)) / 6D;
        }
    }
}
