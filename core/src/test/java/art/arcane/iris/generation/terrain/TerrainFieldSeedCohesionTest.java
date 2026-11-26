package art.arcane.iris.generation.terrain;

import art.arcane.iris.generation.noise.GeneratorRuntime;
import art.arcane.iris.generation.runtime.PackRuntime;
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
    public void cellGeneratorsRetainSeedAssignments() {
        GeneratorRuntime generator = PackRuntime.standalone(null).generator(new IrisGenerator());
        CellGenerator first = generator.cellGenerator(17L);
        CellGenerator second = generator.cellGenerator(91L);
        assertNotSame(first, second);
        assertSame(first, generator.cellGenerator(17L));
        CellGenerator expected = PackRuntime.standalone(null).generator(new IrisGenerator()).cellGenerator(91L);
        assertEquals(expected.getDistance(17D, -81D), second.getDistance(17D, -81D), 0D);
    }
}
