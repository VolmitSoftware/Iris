package art.arcane.iris.generation.terrain;

import art.arcane.iris.generation.noise.IrisGenerator;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import art.arcane.volmlib.util.noise.CellGenerator;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

public class TerrainFieldSeedCohesionTest {
    @Test
    public void coordinateFracturesSeparateSeedAndSignature() {
        IrisDimension shared = new IrisDimension();
        for (long seed : new long[]{91L, 17L, 91L}) {
            for (int signature : new int[]{73, 211}) {
                CNG expected = new IrisDimension().getCoordFracture(new RNG(seed), signature);
                CNG actual = shared.getCoordFracture(new RNG(seed), signature);
                for (int index = 0; index < 8; index++) {
                    assertEquals(expected.noise(index * 128D - 600D, index * -251D + 300D),
                            actual.noise(index * 128D - 600D, index * -251D + 300D), 0D);
                }
            }
        }
    }

    @Test
    public void cellGeneratorsRetainSeedAssignments() {
        IrisGenerator generator = new IrisGenerator();
        CellGenerator first = generator.getCellGenerator(17L);
        CellGenerator second = generator.getCellGenerator(91L);
        assertNotSame(first, second);
        assertSame(first, generator.getCellGenerator(17L));
        CellGenerator expected = new IrisGenerator().getCellGenerator(91L);
        assertEquals(expected.getDistance(17D, -81D), second.getDistance(17D, -81D), 0D);
    }
}
