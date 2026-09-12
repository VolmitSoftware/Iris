package art.arcane.iris.generation.hydrology.cave;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CavePositionIndexTest {
    @Test
    public void preservesDistinctFullIntegerCoordinatesAcrossGrowth() {
        CavePositionIndex index = new CavePositionIndex(1);
        for (int value = -128; value <= 128; value++) {
            assertTrue(index.add(value, value * 31, value * -17));
        }
        assertTrue(index.add(Integer.MIN_VALUE, Integer.MAX_VALUE, 0));
        assertTrue(index.add(Integer.MAX_VALUE, Integer.MIN_VALUE, -1));

        for (int value = -128; value <= 128; value++) {
            assertTrue(index.contains(value, value * 31, value * -17));
            assertFalse(index.add(value, value * 31, value * -17));
        }
        assertTrue(index.contains(Integer.MIN_VALUE, Integer.MAX_VALUE, 0));
        assertTrue(index.contains(Integer.MAX_VALUE, Integer.MIN_VALUE, -1));
        assertFalse(index.contains(Integer.MIN_VALUE, Integer.MAX_VALUE, 1));
    }

    @Test
    public void copiedIndexMatchesOnlyExactTriples() {
        CavePositionIndex index = CavePositionIndex.copyOf(List.of(
                new CavePosition(1, 2, 3),
                new CavePosition(3, 2, 1)
        ));

        assertTrue(index.contains(1, 2, 3));
        assertTrue(index.contains(3, 2, 1));
        assertFalse(index.contains(1, 3, 2));
    }
}
