package art.arcane.iris.generation.hydrology.runtime;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IrisHydrologyRuntimeDiagnosticsTest {
    @Test
    public void nonFiniteHeightMessageNamesTheColumnBothSamplesAndTheTerrainBreakdown() {
        String message = IrisHydrologyRuntime.nonFiniteHeightMessage(
                -66,
                -641,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                (int x, int z) -> "region=temperate biome=temperate/plains"
        );

        assertEquals(
                "Hydrology natural height was not finite at -66,-641 (raw=NaN, sampled=Infinity; region=temperate biome=temperate/plains)",
                message
        );
    }

    @Test
    public void nonFiniteHeightMessageSurvivesAFailingDescriber() {
        String message = IrisHydrologyRuntime.nonFiniteHeightMessage(
                1,
                2,
                Double.NaN,
                Double.NaN,
                (int x, int z) -> {
                    throw new IllegalStateException("stream closed");
                }
        );

        assertEquals(
                "Hydrology natural height was not finite at 1,2 (raw=NaN, sampled=NaN; breakdown unavailable: IllegalStateException: stream closed)",
                message
        );
    }
}
