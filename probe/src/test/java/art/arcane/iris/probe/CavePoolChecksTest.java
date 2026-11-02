package art.arcane.iris.probe;

import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CavePoolChecksTest {
    @Test
    public void everyContainmentCountFailsIncludingForeignFluidContacts() {
        CavePoolProbe.Counts foreign = counts(0, 0, 0, 1, 0, 0, 0, 0);
        assertEquals("a pool touching another fluid fails as the standalone probe does", 1L, foreign.failures());
        assertEquals(36L, counts(1, 2, 3, 4, 5, 6, 7, 8).failures());
        assertEquals(0L, counts(0, 0, 0, 0, 0, 0, 0, 0).failures());
    }

    @Test
    public void theGateFailsOnContainmentAndOrderMismatches() {
        CavePoolProbe.Gate passing = new CavePoolProbe.Gate(counts(0, 0, 0, 0, 0, 0, 0, 0), 0);
        assertEquals(0L, passing.failures());
        assertTrue(passing.line(), passing.line().startsWith("CAVE_POOL_GATE status=PASS failures=0 "));
        assertEquals(2L, new CavePoolProbe.Gate(counts(0, 0, 0, 0, 0, 0, 0, 0), 2).failures());
        CavePoolProbe.Gate failing = new CavePoolProbe.Gate(counts(1, 2, 3, 4, 5, 6, 7, 8), 1);
        assertEquals(37L, failing.failures());
        assertTrue(failing.line(), failing.line().startsWith("CAVE_POOL_GATE status=FAIL failures=37 "));
    }

    @Test
    public void poolCellsThatDidNotStayStillSourcesOfTheirFluidFail() {
        CavePoolProbe.Counts replaced = new CavePoolProbe.Counts(1, 1, 1, 1, Map.of(), List.of(), List.of(), Map.of(), 0, 0, 0, 0, 0, 0, 0, 0,
                Map.of("water->minecraft:stone", 2, "lava->null", 1), List.of());
        assertEquals(3L, replaced.failures());
        assertEquals(3L, new CavePoolProbe.Gate(replaced, 0).failures());
    }

    @Test
    public void poolSourcesMatchTheFluidTheCellResolvesTo() {
        NativeBlockState water = fluid("minecraft:water", true);
        NativeBlockState lava = fluid("minecraft:lava", false);
        assertTrue(CavePoolProbe.source(fluid("minecraft:water", true), water));
        assertTrue(CavePoolProbe.source(fluid("minecraft:water[level=0]", true), water));
        assertFalse("flowing water is not a pool source", CavePoolProbe.source(fluid("minecraft:water[level=1]", true), water));
        assertFalse("lava is not a water pool source", CavePoolProbe.source(fluid("minecraft:lava", false), water));
        assertTrue(CavePoolProbe.source(fluid("minecraft:lava[level=0]", false), lava));
        assertFalse(CavePoolProbe.source(fluid("minecraft:water", true), lava));
        assertFalse(CavePoolProbe.source(null, water));
        NativeBlockState stone = mock(NativeBlockState.class);
        when(stone.key()).thenReturn("minecraft:stone");
        assertFalse(CavePoolProbe.source(stone, water));
    }

    @Test
    public void orderMismatchesCountChunksWhoseHashesDiffer() {
        assertEquals(0, CavePoolProbe.compare(Map.of(1L, 10L, 2L, 20L), Map.of(2L, 20L, 1L, 10L)));
        assertEquals(1, CavePoolProbe.compare(Map.of(1L, 10L, 2L, 20L), Map.of(1L, 10L, 2L, 21L)));
        assertEquals("a chunk missing from the shuffled run is a mismatch", 1, CavePoolProbe.compare(Map.of(1L, 10L, 2L, 20L), Map.of(1L, 10L)));
    }

    private static CavePoolProbe.Counts counts(int air, int open, int sky, int foreign, int waterlogged, int gravity, int fluidAbove,
                                               int gravityCeilings) {
        return new CavePoolProbe.Counts(1, 1, 1, 1, Map.of(), List.of(), List.of(), Map.of(), air, open, sky, foreign, waterlogged, gravity,
                fluidAbove, gravityCeilings, Map.of(), List.of());
    }

    private static NativeBlockState fluid(String key, boolean water) {
        NativeBlockState state = mock(NativeBlockState.class);
        when(state.key()).thenReturn(key);
        when(state.isFluid()).thenReturn(true);
        when(state.isWater()).thenReturn(water);
        return state;
    }
}
