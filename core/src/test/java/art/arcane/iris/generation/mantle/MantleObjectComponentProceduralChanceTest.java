package art.arcane.iris.generation.mantle;

import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MantleObjectComponentProceduralChanceTest {
    @Test
    public void exactChanceBoundariesRemainExact() {
        for (int seed = 0; seed < 1000; seed++) {
            RNG rng = new RNG(seed);
            assertFalse(MantleObjectComponent.passesProceduralChance(rng, 0.0));
            assertTrue(MantleObjectComponent.passesProceduralChance(rng, 1.0));
        }
    }
}
