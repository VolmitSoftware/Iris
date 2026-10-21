package art.arcane.iris.generation.stage;

import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IrisDepositModifierProbabilityTest {
    @Test
    public void generatorAndClumpChancesRemainIndependent() {
        int samples = 100_000;
        int accepted = 0;
        for (int seed = 0; seed < samples; seed++) {
            if (new RNG(seed).d() <= 0.2D
                    && new RNG(IrisDepositModifier.clumpSeed(seed, 0)).d() <= 0.2D) {
                accepted++;
            }
        }
        assertEquals(0.04D, accepted / (double) samples, 0.003D);
    }

    @Test
    public void clumpRandomMatchesRngDrawForDraw() {
        RNG seeds = new RNG(-90181L);
        for (int trial = 0; trial < 2_000; trial++) {
            long seed = trial < 4 ? new long[]{0L, -1L, Long.MIN_VALUE, Long.MAX_VALUE}[trial] : seeds.nextLong();
            RNG expected = new RNG(seed);
            RNG actual = new IrisDepositModifier.ClumpRandom(seed);
            for (int draw = 0; draw < 48; draw++) {
                String label = "seed " + seed + " draw " + draw;
                switch (draw % 8) {
                    case 0 -> assertEquals(label, Float.floatToIntBits(expected.nextFloat()), Float.floatToIntBits(actual.nextFloat()));
                    case 1 -> assertEquals(label, expected.nextInt(3), actual.nextInt(3));
                    case 2 -> assertEquals(label, Double.doubleToLongBits(expected.nextDouble()), Double.doubleToLongBits(actual.nextDouble()));
                    case 3 -> assertEquals(label, Double.doubleToLongBits(expected.d()), Double.doubleToLongBits(actual.d()));
                    case 4 -> assertEquals(label, expected.i(-5, 17), actual.i(-5, 17));
                    case 5 -> assertEquals(label, expected.nextInt(1_000_000_007), actual.nextInt(1_000_000_007));
                    case 6 -> assertEquals(label, expected.nextLong(), actual.nextLong());
                    default -> assertEquals(label, expected.nextBoolean(), actual.nextBoolean());
                }
            }
            assertEquals(expected.getSeed(), actual.getSeed());
        }
    }

    @Test
    public void neighboringClumpChancesRemainIndependentAcrossBatchBoundaries() {
        int samples = 100_000;
        for (int first : new int[]{0, 7, 31}) {
            int accepted = 0;
            for (int seed = 0; seed < samples; seed++) {
                if (new RNG(IrisDepositModifier.clumpSeed(seed, first)).d() <= 0.2D
                        && new RNG(IrisDepositModifier.clumpSeed(seed, first + 1)).d() <= 0.2D) {
                    accepted++;
                }
            }
            assertEquals("Clump pair at " + first, 0.04D, accepted / (double) samples, 0.003D);
        }
    }
}
