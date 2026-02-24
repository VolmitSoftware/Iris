package art.arcane.iris.util.project.noise;

import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class CNGFastPathParityTest {
    @Test
    public void identityFastPathMatchesLegacyAcrossSeedAndCoordinateGrid() {
        for (long seed = 3L; seed <= 11L; seed++) {
            CNG generator = createIdentityGenerator(seed);
            assertFastPathParity("identity-seed-" + seed, generator);
        }
    }

    @Test
    public void transformedGeneratorsMatchLegacyAcrossSeedAndCoordinateGrid() {
        for (long seed = 21L; seed <= 27L; seed++) {
            List<CNG> generators = createTransformedGenerators(seed);
            for (int index = 0; index < generators.size(); index++) {
                assertFastPathParity("transformed-seed-" + seed + "-case-" + index, generators.get(index));
            }
        }
    }

    private void assertFastPathParity(String label, CNG generator) {
        for (int x = -320; x <= 320; x += 19) {
            for (int z = -320; z <= 320; z += 23) {
                double expected = generator.noise(x, z);
                double actual = generator.noiseFast2D(x, z);
                assertEquals(label + " 2D x=" + x + " z=" + z, expected, actual, 1.0E-12D);
            }
        }

        for (int x = -128; x <= 128; x += 17) {
            for (int y = -96; y <= 96; y += 13) {
                for (int z = -128; z <= 128; z += 19) {
                    double expected = generator.noise(x, y, z);
                    double actual = generator.noiseFast3D(x, y, z);
                    assertEquals(label + " 3D x=" + x + " y=" + y + " z=" + z, expected, actual, 1.0E-12D);
                }
            }
        }
    }

    private CNG createIdentityGenerator(long seed) {
        DeterministicNoiseGenerator generator = new DeterministicNoiseGenerator(0.31D + (seed * 0.01D));
        return new CNG(new RNG(seed), generator, 1D, 1).bake();
    }

    private List<CNG> createTransformedGenerators(long seed) {
        List<CNG> generators = new ArrayList<>();

        CNG powerTransformed = createIdentityGenerator(seed).pow(1.27D);
        generators.add(powerTransformed);

        CNG offsetTransformed = createIdentityGenerator(seed + 1L).up(0.08D).down(0.03D).patch(0.91D);
        generators.add(offsetTransformed);

        CNG fractured = createIdentityGenerator(seed + 2L).fractureWith(createIdentityGenerator(seed + 300L), 12.5D);
        generators.add(fractured);

        CNG withChildren = createIdentityGenerator(seed + 3L);
        withChildren.child(createIdentityGenerator(seed + 400L));
        withChildren.child(createIdentityGenerator(seed + 401L));
        generators.add(withChildren);

        return generators;
    }

    private static class DeterministicNoiseGenerator implements NoiseGenerator {
        private final double offset;

        private DeterministicNoiseGenerator(double offset) {
            this.offset = offset;
        }

        @Override
        public double noise(double x) {
            double angle = (x * 0.011D) + offset;
            return 0.2D + (((Math.sin(angle) + 1D) * 0.5D) * 0.6D);
        }

        @Override
        public double noise(double x, double z) {
            double angle = (x * 0.013D) + (z * 0.017D) + offset;
            return 0.2D + (((Math.sin(angle) + 1D) * 0.5D) * 0.6D);
        }

        @Override
        public double noise(double x, double y, double z) {
            double angle = (x * 0.007D) + (y * 0.015D) + (z * 0.019D) + offset;
            return 0.2D + (((Math.sin(angle) + 1D) * 0.5D) * 0.6D);
        }
    }
}
