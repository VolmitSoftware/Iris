package art.arcane.iris.engine.mantle.components;

import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ObjectPlacingProceduralChanceTest {
    @Test
    public void exactChanceBoundariesRemainExact() {
        for (int seed = 0; seed < 1000; seed++) {
            RNG rng = new RNG(seed);
            assertFalse(ChunkObjectPlacing.passesProceduralChance(rng, 0.0));
            assertTrue(ChunkObjectPlacing.passesProceduralChance(rng, 1.0));
        }
    }
}
