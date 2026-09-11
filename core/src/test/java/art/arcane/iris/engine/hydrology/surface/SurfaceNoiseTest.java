package art.arcane.iris.engine.hydrology.surface;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SurfaceNoiseTest {
    @Test
    public void scalarBitsPreserveChannelGeometry() {
        long[][] samples = {
                {0L, 0, 0, 1, 0x3fc9ff45ea7ce2bcL, 0xbfe3005d0ac18ea2L},
                {77L, -40, 39, 1, 0x3fe1608c5d9f966eL, 0x3fb608c5d9f966e0L},
                {77L, -40, 39, 2, 0x3fd12fd3b2255266L, 0xbfdda0589bb55b34L},
                {77L, -40, 39, 3, 0x3fde59c9633f9d3eL, 0xbfaa6369cc062c20L},
                {77L, -40, 39, 7, 0x3febee1ee702b638L, 0x3fe7dc3dce056c70L},
                {77L, -40, 39, 12, 0x3fe58a3d8c6a3a26L, 0x3fd628f631a8e898L},
                {77L, -40, 39, 16, 0x3fe379956dd75dfbL, 0x3fcbccab6ebaefd8L},
                {77L, -40, 39, 31, 0x3fdd9757689aa0dcL, 0xbfb34544bb2af920L},
                {77L, -40, 39, 64, 0x3fe0968a66559de7L, 0x3fa2d14ccab3bce0L},
                {0L, -12, -12, 12, 0x3fa2ed0fbd6e4760L, 0xbfeda25e08523714L},
                {0L, -13, -11, 12, 0x3fabab89552088a4L, 0xbfec8a8ed55beeecL},
                {-1L, 12, -12, 12, 0x3fc3b99ed5ce495cL, 0xbfe623309518db52L},
                {Long.MIN_VALUE, -1, 1, 31, 0x3fe213950b5ed096L, 0x3fc09ca85af684b0L},
                {Long.MAX_VALUE, 255, -257, 256, 0x3fcb1a5a870174baL, 0xbfe272d2bc7f45a3L},
                {1337L, -12345, 67890, 8192, 0x3fd4e8d04021f6aaL, 0xbfd62e5f7fbc12acL},
                {1337L, Integer.MIN_VALUE, Integer.MAX_VALUE, 1, 0x3fdc5d75fb3dcf0cL, 0xbfbd1450261187a0L},
                {1337L, Integer.MIN_VALUE, Integer.MAX_VALUE, 12, 0x3feb8585bd4cd270L, 0x3fe70b0b7a99a4e0L},
                {1337L, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, 0x3fed6ff84c27d2e8L, 0x3feadff0984fa5d0L}
        };
        for (long[] sample : samples) {
            long seed = sample[0];
            int x = (int) sample[1];
            int z = (int) sample[2];
            int wavelength = (int) sample[3];
            String context = "seed=" + seed + ", x=" + x + ", z=" + z + ", wavelength=" + wavelength;
            assertEquals(context, sample[4], Double.doubleToLongBits(SurfaceNoise.value(seed, x, z, wavelength)));
            assertEquals(context, sample[5], Double.doubleToLongBits(SurfaceNoise.signed(seed, x, z, wavelength)));
        }
    }

    @Test
    public void valuesStayInRangeAndAreDeterministic() {
        for (int x = -40; x < 40; x++) {
            for (int z = -40; z < 40; z++) {
                double value = SurfaceNoise.value(77L, x, z, 16);
                assertTrue(value >= 0D && value <= 1D);
                assertEquals(value, SurfaceNoise.value(77L, x, z, 16), 0D);
                double signed = SurfaceNoise.signed(77L, x, z, 16);
                assertTrue(signed >= -1D && signed <= 1D);
            }
        }
    }

    @Test
    public void neighbouringCellsAreCoherent() {
        double maximumStep = 0D;
        for (int x = 0; x < 200; x++) {
            maximumStep = Math.max(maximumStep, Math.abs(SurfaceNoise.value(5L, x, 3, 16) - SurfaceNoise.value(5L, x + 1, 3, 16)));
        }
        assertTrue(maximumStep < 0.2D);
    }

    @Test
    public void smoothStepEasesBetweenZeroAndOne() {
        assertEquals(0D, SurfaceNoise.smoothStep(0D), 0D);
        assertEquals(1D, SurfaceNoise.smoothStep(1D), 0D);
        assertEquals(0.5D, SurfaceNoise.smoothStep(0.5D), 1.0E-9D);
        assertTrue(SurfaceNoise.smoothStep(0.25D) < 0.25D);
        assertTrue(SurfaceNoise.smoothStep(0.75D) > 0.75D);
    }
}
