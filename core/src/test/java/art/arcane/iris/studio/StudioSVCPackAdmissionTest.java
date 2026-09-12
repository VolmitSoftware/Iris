package art.arcane.iris.studio;

import art.arcane.iris.world.IrisStartupValidation;
import art.arcane.iris.pack.datapack.ServerConfigurator;
import art.arcane.iris.pack.BrokenPackException;
import art.arcane.iris.pack.PackDownloader;
import art.arcane.iris.pack.PackValidationResult;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class StudioSVCPackAdmissionTest {
    @After
    public void disableStartupValidation() {
        IrisStartupValidation.disable();
    }

    @Test
    public void loadablePackIsAdmitted() {
        PackValidationResult validation = new PackValidationResult(
                "overworld", List.of(), List.of(), 1L);

        assertNull(StudioSVC.resolvePackAdmissionFailure(
                "overworld", Optional.empty(), validation));
    }

    @Test
    public void missingValidationFailsClosedWithTheResolvedPackName() {
        BrokenPackException failure = StudioSVC.resolvePackAdmissionFailure(
                "overworld-pack", Optional.empty(), null);

        assertEquals("overworld-pack", failure.getPackName());
        assertEquals(List.of(
                "Required pack validation has not completed. Studio creation fails closed until validation succeeds."),
                failure.getReasons());
    }

    @Test
    public void blockingValidationPreservesEveryReasonInOrder() {
        List<String> reasons = List.of(
                "Biome 'broken' has no resolvable regions.",
                "Structure 'castle' references missing pool 'castle/start'.");
        PackValidationResult validation = new PackValidationResult(
                "overworld", reasons, List.of(), 1L);

        BrokenPackException failure = StudioSVC.resolvePackAdmissionFailure(
                "overworld", Optional.empty(), validation);

        assertEquals("overworld", failure.getPackName());
        assertEquals(reasons, failure.getReasons());
    }

    @Test
    public void startupDenialTakesPriorityOverCachedPackValidation() {
        String denial = "Restart the server after changing external datapacks.";
        PackValidationResult validation = new PackValidationResult(
                "overworld", List.of(), List.of(), 1L);

        BrokenPackException failure = StudioSVC.resolvePackAdmissionFailure(
                "overworld", Optional.of(denial), validation);

        assertEquals(List.of(denial), failure.getReasons());
    }

    @Test
    public void completedPackDownloadRetainsTheCreationGateWithoutDenyingLogin() throws Exception {
        Field restartRequired = ServerConfigurator.class.getDeclaredField("loadedDatapackRestartRequired");
        restartRequired.setAccessible(true);
        boolean previous = restartRequired.getBoolean(null);
        IrisStartupValidation.begin();
        IrisStartupValidation.markDatapacksReady();
        IrisStartupValidation.markPacksReady();

        try {
            StudioSVC.retainPackRestartRequirement(
                    new PackDownloader.PackInstallResult("overworld", true, true));

            assertTrue(ServerConfigurator.worldCreationDenialReason(false).isPresent());
            assertTrue(ServerConfigurator.worldCreationDenialReason(true).isEmpty());
            assertTrue(IrisStartupValidation.denialReason().isEmpty());
        } finally {
            restartRequired.setBoolean(null, previous);
        }
    }
}
