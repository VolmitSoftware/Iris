package art.arcane.iris.modules.placement;

import art.arcane.iris.util.project.noise.SimplexNoise;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** The vacuum edge's copy of simplex noise draws exactly what Iris's own does, so vacuumed edges stay where they were. */
public class EdgeSimplexPinTest {
    private static final long[] SEEDS = {0L, 1L, -7L, 1337L, 0x5DEECE66DL, Long.MAX_VALUE, Long.MIN_VALUE};
    private static final double SPAN = 400D;
    private static final double STEP = 37.25D;

    @Test
    public void edgeSimplexMatchesIrisSimplexNoiseBitForBit() {
        for (long seed : SEEDS) {
            EdgeSimplex copy = new EdgeSimplex(seed);
            SimplexNoise iris = new SimplexNoise(seed);
            for (double x = -SPAN; x <= SPAN; x += STEP) {
                for (double z = -SPAN; z <= SPAN; z += STEP) {
                    assertEquals("seed " + seed + " at " + x + "," + z, iris.noiseSigned(x, z), copy.signed(x, z), 0D);
                }
            }
        }
    }
}
