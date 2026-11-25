package art.arcane.iris.generation.noise;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.noise.NoiseGenerator;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public final class IrisNoiseGeneratorFractureMirrorTest {
    private static final long SEED = 71L;
    private static final int GRID_SIZE = 40;
    private static final double GRID_STEP = 7.3D;
    private static final double MIRRORED = 1.0E-12D;
    private static final int ALLOWED_MIRRORED_SAMPLES = 2;

    @Test
    public void aCompositeFractureIsNotMirroredAcrossTheDiagonal() {
        IrisNoiseGenerator fractured = diagonalSymmetricRoot().setFracture(new KList<>(fracture()));

        assertTrue(mirroredSamples(fractured) <= ALLOWED_MIRRORED_SAMPLES);
    }

    @Test
    public void theDiagonalSymmetricRootIsMirroredWithoutAFracture() {
        assertTrue(mirroredSamples(diagonalSymmetricRoot()) > ALLOWED_MIRRORED_SAMPLES);
    }

    private static int mirroredSamples(IrisNoiseGenerator noise) {
        int mirroredSamples = 0;
        for (int i = 0; i < GRID_SIZE; i++) {
            for (int j = i + 1; j < GRID_SIZE; j++) {
                double a = i * GRID_STEP;
                double b = j * GRID_STEP + 0.5D;
                if (Math.abs(noise.getNoise(SEED, a, b, null) - noise.getNoise(SEED, b, a, null)) < MIRRORED) {
                    mirroredSamples++;
                }
            }
        }
        return mirroredSamples;
    }

    private static IrisNoiseGenerator diagonalSymmetricRoot() {
        IrisNoiseGenerator root = new IrisNoiseGenerator().setStyle(NoiseStyle.SIMPLEX.style()).setSeed(5L);
        root.getGenerator(SEED, null, null).setGenerator(new DiagonalSymmetricGenerator());
        return root;
    }

    private static IrisNoiseGenerator fracture() {
        return new IrisNoiseGenerator().setStyle(NoiseStyle.SIMPLEX.style()).setSeed(9L).setZoom(40D).setOpacity(40D);
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
