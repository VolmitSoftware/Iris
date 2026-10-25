package art.arcane.iris.generation.hydrology.grid;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CellHashTest {
    private static final long PINNED = 6503863854654667959L;

    @Test
    public void mixIsPinnedAndSensitiveToEveryInput() {
        assertEquals(PINNED, CellHash.mix(1337L, 7L, -3, 12));
        Set<Long> values = new HashSet<>();
        values.add(CellHash.mix(1337L, 7L, -3, 12));
        values.add(CellHash.mix(1338L, 7L, -3, 12));
        values.add(CellHash.mix(1337L, 8L, -3, 12));
        values.add(CellHash.mix(1337L, 7L, -2, 12));
        values.add(CellHash.mix(1337L, 7L, -3, 13));
        values.add(CellHash.mix(1337L, 7L, 12, -3));
        assertEquals(6, values.size());
    }

    @Test
    public void unitStaysInsideTheHalfOpenInterval() {
        for (int x = -500; x < 500; x++) {
            double unit = CellHash.unit(CellHash.mix(1L, 2L, x, -x));
            assertTrue(unit >= 0D && unit < 1D);
        }
        assertEquals(0D, CellHash.unit(0L), 0D);
        assertTrue(CellHash.unit(-1L) < 1D);
    }
}
