package art.arcane.iris.world;

import org.junit.After;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class IrisStartupValidationTest {
    @After
    public void disableValidation() {
        IrisStartupValidation.disable();
    }

    @Test
    public void disabledValidationAllowsCreation() {
        IrisStartupValidation.disable();

        assertTrue(IrisStartupValidation.isReady());
        assertFalse(IrisStartupValidation.isRestartRequired());
        assertTrue(IrisStartupValidation.denialReason().isEmpty());
        IrisStartupValidation.requireWorldCreationReady();
    }

    @Test
    public void pendingValidationDeniesCreation() {
        IrisStartupValidation.begin();

        assertFalse(IrisStartupValidation.isReady());
        assertFalse(IrisStartupValidation.isRestartRequired());
        assertTrue(IrisStartupValidation.denialReason().orElseThrow().contains("external datapacks"));
        try {
            IrisStartupValidation.requireWorldCreationReady();
            fail("Expected pending startup validation to lock world creation");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("world creation is locked"));
        }
    }

    @Test
    public void bothValidationPhasesMustComplete() {
        IrisStartupValidation.begin();
        IrisStartupValidation.markDatapacksReady();

        assertFalse(IrisStartupValidation.isReady());
        assertTrue(IrisStartupValidation.denialReason().orElseThrow().contains("dimension packs"));

        IrisStartupValidation.markPacksReady();

        assertTrue(IrisStartupValidation.isReady());
        assertTrue(IrisStartupValidation.denialReason().isEmpty());
    }

    @Test
    public void invalidDatapacksExposeTheFailure() {
        IrisStartupValidation.begin();
        IrisStartupValidation.markDatapacksInvalid("broken managed datapack");
        IrisStartupValidation.markPacksReady();

        assertEquals("broken managed datapack", IrisStartupValidation.denialReason().orElseThrow());
    }

    @Test
    public void restartRequirementCannotBeDowngradedToReady() {
        IrisStartupValidation.begin();
        IrisStartupValidation.requireRestart("restart required for registry load");
        IrisStartupValidation.markDatapacksReady();
        IrisStartupValidation.markPacksReady();

        assertFalse(IrisStartupValidation.isReady());
        assertTrue(IrisStartupValidation.isRestartRequired());
        assertEquals("restart required for registry load", IrisStartupValidation.denialReason().orElseThrow());
    }

    @Test
    public void repeatedValidationCannotClearRestartRequirement() {
        IrisStartupValidation.begin();
        IrisStartupValidation.requireRestart("restart boundary");

        IrisStartupValidation.beginDatapackValidation();
        IrisStartupValidation.markDatapacksReady();
        IrisStartupValidation.markPacksReady();

        assertFalse(IrisStartupValidation.isReady());
        assertEquals("restart boundary", IrisStartupValidation.denialReason().orElseThrow());
    }

    @Test
    public void restartBoundaryAllowsReplacementStagingWithoutAllowingRuntimeCreation() {
        IrisStartupValidation.begin();
        IrisStartupValidation.markDatapacksReady();
        IrisStartupValidation.markPacksReady();
        IrisStartupValidation.requireRestart("restart boundary");

        assertThrows(IllegalStateException.class, IrisStartupValidation::requireWorldCreationReady);
        IrisStartupValidation.requireWorldReplacementStagingReady();

        IrisStartupValidation.markPacksInvalid(List.of("pack validation failed"));
        assertThrows(
                IllegalStateException.class,
                IrisStartupValidation::requireWorldReplacementStagingReady
        );
    }

    @Test
    public void forceAllowsOnlyStudioAtACompletedRestartBoundary() {
        IrisStartupValidation.begin();
        IrisStartupValidation.markDatapacksReady();
        IrisStartupValidation.markPacksReady();
        IrisStartupValidation.requireRestart("restart boundary");

        assertTrue(IrisStartupValidation.studioDenialReason(true).isEmpty());
        assertThrows(IllegalStateException.class, IrisStartupValidation::requireWorldCreationReady);

        IrisStartupValidation.markPacksInvalid(List.of("pack validation failed"));
        assertFalse(IrisStartupValidation.studioDenialReason(true).isEmpty());
    }

    @Test
    public void packValidationInfrastructureFailureDeniesCreation() {
        IrisStartupValidation.begin();
        IrisStartupValidation.markDatapacksReady();
        IrisStartupValidation.markPacksInvalid(List.of("pack registry unavailable"));

        assertEquals("pack registry unavailable", IrisStartupValidation.denialReason().orElseThrow());
    }

    @Test
    public void runtimeValidationMustCompleteBeforeCreation() {
        IrisStartupValidation.begin();
        IrisStartupValidation.markDatapacksReady();
        IrisStartupValidation.markPacksReady();
        IrisStartupValidation.beginRuntimeValidation();

        assertFalse(IrisStartupValidation.isReady());
        assertEquals("Iris is still validating runtime injection.", IrisStartupValidation.denialReason().orElseThrow());
        assertThrows(IllegalStateException.class, IrisStartupValidation::requireWorldCreationReady);

        IrisStartupValidation.markRuntimeReady();

        assertTrue(IrisStartupValidation.isReady());
        IrisStartupValidation.requireWorldCreationReady();
    }

    @Test
    public void packRevalidationCannotClearRuntimeFailure() {
        IrisStartupValidation.begin();
        IrisStartupValidation.beginRuntimeValidation();
        IrisStartupValidation.markRuntimeInvalid("Java agent unavailable");
        IrisStartupValidation.markDatapacksReady();
        IrisStartupValidation.markPacksReady();
        IrisStartupValidation.beginDatapackValidation();
        IrisStartupValidation.markDatapacksInvalid("temporary datapack failure");
        IrisStartupValidation.markDatapacksReady();
        IrisStartupValidation.markPacksInvalid(List.of("temporary pack failure"));
        IrisStartupValidation.markPacksReady();

        assertFalse(IrisStartupValidation.isReady());
        assertEquals("Java agent unavailable", IrisStartupValidation.denialReason().orElseThrow());
    }

    @Test
    public void forcedStudioAndReplacementCannotBypassRuntimeFailure() {
        IrisStartupValidation.begin();
        IrisStartupValidation.markDatapacksReady();
        IrisStartupValidation.markPacksReady();
        IrisStartupValidation.requireRestart("restart boundary");
        IrisStartupValidation.beginRuntimeValidation();

        assertFalse(IrisStartupValidation.studioDenialReason(true).isEmpty());
        assertThrows(IllegalStateException.class, IrisStartupValidation::requireWorldReplacementStagingReady);

        IrisStartupValidation.markRuntimeInvalid("Java agent unavailable");

        assertEquals("Java agent unavailable", IrisStartupValidation.studioDenialReason(true).orElseThrow());
        assertThrows(IllegalStateException.class, IrisStartupValidation::requireWorldReplacementStagingReady);

        IrisStartupValidation.markRuntimeReady();

        assertTrue(IrisStartupValidation.studioDenialReason(true).isEmpty());
        IrisStartupValidation.requireWorldReplacementStagingReady();
        assertThrows(IllegalStateException.class, IrisStartupValidation::requireWorldCreationReady);
    }

    @Test
    public void repeatedRuntimeValidationRevokesPreviousReadiness() {
        IrisStartupValidation.begin();
        IrisStartupValidation.markDatapacksReady();
        IrisStartupValidation.markPacksReady();
        IrisStartupValidation.beginRuntimeValidation();
        IrisStartupValidation.markRuntimeReady();
        IrisStartupValidation.beginRuntimeValidation();

        assertFalse(IrisStartupValidation.isReady());
        assertThrows(IllegalStateException.class, IrisStartupValidation::requireWorldCreationReady);

        IrisStartupValidation.markRuntimeInvalid("injection retry failed");

        assertEquals("injection retry failed", IrisStartupValidation.denialReason().orElseThrow());
    }

    @Test
    public void runtimeFailureCannotEnableDisabledValidation() {
        IrisStartupValidation.disable();
        IrisStartupValidation.beginRuntimeValidation();
        IrisStartupValidation.markRuntimeInvalid("Java agent unavailable");

        assertTrue(IrisStartupValidation.isReady());
        IrisStartupValidation.requireWorldCreationReady();
    }
}
