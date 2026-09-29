package art.arcane.iris.modded.command;

import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.modded.localization.ModdedCommandMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
import org.junit.Test;

import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;

public class IrisModdedCommandsTeleportTest {
    private static final String DIMENSION = "irisworldgen:1";

    @Test
    public void failureReportsItsRootCauseInsteadOfAnUnloadedDimension() {
        String message = IrisModdedCommands.teleportFailure(DIMENSION, "Tester",
                new CompletionException(new IllegalStateException("Exception generating new chunk")), true, true);

        assertEquals(IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_TELEPORT_FAILED_REASON,
                MessageArgument.untrusted("dimensionId", DIMENSION),
                MessageArgument.untrusted("reason", "Exception generating new chunk")), message);
    }

    @Test
    public void failureWithoutMessageNamesTheFailureType() {
        String message = IrisModdedCommands.teleportFailure(DIMENSION, "Tester", new TimeoutException(), true, true);

        assertEquals(IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_TELEPORT_FAILED_REASON,
                MessageArgument.untrusted("dimensionId", DIMENSION),
                MessageArgument.untrusted("reason", "TimeoutException")), message);
    }

    @Test
    public void unloadedDimensionIsReportedOnlyWhenItIsGone() {
        assertEquals(IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_TELEPORT_FAILED_DIMENSION_IS_NOT_LOADED,
                        MessageArgument.untrusted("dimensionId", DIMENSION)),
                IrisModdedCommands.teleportFailure(DIMENSION, "Tester", null, false, true));
        assertEquals(IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_TELEPORT_CANCELLED_PLAYER_OFFLINE,
                        MessageArgument.untrusted("dimensionId", DIMENSION),
                        MessageArgument.untrusted("value", "Tester")),
                IrisModdedCommands.teleportFailure(DIMENSION, "Tester", null, true, false));
        assertEquals(IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_TELEPORT_REFUSED,
                        MessageArgument.untrusted("dimensionId", DIMENSION)),
                IrisModdedCommands.teleportFailure(DIMENSION, "Tester", null, true, true));
    }
}
