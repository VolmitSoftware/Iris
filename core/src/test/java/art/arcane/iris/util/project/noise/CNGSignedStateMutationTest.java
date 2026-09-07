package art.arcane.iris.util.project.noise;

import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class CNGSignedStateMutationTest {
    private static final double EPSILON = 1.0E-12D;

    @Test
    public void eachTransformRefreshesPreviouslyCachedCoordinates() {
        List<Consumer<CNG>> mutations = List.of(
                noise -> noise.scale(0.375D),
                noise -> noise.scale(0.5D).bake().scale(0.75D),
                noise -> noise.zoom(2D),
                noise -> noise.oct(3),
                noise -> noise.up(0.125D),
                noise -> noise.down(0.125D),
                noise -> noise.patch(0.75D),
                noise -> noise.pow(1.25D),
                noise -> noise.fractureWith(simplex(13L), 4D),
                noise -> noise.child(simplex(17L)).injectWith(CNG.MULTIPLY));
        for (Consumer<CNG> mutation : mutations) {
            CNG sampled = simplex(11L);
            sampled.noiseFastSigned2D(4D, 8D);
            sampled.noiseFastSigned3D(4D, 6D, 8D);
            mutation.accept(sampled);
            CNG expected = simplex(11L);
            mutation.accept(expected);
            assertEquals((expected.noiseFast2D(4D, 8D) * 2D) - 1D,
                    sampled.noiseFastSigned2D(4D, 8D), EPSILON);
            assertEquals((expected.noiseFast3D(4D, 6D, 8D) * 2D) - 1D,
                    sampled.noiseFastSigned3D(4D, 6D, 8D), EPSILON);
        }
    }

    @Test
    public void generatorCallbackMutationInvalidatesOnlySubsequentSamples() {
        for (boolean threeDimensions : new boolean[]{false, true}) {
            RecordingNoise source = new RecordingNoise();
            CNG noise = new CNG(new RNG(11L), source, 1D, 1);
            source.onSample = () -> noise.scale(0.5D);
            assertEquals(value(threeDimensions, 4D, 6D, 8D), sample(noise, threeDimensions), 0D);
            assertEquals(value(threeDimensions, 2D, 3D, 4D), sample(noise, threeDimensions), 0D);
            assertEquals(value(threeDimensions, 2D, 3D, 4D), sample(noise, threeDimensions), 0D);
            assertEquals(2, source.calls);
        }
    }

    @Test
    public void nestedFractureOverridesRetainParentAndChildMutationBoundaries() {
        for (boolean threeDimensions : new boolean[]{false, true}) {
            RecordingNoise source = new RecordingNoise();
            CNG parent = new CNG(new RNG(11L), source, 1D, 1);
            RecordingNoise fractureSource = new RecordingNoise();
            MutatingFracture fracture = new MutatingFracture(fractureSource);
            fracture.beforeSample = () -> {
                parent.scale(0.5D);
                fracture.scale(0.25D);
            };
            parent.fractureWith(fracture, 8D);
            double shiftedX = 4D + value(threeDimensions, 1D, 1.5D, 2D) * 4D;
            double shiftedY = 6D + value(false, 1.5D, 0D, 1D) * 4D;
            double shiftedZ = 8D + (threeDimensions
                    ? value(true, 2D, 1D, 1.5D) : value(false, 2D, 0D, 1D)) * 4D;
            assertEquals(value(threeDimensions, shiftedX, shiftedY, shiftedZ), sample(parent, threeDimensions), 0D);
            assertEquals(value(threeDimensions, shiftedX * 0.5D, shiftedY * 0.5D, shiftedZ * 0.5D),
                    sample(parent, threeDimensions), 0D);
            assertEquals(2, source.calls);
        }
    }

    @Test
    public void callbackFailureRetainsItsMutationAndDoesNotPublishASample() {
        for (boolean threeDimensions : new boolean[]{false, true}) {
            RecordingNoise source = new RecordingNoise();
            CNG noise = new CNG(new RNG(11L), source, 1D, 1);
            IllegalStateException failure = new IllegalStateException("sample failed");
            source.onSample = () -> {
                noise.scale(0.25D);
                throw failure;
            };
            assertSame(failure, assertThrows(IllegalStateException.class, () -> sample(noise, threeDimensions)));
            assertEquals(value(threeDimensions, 1D, 1.5D, 2D), sample(noise, threeDimensions), 0D);
            assertEquals(value(threeDimensions, 1D, 1.5D, 2D), sample(noise, threeDimensions), 0D);
            assertEquals(2, source.calls);
        }
    }

    private static CNG simplex(long seed) {
        return new CNG(new RNG(seed), new SimplexNoise(seed), 0.75D, 1);
    }

    private static double sample(CNG noise, boolean threeDimensions) {
        return threeDimensions ? noise.noiseFastSigned3D(4D, 6D, 8D) : noise.noiseFastSigned2D(4D, 8D);
    }

    private static double value(boolean threeDimensions, double x, double y, double z) {
        return threeDimensions ? x * 0.01D + y * 0.02D + z * 0.03D : x * 0.01D + z * 0.02D;
    }

    private static final class RecordingNoise implements NoiseGenerator {
        private Runnable onSample;
        private int calls;

        @Override
        public double noise(double x) {
            return noise(x, 0D);
        }

        @Override
        public double noise(double x, double z) {
            return (noiseSigned(x, z) + 1D) * 0.5D;
        }

        @Override
        public double noise(double x, double y, double z) {
            return (noiseSigned(x, y, z) + 1D) * 0.5D;
        }

        @Override
        public double noiseSigned(double x, double z) {
            sampled();
            return value(false, x, 0D, z);
        }

        @Override
        public double noiseSigned(double x, double y, double z) {
            sampled();
            return value(true, x, y, z);
        }

        private void sampled() {
            calls++;
            Runnable action = onSample;
            onSample = null;
            if (action != null) {
                action.run();
            }
        }
    }

    private static final class MutatingFracture extends CNG {
        private Runnable beforeSample;

        private MutatingFracture(NoiseGenerator source) {
            super(new RNG(13L), source, 1D, 1);
        }

        @Override
        public double noiseFastSigned2D(double x, double z) {
            mutate();
            return super.noiseFastSigned2D(x, z);
        }

        @Override
        public double noiseFastSigned3D(double x, double y, double z) {
            mutate();
            return super.noiseFastSigned3D(x, y, z);
        }

        private void mutate() {
            Runnable action = beforeSample;
            beforeSample = null;
            if (action != null) {
                action.run();
            }
        }
    }
}
