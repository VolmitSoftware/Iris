package art.arcane.iris.probe;

import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SeaInletChecksTest {
    @Test
    public void roofBreachesFailWithTheContainmentSealAndRoofChecks() {
        SeaInletProbe.Checks breached = new SeaInletProbe.Checks(3, 0L, 0L, 0L, 0L, 1939L, 0L, 0L, 0L, 0L, 0L, List.of());
        assertEquals(1939L, breached.failures());
        SeaInletProbe.Checks failing = new SeaInletProbe.Checks(3, 1L, 2L, 3L, 4L, 9L, 5L, 6L, 7L, 8L, 0L, List.of());
        assertEquals(45L, failing.failures());
    }

    @Test
    public void looseBedsAreReportedButDoNotFail() {
        SeaInletProbe.Checks reported = new SeaInletProbe.Checks(3, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 952L, List.of());
        assertEquals(0L, reported.failures());
    }

    @Test
    public void theGateFailsOnEveryCheckIncludingOrderSeaLevelAndAddedLeaks() {
        SeaInletProbe.Checks clean = new SeaInletProbe.Checks(3, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 952L, List.of());
        SeaInletProbe.Gate passing = new SeaInletProbe.Gate(clean, 0, 0, 0);
        assertEquals(0L, passing.failures());
        assertTrue(passing.line(), passing.line().startsWith("SEA_INLET_GATE status=PASS failures=0 "));
        assertEquals(2L, new SeaInletProbe.Gate(clean, 0, 0, 2).failures());
        assertEquals(1L, new SeaInletProbe.Gate(clean, 1, 0, 0).failures());
        assertEquals(1L, new SeaInletProbe.Gate(clean, 0, 1, 0).failures());
        SeaInletProbe.Checks breached = new SeaInletProbe.Checks(3, 0L, 0L, 0L, 0L, 4L, 0L, 0L, 0L, 0L, 0L, List.of());
        SeaInletProbe.Gate failing = new SeaInletProbe.Gate(breached, 0, 0, 0);
        assertEquals(4L, failing.failures());
        assertTrue(failing.line(), failing.line().startsWith("SEA_INLET_GATE status=FAIL failures=4 "));
    }

    @Test
    public void addedLeaksAreTheLeaksMissingWithoutInlets() {
        long kept = SeaInletProbe.cellKey(10, 300, -20);
        long removed = SeaInletProbe.cellKey(11, 300, -20);
        long added = SeaInletProbe.cellKey(10, 301, -20);
        assertEquals(0, SeaInletProbe.addedLeaks(Set.of(kept), Set.of(kept)));
        assertEquals("inlets that remove natural leaks do not fail", 0, SeaInletProbe.addedLeaks(Set.of(kept), Set.of(kept, removed)));
        assertEquals("a leak the inlets move counts although the totals match", 1, SeaInletProbe.addedLeaks(Set.of(kept, added),
                Set.of(kept, removed)));
        assertEquals(2, SeaInletProbe.addedLeaks(Set.of(added, removed), Set.of()));
        assertNotEquals(SeaInletProbe.cellKey(-1, 300, 0), SeaInletProbe.cellKey(0, 300, -1));
        assertNotEquals(SeaInletProbe.cellKey(5, 0, 5), SeaInletProbe.cellKey(5, 4095, 5));
    }

    @Test
    public void inletCellsAreClassifiedAgainstTheDimensionSeaFluid() {
        assertEquals(SeaInletProbe.classify(state("minecraft:water"), "minecraft:water"), SeaInletProbe.classify(state("minecraft:lava"),
                "minecraft:lava"));
        assertEquals(SeaInletProbe.classify(state("minecraft:water[level=8]"), "minecraft:water"),
                SeaInletProbe.classify(state("minecraft:lava[level=8]"), "minecraft:lava"));
        assertEquals(SeaInletProbe.classify(state("minecraft:lava"), "minecraft:water"), SeaInletProbe.classify(state("minecraft:water"),
                "minecraft:lava"));
        assertNotEquals(SeaInletProbe.classify(state("minecraft:lava"), "minecraft:lava"), SeaInletProbe.classify(state("minecraft:lava"),
                "minecraft:water"));
    }

    private static NativeBlockState state(String key) {
        NativeBlockState state = mock(NativeBlockState.class);
        when(state.key()).thenReturn(key);
        return state;
    }
}
