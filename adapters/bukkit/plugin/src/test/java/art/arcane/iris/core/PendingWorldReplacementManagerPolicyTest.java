package art.arcane.iris.core;

import art.arcane.iris.Iris;
import art.arcane.iris.core.ExactWorldSlotPathPolicy.SlotKind;
import art.arcane.iris.core.lifecycle.WorldReplacementBootstrapMarker;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.IrisEnvironment;
import art.arcane.iris.util.common.plugin.VolmitSender;
import org.bukkit.NamespacedKey;
import org.junit.After;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

public class PendingWorldReplacementManagerPolicyTest {
    @After
    public void disableValidation() {
        IrisStartupValidation.disable();
    }

    @Test
    public void irisManagedSlotsAllowEveryReplacementEnvironment() {
        for (IrisEnvironment environment : IrisEnvironment.values()) {
            PendingWorldReplacementManager.requireCompatibleEnvironment(SlotKind.IRIS_MANAGED, environment);
        }
    }

    @Test
    public void vanillaSlotsRequireTheirIdentityEnvironment() {
        List<EnvironmentExpectation> expectations = List.of(
                new EnvironmentExpectation(SlotKind.VANILLA_OVERWORLD, IrisEnvironment.NORMAL),
                new EnvironmentExpectation(SlotKind.VANILLA_NETHER, IrisEnvironment.NETHER),
                new EnvironmentExpectation(SlotKind.VANILLA_END, IrisEnvironment.THE_END)
        );

        for (EnvironmentExpectation expectation : expectations) {
            for (IrisEnvironment environment : IrisEnvironment.values()) {
                if (environment == expectation.environment()) {
                    PendingWorldReplacementManager.requireCompatibleEnvironment(
                            expectation.slotKind(),
                            environment
                    );
                    continue;
                }
                assertThrows(
                        IllegalArgumentException.class,
                        () -> PendingWorldReplacementManager.requireCompatibleEnvironment(
                                expectation.slotKind(),
                                environment
                        )
                );
            }
        }
    }

    @Test
    public void disabledVanillaSlotsFailWithoutRestrictingOtherSlots() throws Exception {
        PendingWorldReplacementManager.requireVanillaSlotEnabled(SlotKind.IRIS_MANAGED, false, false);
        PendingWorldReplacementManager.requireVanillaSlotEnabled(SlotKind.VANILLA_OVERWORLD, false, false);
        PendingWorldReplacementManager.requireVanillaSlotEnabled(SlotKind.VANILLA_NETHER, true, false);
        PendingWorldReplacementManager.requireVanillaSlotEnabled(SlotKind.VANILLA_END, false, true);

        IOException netherFailure = assertThrows(
                IOException.class,
                () -> PendingWorldReplacementManager.requireVanillaSlotEnabled(
                        SlotKind.VANILLA_NETHER,
                        false,
                        true
                )
        );
        IOException endFailure = assertThrows(
                IOException.class,
                () -> PendingWorldReplacementManager.requireVanillaSlotEnabled(
                        SlotKind.VANILLA_END,
                        true,
                        false
                )
        );

        assertEquals(
                "allow-nether must be true before the vanilla Nether can be replaced.",
                netherFailure.getMessage()
        );
        assertEquals(
                "Bukkit allow-end must be true before the vanilla End can be replaced.",
                endFailure.getMessage()
        );
    }

    @Test
    public void replacementStagingPassesTheRestartBoundaryWithoutUnlockingCreation() {
        IrisStartupValidation.begin();
        IrisStartupValidation.markDatapacksReady();
        IrisStartupValidation.markPacksReady();
        IrisStartupValidation.requireRestart("restart boundary");
        PendingWorldReplacementManager manager = new PendingWorldReplacementManager(mock(Iris.class));
        VolmitSender sender = mock(VolmitSender.class);
        IrisDimension dimension = mock(IrisDimension.class);

        assertThrows(IllegalStateException.class, IrisStartupValidation::requireWorldCreationReady);
        try (MockedStatic<WorldReplacementBootstrapMarker> bootstrapMarker =
                     mockStatic(WorldReplacementBootstrapMarker.class)) {
            bootstrapMarker.when(WorldReplacementBootstrapMarker::wasBootstrappedThisProcess).thenReturn(false);

            IOException failure = assertThrows(
                    IOException.class,
                    () -> manager.stageReplacement(sender, NamespacedKey.minecraft("the_nether"), dimension, null)
            );

            assertEquals(
                    "Exact world replacement requires a full Paper-family startup bootstrap.",
                    failure.getMessage()
            );
        }
    }

    private record EnvironmentExpectation(SlotKind slotKind, IrisEnvironment environment) {
    }
}
