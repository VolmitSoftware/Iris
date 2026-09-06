package art.arcane.iris.engine.modifier;

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
