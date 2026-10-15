package art.arcane.iris.modded;

import art.arcane.iris.pack.PackValidationRegistry;
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
        ModdedDimensionRegistryStore.PersistentDimension moon =
                new ModdedDimensionRegistryStore.PersistentDimension("iris:moon", "overworld", "overworld", 1337L);
        IllegalStateException failure = new IllegalStateException("Iris generation history is unusable for 'iris:moon'");

        try (MockedStatic<ModdedIrisLog> log = mockStatic(ModdedIrisLog.class)) {
            ModdedStartup.finishDimensionRestore(ModdedDimensionInjection.start(List.of(moon), new FailingSteps(failure)));

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
        NativeProtocolPlayer owner = mock(NativeProtocolPlayer.class);
        when(owner.isServerOwner()).thenReturn(true);
        ModdedStartup.warnStartupFailuresTo(owner);
        verify(owner).sendMessage(argThat((String message) -> message.contains("iris:moon")
                && message.contains("generation history is unusable")));

        verify(operator).sendMessage(argThat((String message) -> message.contains("iris:moon")
                && message.contains("generation history is unusable")));
        verify(player, never()).sendMessage(anyString());

        ModdedStartup.reset();
        NativeProtocolPlayer laterOperator = mock(NativeProtocolPlayer.class);
        when(laterOperator.isGameMaster()).thenReturn(true);
        ModdedStartup.warnStartupFailuresTo(laterOperator);
        verify(laterOperator, never()).sendMessage(any());
    }

    private record FailingSteps(RuntimeException failure) implements ModdedDimensionInjection.Steps<String, String, String> {
        @Override
        public String prepare(ModdedDimensionRegistryStore.PersistentDimension dimension) {
            throw failure;
        }

        @Override
        public String begin(ModdedDimensionRegistryStore.PersistentDimension dimension, String prepared) {
            throw new AssertionError("A failed preparation must not create a level");
        }

        @Override
        public String build(String level, String prepared) {
            throw new AssertionError("A failed preparation must not build an engine");
        }

        @Override
        public void complete(String level, String engine) {
            throw new AssertionError("A failed preparation must not publish a level");
        }

        @Override
        public void abandon(String level, String engine, Throwable failure) {
            throw new AssertionError("A failed preparation has no level to abandon");
        }

        @Override
        public void discard(String prepared) {
        }
    }
}
