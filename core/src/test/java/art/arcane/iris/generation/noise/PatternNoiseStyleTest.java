package art.arcane.iris.generation.noise;

import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PatternNoiseStyleTest {
    private static final NoiseStyle[] STYLES = {
            NoiseStyle.GYROID, NoiseStyle.QUASICRYSTAL, NoiseStyle.TRUCHET,
            NoiseStyle.CRATER, NoiseStyle.VORTEX,
            NoiseStyle.DUNE, NoiseStyle.STRATA, NoiseStyle.WOOD, NoiseStyle.GABOR, NoiseStyle.MARBLE,
            NoiseStyle.SCALES, NoiseStyle.CHLADNI, NoiseStyle.KALEIDOSCOPE, NoiseStyle.MENGER_SPONGE, NoiseStyle.CIRCUIT
    };

    @Test
    public void styleIntegrationUsesTheSameScaleInEveryDimension() {
        for (NoiseStyle style : STYLES) {
            CNG direct = style.create(new RNG(7331L));
            CNG zoomed = style.style().zoomed(2D).createNoCache(new RNG(7331L), null);
            for (int index = 0; index < 128; index++) {
                double x = index * 13.713D - 400D;
                double y = index * 1.119D - 70D;
                double z = index * -17.179D + 300D;
                assertEquals(style.name(), direct.noise(x, z), zoomed.noise(x * 2D, z * 2D), 1E-10D);
                assertEquals(style.name(), direct.noise(x, y, z), zoomed.noise(x * 2D, y * 2D, z * 2D), 1E-10D);
                assertEquals(style.name(), direct.noise(x, z), direct.noise(x, 0D, z), 0D);
            }
        }
    }

}
