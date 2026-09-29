package art.arcane.iris.modded;

import art.arcane.iris.pack.PackValidationRegistry;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolPlayer;
import org.junit.After;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A persistent dimension that cannot be re-injected is skipped, which already writes no chunks. The skip used to be
 * one console line; it has to be a banner and has to reach the operators who can fix it.
 */
public class ModdedStartupReinjectFailureTest {
    @After
    public void resetStartup() {
        ModdedStartup.reset();
        PackValidationRegistry.clear();
    }

    @Test
    public void aDimensionThatFailsToReinjectIsBanneredAndReportedToOperatorsOnJoin() {
        NativeModdedServer server = mock(NativeModdedServer.class);
        ModdedDimensionRegistryStore.PersistentDimension moon =
                new ModdedDimensionRegistryStore.PersistentDimension("iris:moon", "overworld", "overworld", 1337L);
        IllegalStateException failure = new IllegalStateException("Iris generation history is unusable for 'iris:moon'");

        try (MockedStatic<ModdedDimensionRegistryStore> store = mockStatic(ModdedDimensionRegistryStore.class);
             MockedStatic<ModdedDimensionManager> manager = mockStatic(ModdedDimensionManager.class);
             MockedStatic<ModdedIrisLog> log = mockStatic(ModdedIrisLog.class)) {
            store.when(() -> ModdedDimensionRegistryStore.loadForStartup(server)).thenReturn(List.of(moon));
            manager.when(() -> ModdedDimensionManager.restorePersistent(server, "iris:moon", "overworld", "overworld", 1337L))
                    .thenThrow(failure);

            ModdedStartup.reinjectPersistentDimensions(server);

            log.verify(() -> ModdedIrisLog.error(argThat((String line) -> line != null && line.contains("iris:moon")
                    && line.startsWith("Iris refused"))), times(1));
            log.verify(() -> ModdedIrisLog.error(argThat((String line) -> line != null && line.startsWith("Cause: ")
                    && line.contains("generation history is unusable"))), times(1));
        }

        NativeProtocolPlayer operator = mock(NativeProtocolPlayer.class);
        when(operator.isGameMaster()).thenReturn(true);
        NativeProtocolPlayer player = mock(NativeProtocolPlayer.class);
        ModdedStartup.warnStartupFailuresTo(operator);
        ModdedStartup.warnStartupFailuresTo(player);

        verify(operator).sendMessage(argThat((String message) -> message.contains("iris:moon")
                && message.contains("generation history is unusable")));
        verify(player, never()).sendMessage(anyString());

        ModdedStartup.reset();
        NativeProtocolPlayer laterOperator = mock(NativeProtocolPlayer.class);
        when(laterOperator.isGameMaster()).thenReturn(true);
        ModdedStartup.warnStartupFailuresTo(laterOperator);
        verify(laterOperator, never()).sendMessage(any());
    }
}
