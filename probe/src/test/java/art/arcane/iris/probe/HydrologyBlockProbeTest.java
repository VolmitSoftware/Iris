package art.arcane.iris.probe;

import org.junit.Test;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class HydrologyBlockProbeTest {
    @Test
    public void failuresNameMissingSurfaceLeaksTicksAndCoverage() {
        HydrologyBlockProbe.Evidence evidence = new HydrologyBlockProbe.Evidence();
        evidence.scan.missingSurfaces = 2;
        evidence.scan.containmentLeaks = 3;
        evidence.scan.tickConversionCells = 4;
        evidence.channelColumns = 5;
        assertEquals(List.of(
                "Generated blocks miss the planned water surface in 2 columns.",
                "Generated water leaks into open ground in 3 cells.",
                "Fluid ticks would move generated water in 4 cells.",
                "Required generated coverage is absent: pond"), HydrologyBlockProbe.failures(evidence, Set.of("channel", "pond")));
    }
}
