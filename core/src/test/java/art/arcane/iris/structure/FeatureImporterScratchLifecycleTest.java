package art.arcane.iris.structure;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FeatureImporterScratchLifecycleTest {
    @Test
    public void scratchWorldNamesRequireReservedUuidIdentity() {
        assertTrue(FeatureImporter.isReservedScratchWorldName(
                "iris-feature-import-bac1678e-9bca-4d70-9510-a146566e478c"));
        assertFalse(FeatureImporter.isReservedScratchWorldName("iris_vanilla_import"));
        assertFalse(FeatureImporter.isReservedScratchWorldName("iris-feature-import-not-a-uuid"));
        assertFalse(FeatureImporter.isReservedScratchWorldName(null));
    }

    @Test
    public void confirmedUnloadClosesGeneratorBeforeDeletingFolder() {
        ArrayList<String> phases = new ArrayList<>();

        FeatureImporter.sequenceScratchTeardown(
                () -> {
                    phases.add("unload");
                    return CompletableFuture.completedFuture(true);
                },
                () -> false,
                () -> phase(phases, "close-generator"),
                () -> phase(phases, "delete-folder"),
                "scratch"
        ).join();

        assertEquals(List.of("unload", "close-generator", "delete-folder"), phases);
    }

    @Test
    public void failedUnloadNeverClosesGeneratorOrDeletesFolder() {
        ArrayList<String> phases = new ArrayList<>();

        try {
            FeatureImporter.sequenceScratchTeardown(
                    () -> {
                        phases.add("unload");
                        return CompletableFuture.completedFuture(false);
                    },
                    () -> true,
                    () -> phase(phases, "close-generator"),
                    () -> phase(phases, "delete-folder"),
                    "scratch"
            ).join();
            fail("Expected unload refusal");
        } catch (CompletionException exception) {
            assertTrue(exception.getCause().getMessage().contains("not confirmed"));
        }

        assertEquals(List.of("unload"), phases);
    }

    @Test
    public void identityStillLoadedPreventsGeneratorCloseAndFolderDeletion() {
        ArrayList<String> phases = new ArrayList<>();

        try {
            FeatureImporter.sequenceScratchTeardown(
                    () -> {
                        phases.add("unload");
                        return CompletableFuture.completedFuture(true);
                    },
                    () -> true,
                    () -> phase(phases, "close-generator"),
                    () -> phase(phases, "delete-folder"),
                    "scratch"
            ).join();
            fail("Expected loaded-identity refusal");
        } catch (CompletionException exception) {
            assertTrue(exception.getCause().getMessage().contains("not confirmed"));
        }

        assertEquals(List.of("unload"), phases);
    }

    @Test
    public void generatorCloseFailureNeverDeletesFolder() {
        ArrayList<String> phases = new ArrayList<>();
        IllegalStateException failure = new IllegalStateException("close rejected");

        try {
            FeatureImporter.sequenceScratchTeardown(
                    () -> {
                        phases.add("unload");
                        return CompletableFuture.completedFuture(true);
                    },
                    () -> false,
                    () -> {
                        phases.add("close-generator");
                        return CompletableFuture.failedFuture(failure);
                    },
                    () -> phase(phases, "delete-folder"),
                    "scratch"
            ).join();
            fail("Expected generator close failure");
        } catch (CompletionException exception) {
            assertSame(failure, exception.getCause());
        }

        assertEquals(List.of("unload", "close-generator"), phases);
    }

    @Test
    public void terminalTimeoutPreventsLateUnloadFromClosingOrDeleting() {
        ArrayList<String> phases = new ArrayList<>();
        AtomicBoolean terminalTimeout = new AtomicBoolean(false);
        CompletableFuture<Boolean> unload = new CompletableFuture<>();

        CompletableFuture<Void> cleanup = FeatureImporter.sequenceScratchTeardown(
                () -> {
                    phases.add("unload");
                    return unload;
                },
                () -> false,
                () -> phase(phases, "close-generator"),
                () -> phase(phases, "delete-folder"),
                "scratch",
                terminalTimeout::get);

        terminalTimeout.set(true);
        unload.complete(true);
        try {
            cleanup.join();
            fail("Expected terminal timeout");
        } catch (CompletionException exception) {
            assertEquals("Scratch world cleanup stopped after its terminal timeout.",
                    exception.getCause().getMessage());
        }
        assertEquals(List.of("unload"), phases);
    }

    private static CompletableFuture<Void> phase(List<String> phases, String phase) {
        phases.add(phase);
        return CompletableFuture.completedFuture(null);
    }
}
