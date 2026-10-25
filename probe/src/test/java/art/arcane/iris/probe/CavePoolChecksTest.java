package art.arcane.iris.probe;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class CavePoolChecksTest {
    @Test
    public void everyContainmentCountFailsIncludingForeignFluidContacts() {
        CavePoolProbe.Counts foreign = counts(0, 0, 0, 1, 0, 0, 0, 0);
        assertEquals("a pool touching another fluid fails as the standalone probe does", 1L, foreign.failures());
        assertEquals(36L, counts(1, 2, 3, 4, 5, 6, 7, 8).failures());
        assertEquals(0L, counts(0, 0, 0, 0, 0, 0, 0, 0).failures());
    }

    private static CavePoolProbe.Counts counts(int air, int open, int sky, int foreign, int waterlogged, int gravity, int fluidAbove,
                                               int gravityCeilings) {
        return new CavePoolProbe.Counts(1, 1, 1, 1, Map.of(), List.of(), List.of(), Map.of(), air, open, sky, foreign, waterlogged, gravity,
                fluidAbove, gravityCeilings, Map.of(), List.of());
    }
}
