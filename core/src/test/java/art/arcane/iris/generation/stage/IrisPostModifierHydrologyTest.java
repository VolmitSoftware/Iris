package art.arcane.iris.generation.stage;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisPostModifierHydrologyTest {
    @Test
    public void columnsTouchingARiverFootprintSkipThePostPasses() {
        assertTrue(IrisPostModifier.skipsHydrologyColumn(new double[] {1D, 0D, 0D, 0D, 0D}));
        assertTrue(IrisPostModifier.skipsHydrologyColumn(new double[] {0D, 0D, 0.5D, 0D, 0D}));
        assertTrue(IrisPostModifier.skipsHydrologyColumn(new double[] {0D, 0D, 0D, 0D, 0.75D}));
    }

    @Test
    public void columnsAwayFromRiversKeepThePostPasses() {
        assertFalse(IrisPostModifier.skipsHydrologyColumn(new double[] {0D, 0D, 0D, 0D, 0D}));
    }
}
