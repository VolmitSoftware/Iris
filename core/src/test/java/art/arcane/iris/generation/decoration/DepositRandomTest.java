package art.arcane.iris.generation.decoration;

import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DepositRandomTest {
    @Test
    public void everyDrawMatchesTheRngSequence() {
        int[] bounds = {1, 2, 3, 5, 7, 16, 100, 1 << 20, Integer.MAX_VALUE};
        for (long seed : new long[]{0L, 1L, -1L, 42L, 0x9E3779B97F4A7C15L, Long.MIN_VALUE}) {
            RNG reference = new RNG(seed);
            DepositRandom random = new DepositRandom(seed);
            assertEquals(reference.getSeed(), random.getSeed());
            for (int draw = 0; draw < 4000; draw++) {
                int bound = bounds[draw % bounds.length];
                switch (draw % 9) {
                    case 0 -> assertEquals(reference.nextInt(), random.nextInt());
                    case 1 -> assertEquals(reference.nextInt(bound), random.nextInt(bound));
                    case 2 -> assertEquals(reference.nextLong(), random.nextLong());
                    case 3 -> assertEquals(Double.doubleToRawLongBits(reference.nextDouble()),
                            Double.doubleToRawLongBits(random.nextDouble()));
                    case 4 -> assertEquals(Float.floatToRawIntBits(reference.nextFloat()),
                            Float.floatToRawIntBits(random.nextFloat()));
                    case 5 -> assertEquals(reference.nextBoolean(), random.nextBoolean());
                    case 6 -> assertEquals(Double.doubleToRawLongBits(reference.d()),
                            Double.doubleToRawLongBits(random.d()));
                    case 7 -> assertEquals(reference.i(-bound / 2, bound), random.i(-bound / 2, bound));
                    default -> assertEquals(reference.i(0, 1), random.i(0, 1));
                }
            }
        }
    }

    @Test
    public void skipAdvancesExactlyLikeRepeatedSteps() {
        for (long seed : new long[]{3L, -11L, 0x5DEECE66DL}) {
            for (long steps : new long[]{0L, 1L, 2L, 3L, 7L, 64L, 65L, 1000L, 123457L}) {
                DepositRandom stepped = new DepositRandom(seed);
                DepositRandom jumped = new DepositRandom(seed);
                for (long step = 0L; step < steps; step++) {
                    stepped.nextInt();
                }
                jumped.skip(steps);
                assertEquals("seed " + seed + " steps " + steps, stepped.state(), jumped.state());
                assertEquals(stepped.nextLong(), jumped.nextLong());
            }
        }
    }

    @Test
    public void reseedingAndRestoringReproduceTheSequence() {
        DepositRandom random = new DepositRandom();
        for (long seed : new long[]{5L, 77L, -9001L}) {
            random.setSeed(seed);
            RNG reference = new RNG(seed);
            long saved = random.state();
            long first = random.nextLong();
            assertEquals(reference.nextLong(), first);
            random.restore(saved);
            assertEquals(first, random.nextLong());
        }
    }
}
