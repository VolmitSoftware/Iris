package art.arcane.iris.probe;

import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SeaInletChecksTest {
    @Test
    public void roofBreachesAndLooseBedsAreReportedButOnlyContainmentAndSealChecksFail() {
        SeaInletProbe.Checks reported = new SeaInletProbe.Checks(3, 0L, 0L, 0L, 0L, 1939L, 0L, 0L, 0L, 0L, 952L, List.of());
        assertEquals(0L, reported.failures());
        SeaInletProbe.Checks failing = new SeaInletProbe.Checks(3, 1L, 2L, 3L, 4L, 0L, 5L, 6L, 7L, 8L, 0L, List.of());
        assertEquals(36L, failing.failures());
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
