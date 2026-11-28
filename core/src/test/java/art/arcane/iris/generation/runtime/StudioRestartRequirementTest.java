package art.arcane.iris.generation.runtime;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class StudioRestartRequirementTest {
    @Test
    public void noPendingBiomesMeansNothingIsPending() {
        assertFalse(StudioRestartRequirement.pending(List.of()).isPending());
    }

    @Test
    public void summaryListsTheFirstBiomesAndCountsTheRest() {
        StudioRestartRequirement requirement = StudioRestartRequirement.pending(List.of("ash", "dunes", "fog", "mist"));

        assertTrue(requirement.isPending());
        assertEquals("ash, dunes, fog +1", requirement.biomeSummary());
    }
}
