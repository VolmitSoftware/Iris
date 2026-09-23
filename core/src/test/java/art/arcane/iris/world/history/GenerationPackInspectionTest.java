package art.arcane.iris.world.history;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mockStatic;

public class GenerationPackInspectionTest extends GenerationHistorySupport {
    @Test
    public void inspectionVerifiesEachSelectedPackOnce() throws Exception {
        Path world = temporaryFolder.newFolder("world").toPath();
        Path pack = createPack("pack", "alpha");
        Path update = createPack("update", "beta");
        GenerationHistory history = createHistory(world, pack);
        GenerationActivation pending = stage(history, update);
        AtomicInteger hashes = new AtomicInteger();
        try (MockedStatic<GenerationPackFingerprint> ignored = mockStatic(GenerationPackFingerprint.class, invocation -> {
            if (invocation.getMethod().getName().equals("compute")) {
                hashes.incrementAndGet();
            }
            return invocation.callRealMethod();
        })) {
            GenerationHistory.PackInspection inspection = GenerationHistory.inspectPacks(world);
            assertEquals(history.activeEpoch(), inspection.manifest().activeEpoch());
            assertEquals(history.paths().packRoot(history.activeEpoch().epochId()), inspection.activePackRoot());
            assertEquals(history.paths().packRoot(pending.epochId()), inspection.pendingPackRoot().orElseThrow());
            assertEquals(2, hashes.get());
        }
    }

    @Test
    public void inspectionDoesNotReplaceRuntimeSemanticValidation() throws Exception {
        Path world = temporaryFolder.newFolder("world").toPath();
        GenerationHistory history = createHistory(world, createPack("pack", "alpha"));
        Files.delete(history.paths().generationRoot().resolve("semantics/index.isix"));

        GenerationHistory.PackInspection inspection = GenerationHistory.inspectPacks(world);

        assertEquals(history.activeEpoch(), inspection.manifest().activeEpoch());
        assertFalse(inspection.pendingPackRoot().isPresent());
        assertThrows(IOException.class, () -> GenerationHistory.open(world));
    }

    @Test
    public void laterInspectionsRejectModifiedActivePacks() throws Exception {
        Path world = temporaryFolder.newFolder("world").toPath();
        GenerationHistory history = createHistory(world, createPack("pack", "alpha"));
        GenerationHistory.PackInspection inspection = GenerationHistory.inspectPacks(world);
        Files.writeString(inspection.activePackRoot().resolve("dimensions/main.json"), "changed");

        assertThrows(IOException.class, () -> GenerationHistory.inspectPacks(world));
        assertThrows(IOException.class, history::activePackRoot);
        assertThrows(IOException.class, () -> GenerationHistory.open(world));
    }

    @Test
    public void inspectionRejectsModifiedPendingPacks() throws Exception {
        Path world = temporaryFolder.newFolder("world").toPath();
        GenerationHistory history = createHistory(world, createPack("pack", "alpha"));
        GenerationActivation pending = stage(history, createPack("update", "beta"));
        Files.writeString(history.paths().packRoot(pending.epochId()).resolve("dimensions/main.json"), "changed");

        assertThrows(IOException.class, () -> GenerationHistory.inspectPacks(world));
    }

    @Test
    public void inspectionRejectsMalformedManifest() throws Exception {
        Path world = temporaryFolder.newFolder("world").toPath();
        GenerationHistory history = createHistory(world, createPack("pack", "alpha"));
        Files.writeString(history.paths().manifest(), "{}");

        assertThrows(IOException.class, () -> GenerationHistory.inspectPacks(world));
    }
}
