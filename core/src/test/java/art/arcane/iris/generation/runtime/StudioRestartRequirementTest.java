package art.arcane.iris.generation.runtime;

import art.arcane.iris.world.history.RegistryRestartRequiredException;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class StudioRestartRequirementTest {
    @Test
    public void noDeferredBiomesMeansNothingIsPending() {
        StudioRestartRequirement requirement = StudioRestartRequirement.deferring(List.of());

        assertFalse(requirement.isPending());
        assertFalse(requirement.blocksHotload());
    }

    @Test
    public void summaryListsBlockingBiomesFirstAndCountsTheRest() {
        StudioRestartRequirement requirement = StudioRestartRequirement.blockedBy(
                new RegistryRestartRequiredException(List.of("ash"), List.of("mist", "dunes", "fog", "ash")));

        assertTrue(requirement.isPending());
        assertTrue(requirement.blocksHotload());
        assertEquals("ash, mist, dunes +1", requirement.biomeSummary());
    }

    @Test
    public void deferredBiomesHotloadWithoutBlocking() {
        StudioRestartRequirement requirement = StudioRestartRequirement.deferring(List.of("mist"));

        assertTrue(requirement.isPending());
        assertFalse(requirement.blocksHotload());
        assertEquals("mist", requirement.biomeSummary());
    }
}
