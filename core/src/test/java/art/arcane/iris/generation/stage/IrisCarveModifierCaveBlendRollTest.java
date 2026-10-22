package art.arcane.iris.generation.stage;

import art.arcane.volmlib.util.math.BlockPosition;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.util.SplittableRandom;

import static org.junit.Assert.assertEquals;

public class IrisCarveModifierCaveBlendRollTest {
    private static final int SAMPLES = 1_000_000;

    @Test
    public void statelessBlendRandomMatchesParallelRngForOneMillionPoints() {
        SplittableRandom random = new SplittableRandom(0x5eed_cafe_f00dL);
        long[] seeds = {0L, 1L, -1L, 1337L, Long.MIN_VALUE, Long.MAX_VALUE, 0x5DEECE66DL, random.nextLong()};
        for (int sample = 0; sample < SAMPLES; sample++) {
            long seed = sample < seeds.length * 64 ? seeds[sample % seeds.length] : random.nextLong();
            int x = coordinate(random, sample);
            int y = sample % 7 == 0 ? random.nextInt() : random.nextInt(-2048, 2048);
            int z = coordinate(random, sample + 3);
            assertEquals(seed + " @ " + x + "," + y + "," + z,
                    new RNG(seed).nextParallelRNG(BlockPosition.toLong(x, y, z)).nextInt(),
                    IrisCarveModifier.caveBlendRandom(seed, x, y, z));
        }
    }

    @Test
    public void statelessBlendRandomMatchesParallelRngAtCoordinateExtremes() {
        int[] edges = {Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -33_554_433, -33_554_432, -1, 0, 1,
                33_554_431, 33_554_432, Integer.MAX_VALUE - 1, Integer.MAX_VALUE};
        long[] seeds = {0L, -1L, 1337L, Long.MIN_VALUE, Long.MAX_VALUE};
        for (long seed : seeds) {
            for (int x : edges) {
                for (int y : edges) {
                    for (int z : edges) {
                        assertEquals(new RNG(seed).nextParallelRNG(BlockPosition.toLong(x, y, z)).nextInt(),
                                IrisCarveModifier.caveBlendRandom(seed, x, y, z));
                    }
                }
            }
        }
    }

    private static int coordinate(SplittableRandom random, int sample) {
        return switch (sample % 4) {
            case 0 -> random.nextInt();
            case 1 -> random.nextInt(-30_000_000, 30_000_000);
            default -> random.nextInt(-65_536, 65_536);
        };
    }
}
