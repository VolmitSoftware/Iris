package art.arcane.iris.studio.object;

import art.arcane.iris.pack.datapack.ServerConfigurator;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mockStatic;

public class StudioOpenCoordinatorCloseSequenceTest {
    @Test
    public void unloadCompletesBeforeGeneratorCloseAndFolderDeletion() {
        ArrayList<String> phases = new ArrayList<>();

        StudioOpenCoordinator.sequenceStudioClose(
                () -> phase(phases, "evacuate"),
                () -> phase(phases, "unload"),
                () -> phase(phases, "close-generator"),
                () -> phase(phases, "delete-folders")
        ).join();

        assertEquals(List.of("evacuate", "unload", "close-generator", "delete-folders"), phases);
    }

    @Test
    public void unloadFailurePreventsGeneratorCloseAndFolderDeletion() {
        ArrayList<String> phases = new ArrayList<>();
        IllegalStateException failure = new IllegalStateException("unload rejected");

        try {
            StudioOpenCoordinator.sequenceStudioClose(
                    () -> phase(phases, "evacuate"),
                    () -> {
                        phases.add("unload");
                        return CompletableFuture.failedFuture(failure);
                    },
                    () -> phase(phases, "close-generator"),
                    () -> phase(phases, "delete-folders")
            ).join();
            fail("Expected unload failure");
        } catch (CompletionException exception) {
            assertSame(failure, exception.getCause());
        }

        assertEquals(List.of("evacuate", "unload"), phases);
    }

    @Test
    public void generatorCloseFailurePreventsFolderDeletion() {
        ArrayList<String> phases = new ArrayList<>();
        IllegalStateException failure = new IllegalStateException("close rejected");

        try {
            StudioOpenCoordinator.sequenceStudioClose(
                    () -> phase(phases, "evacuate"),
                    () -> phase(phases, "unload"),
                    () -> {
                        phases.add("close-generator");
                        return CompletableFuture.failedFuture(failure);
                    },
                    () -> phase(phases, "delete-folders")
            ).join();
            fail("Expected generator close failure");
        } catch (CompletionException exception) {
            assertSame(failure, exception.getCause());
        }

        assertEquals(List.of("evacuate", "unload", "close-generator"), phases);
    }

    @Test
    public void terminalTimeoutPreventsLateUnloadFromClosingOrDeleting() {
        ArrayList<String> phases = new ArrayList<>();
        AtomicBoolean terminalTimeout = new AtomicBoolean(false);
        CompletableFuture<Void> unload = new CompletableFuture<>();

        CompletableFuture<Void> close = StudioOpenCoordinator.sequenceStudioClose(
                () -> phase(phases, "evacuate"),
                () -> {
                    phases.add("unload");
                    return unload;
                },
                () -> phase(phases, "close-generator"),
                () -> phase(phases, "delete-folders"),
                terminalTimeout::get);

        terminalTimeout.set(true);
        unload.complete(null);
        try {
            close.join();
            fail("Expected terminal timeout");
        } catch (CompletionException exception) {
            assertEquals("Studio close stopped after its terminal timeout.", exception.getCause().getMessage());
        }
        assertEquals(List.of("evacuate", "unload"), phases);
    }

    @Test
    public void closeFailureAfterUnloadBeginsRequestsTerminalRestart() {
        try (MockedStatic<ServerConfigurator> configurator = mockStatic(ServerConfigurator.class)) {
            StudioOpenCoordinator.requestRestartAfterPartialClose("studio-world", true);

            configurator.verify(() -> ServerConfigurator.restart(
                    "Studio close failed after world unload began for \"studio-world\"."));
        }
    }

    @Test
    public void closeFailureBeforeUnloadDoesNotRequestRestart() {
        try (MockedStatic<ServerConfigurator> configurator = mockStatic(ServerConfigurator.class)) {
            StudioOpenCoordinator.requestRestartAfterPartialClose("studio-world", false);

            configurator.verifyNoInteractions();
        }
    }

    private static CompletableFuture<Void> phase(List<String> phases, String phase) {
        phases.add(phase);
        return CompletableFuture.completedFuture(null);
    }
}
