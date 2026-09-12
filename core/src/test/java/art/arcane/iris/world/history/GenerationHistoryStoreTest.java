package art.arcane.iris.world.history;

import art.arcane.iris.testsupport.DurabilityMode;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class GenerationHistoryStoreTest {
    @ClassRule
    public static final DurabilityMode DURABILITY = DurabilityMode.relaxed();

    private static final String PACK_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String PACK_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(123456789L), ZoneOffset.UTC);

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void persistsActiveAndPendingHistoryAcrossReopen() throws Exception {
        Path directory = temporaryFolder.getRoot().toPath().resolve("generation");
        GenerationEpoch epochA = epoch(PACK_A);
        GenerationEpoch epochB = epoch(PACK_B);
        GenerationHistoryStore store = GenerationHistoryStore.initialize(directory, epochA, CLOCK);

        GenerationActivation pending = store.preparePendingActivation(epochB, 512);
        GenerationHistoryStore reopenedPending = GenerationHistoryStore.open(directory, CLOCK);

        assertEquals(epochA, reopenedPending.activeEpoch());
        assertEquals(epochB, reopenedPending.pendingEpoch().orElseThrow());
        assertEquals(2L, pending.activationId());
        assertEquals(Long.valueOf(1L), pending.parentActivationId());

        reopenedPending.completePendingTransition(pending.activationId(), digest('d'), digest('e'));
        reopenedPending.activatePending(pending.activationId());
        GenerationHistoryStore reopenedActive = GenerationHistoryStore.open(directory, CLOCK);

        assertEquals(epochB, reopenedActive.activeEpoch());
        assertEquals(2L, reopenedActive.activeActivation().activationId());
        assertFalse(reopenedActive.pendingActivation().isPresent());
        assertEquals(2, reopenedActive.manifest().epochs().size());
        assertEquals(2, reopenedActive.manifest().activations().size());
    }

    @Test
    public void repeatedPreparationDoesNotRewriteOrRenumberPendingActivation() throws Exception {
        Path directory = temporaryFolder.getRoot().toPath().resolve("generation");
        GenerationHistoryStore store = GenerationHistoryStore.initialize(directory, epoch(PACK_A), CLOCK);
        GenerationEpoch epochB = epoch(PACK_B);
        GenerationActivation first = store.preparePendingActivation(epochB, 512);
        byte[] firstManifest = Files.readAllBytes(directory.resolve(GenerationHistoryStore.MANIFEST_FILE_NAME));

        GenerationActivation repeated = store.preparePendingActivation(epochB, 512);
        byte[] repeatedManifest = Files.readAllBytes(directory.resolve(GenerationHistoryStore.MANIFEST_FILE_NAME));

        assertEquals(first, repeated);
        assertEquals(2L, repeated.activationId());
        assertEquals(2, store.manifest().activations().size());
        assertArrayEquals(firstManifest, repeatedManifest);
    }

    @Test
    public void manifestMutationsLeaveOnlyTheCanonicalFile() throws Exception {
        Path directory = temporaryFolder.getRoot().toPath().resolve("generation");
        GenerationHistoryStore store = GenerationHistoryStore.initialize(directory, epoch(PACK_A), CLOCK);
        GenerationActivation pending = store.preparePendingActivation(epoch(PACK_B), 512);
        store.completePendingTransition(pending.activationId(), digest('d'), digest('e'));
        store.activatePending(pending.activationId());

        try (Stream<Path> entries = Files.list(directory)) {
            List<String> names = entries.map(path -> path.getFileName().toString()).sorted().toList();
            assertEquals(List.of("epochs", GenerationHistoryStore.MANIFEST_FILE_NAME), names);
        }
        GenerationHistoryStore reopened = GenerationHistoryStore.open(directory, CLOCK);
        assertEquals(store.manifest(), reopened.manifest());
    }

    @Test
    public void failsClosedWhenManifestIsMissing() throws Exception {
        Path directory = temporaryFolder.newFolder("generation").toPath();

        assertThrows(NoSuchFileException.class, () -> GenerationHistoryStore.open(directory, CLOCK));
    }

    @Test
    public void failsClosedOnMalformedOrUnknownSchema() throws Exception {
        Path malformedDirectory = temporaryFolder.newFolder("malformed").toPath();
        Path malformedManifest = malformedDirectory.resolve(GenerationHistoryStore.MANIFEST_FILE_NAME);
        Files.writeString(malformedManifest, "{not-json", StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> GenerationHistoryStore.open(malformedDirectory, CLOCK));

        Path unknownDirectory = temporaryFolder.getRoot().toPath().resolve("unknown-schema");
        GenerationHistoryStore.initialize(unknownDirectory, epoch(PACK_A), CLOCK);
        JsonObject unknown = readJson(unknownDirectory);
        unknown.addProperty("schemaVersion", GenerationManifest.CURRENT_SCHEMA_VERSION + 1);
        writeJson(unknownDirectory, unknown);

        assertThrows(IOException.class, () -> GenerationHistoryStore.open(unknownDirectory, CLOCK));
    }

    @Test
    public void failsClosedOnMissingEpochReference() throws Exception {
        Path directory = temporaryFolder.getRoot().toPath().resolve("missing-epoch");
        GenerationHistoryStore.initialize(directory, epoch(PACK_A), CLOCK);
        JsonObject json = readJson(directory);
        json.getAsJsonArray("activations")
                .get(0)
                .getAsJsonObject()
                .addProperty("epochId", PACK_B);
        writeJson(directory, json);

        assertThrows(IOException.class, () -> GenerationHistoryStore.open(directory, CLOCK));
    }

    @Test
    public void failsClosedOnForgedEpochIdentityAndUnknownFields() throws Exception {
        Path forgedDirectory = temporaryFolder.getRoot().toPath().resolve("forged");
        GenerationHistoryStore.initialize(forgedDirectory, epoch(PACK_A), CLOCK);
        Path metadata = forgedDirectory.resolve("epochs").resolve(epoch(PACK_A).epochId()).resolve("epoch.json");
        JsonObject forged = JsonParser.parseString(Files.readString(metadata)).getAsJsonObject();
        forged.addProperty("epochId", PACK_B);
        Files.writeString(metadata, forged.toString());

        assertThrows(IOException.class, () -> GenerationHistoryStore.open(forgedDirectory, CLOCK));

        Path unknownFieldDirectory = temporaryFolder.getRoot().toPath().resolve("unknown-field");
        GenerationHistoryStore.initialize(unknownFieldDirectory, epoch(PACK_A), CLOCK);
        JsonObject unknownField = readJson(unknownFieldDirectory);
        unknownField.addProperty("unexpected", true);
        writeJson(unknownFieldDirectory, unknownField);

        assertThrows(IOException.class, () -> GenerationHistoryStore.open(unknownFieldDirectory, CLOCK));
    }

    @Test
    public void rejectsInitializationOverExistingManifest() throws Exception {
        Path directory = temporaryFolder.getRoot().toPath().resolve("generation");
        GenerationHistoryStore.initialize(directory, epoch(PACK_A), CLOCK);

        assertThrows(
                FileAlreadyExistsException.class,
                () -> GenerationHistoryStore.initialize(directory, epoch(PACK_B), CLOCK)
        );
        GenerationHistoryStore reopened = GenerationHistoryStore.open(directory, CLOCK);
        assertEquals(PACK_A, reopened.activeEpoch().packFingerprint());
    }

    @Test
    public void failedActivationDoesNotChangePersistedState() throws Exception {
        Path directory = temporaryFolder.getRoot().toPath().resolve("generation");
        GenerationHistoryStore store = GenerationHistoryStore.initialize(directory, epoch(PACK_A), CLOCK);
        store.preparePendingActivation(epoch(PACK_B), 512);

        assertThrows(IllegalStateException.class, () -> store.activatePending(99L));

        GenerationHistoryStore reopened = GenerationHistoryStore.open(directory, CLOCK);
        assertEquals(PACK_A, reopened.activeEpoch().packFingerprint());
        assertTrue(reopened.pendingActivation().isPresent());
    }

    @Test
    public void rejectsSymbolicLinkStorageRootAndParent() throws Exception {
        Path probe = temporaryFolder.newFolder("history-symlink-probe").toPath();
        Path probeTarget = temporaryFolder.newFolder("history-symlink-probe-target").toPath();
        try {
            Files.createSymbolicLink(probe.resolve("link"), probeTarget);
        } catch (IOException | UnsupportedOperationException exception) {
            Assume.assumeNoException(exception);
        }

        Path rootContainer = temporaryFolder.newFolder("history-linked-root-container").toPath();
        Path linkedRoot = rootContainer.resolve("generation");
        Files.createSymbolicLink(linkedRoot, temporaryFolder.newFolder("history-linked-root-target").toPath());
        assertThrows(
                IOException.class,
                () -> GenerationHistoryStore.initialize(linkedRoot, epoch(PACK_A), CLOCK)
        );

        Path parentContainer = temporaryFolder.newFolder("history-linked-parent-container").toPath();
        Path linkedParent = parentContainer.resolve("iris");
        Files.createSymbolicLink(linkedParent, temporaryFolder.newFolder("history-linked-parent-target").toPath());
        assertThrows(
                IOException.class,
                () -> GenerationHistoryStore.initialize(linkedParent.resolve("generation"), epoch(PACK_A), CLOCK)
        );
    }

    @Test
    public void concurrentInitializationCannotClobberTheInitialManifest() throws Exception {
        Path directory = temporaryFolder.getRoot().toPath().resolve("generation-race");
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(
                    () -> initializeAfterBarrier(directory, epoch(PACK_A), barrier)
            );
            Future<Boolean> second = executor.submit(
                    () -> initializeAfterBarrier(directory, epoch(PACK_B), barrier)
            );

            assertTrue(first.get() ^ second.get());
            String fingerprint = GenerationHistoryStore.open(directory, CLOCK).activeEpoch().packFingerprint();
            assertTrue(fingerprint.equals(PACK_A) || fingerprint.equals(PACK_B));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void postRenameSyncFailureReconcilesTheLiveManifest() throws Exception {
        Path directory = temporaryFolder.getRoot().toPath().resolve("sync-reconcile");
        AtomicInteger syncCount = new AtomicInteger();
        GenerationHistoryStore.DirectorySync directorySync = ignored -> {
            if (syncCount.incrementAndGet() > 1) {
                throw new IOException("Injected directory sync failure");
            }
        };
        GenerationHistoryStore store = GenerationHistoryStore.initialize(
                directory,
                epoch(PACK_A),
                CLOCK,
                directorySync
        );

        assertThrows(IOException.class, () -> store.preparePendingActivation(epoch(PACK_B), 512));

        assertEquals(PACK_B, store.pendingEpoch().orElseThrow().packFingerprint());
        assertEquals(store.manifest(), GenerationHistoryStore.open(directory, CLOCK).manifest());
    }

    @Test
    public void failedPostRenameReconciliationPoisonsTheLiveStore() throws Exception {
        Path directory = temporaryFolder.getRoot().toPath().resolve("sync-poison");
        AtomicInteger syncCount = new AtomicInteger();
        GenerationHistoryStore.DirectorySync directorySync = syncedDirectory -> {
            if (syncCount.incrementAndGet() > 1) {
                Files.writeString(syncedDirectory.resolve(GenerationHistoryStore.MANIFEST_FILE_NAME), "{");
                throw new IOException("Injected unrecoverable directory sync failure");
            }
        };
        GenerationHistoryStore store = GenerationHistoryStore.initialize(
                directory,
                epoch(PACK_A),
                CLOCK,
                directorySync
        );

        assertThrows(IOException.class, () -> store.preparePendingActivation(epoch(PACK_B), 512));
        assertThrows(IllegalStateException.class, store::activeEpoch);
    }

    @Test
    public void manifestKeepsOnlyEpochReferencesAndMetadataSurvivesReopen() throws Exception {
        Path directory = temporaryFolder.getRoot().toPath().resolve("epoch-index");
        GenerationEpoch first = epoch(PACK_A);
        GenerationHistoryStore store = GenerationHistoryStore.initialize(directory, first, CLOCK);
        store.preparePendingActivation(epoch(PACK_B), 256);
        JsonObject index = readJson(directory);
        assertTrue(index.getAsJsonArray("epochs").get(0).isJsonPrimitive());
        assertFalse(Files.readString(directory.resolve("manifest.json")).contains("registryContract"));
        assertTrue(Files.isRegularFile(directory.resolve("epochs").resolve(first.epochId()).resolve("epoch.json")));
        assertEquals(store.manifest(), GenerationHistoryStore.open(directory, CLOCK).manifest());
    }

    @Test
    public void failedEpochPublicationLeavesTheCommittedManifestUsable() throws Exception {
        Path directory = temporaryFolder.getRoot().toPath().resolve("metadata-failure");
        GenerationHistoryStore store = GenerationHistoryStore.initialize(directory, epoch(PACK_A), CLOCK);
        byte[] original = Files.readAllBytes(directory.resolve("manifest.json"));
        GenerationEpoch replacement = epoch(PACK_B);
        Files.writeString(directory.resolve("epochs").resolve(replacement.epochId()), "blocked");
        assertThrows(IOException.class, () -> store.preparePendingActivation(replacement, 256));
        assertArrayEquals(original, Files.readAllBytes(directory.resolve("manifest.json")));
        assertEquals(epoch(PACK_A), store.activeEpoch());
        assertFalse(store.pendingEpoch().isPresent());
        assertEquals(store.manifest(), GenerationHistoryStore.open(directory, CLOCK).manifest());
    }

    @Test
    public void rejectsMissingReferencedEpochMetadata() throws Exception {
        Path directory = temporaryFolder.getRoot().toPath().resolve("missing-metadata");
        GenerationEpoch first = epoch(PACK_A);
        GenerationHistoryStore.initialize(directory, first, CLOCK);
        Files.delete(directory.resolve("epochs").resolve(first.epochId()).resolve("epoch.json"));
        assertThrows(IOException.class, () -> GenerationHistoryStore.open(directory, CLOCK));
    }

    private static boolean initializeAfterBarrier(
            Path directory,
            GenerationEpoch epoch,
            CyclicBarrier barrier
    ) throws Exception {
        barrier.await();
        try {
            GenerationHistoryStore.initialize(directory, epoch, CLOCK);
            return true;
        } catch (IOException expected) {
            return false;
        }
    }

    private static GenerationEpoch epoch(String packFingerprint) {
        GenerationEpoch.DimensionContract contract = new GenerationEpoch.DimensionContract(
                "overworld",
                "iris:overworld_type",
                "NORMAL",
                "OVERWORLD",
                127,
                -64,
                384,
                384,
                1D,
                false,
                "none",
                0,
                "0".repeat(64),
                GenerationEpochContractFactory.CURRENT_DIMENSION_TYPE_FINGERPRINT_SCHEMA,
                "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
        );
        return GenerationEpoch.create(new GenerationEpoch.Spec(
                packFingerprint,
                GenerationPackFingerprint.CURRENT_VERSION,
                42L,
                1,
                1,
                1,
                GenerationKernelV1.IMPLEMENTATION_FINGERPRINT,
                contract,
                GenerationRegistryContract.empty()
        ));
    }

    private static String digest(char value) {
        return String.valueOf(value).repeat(64);
    }

    private static JsonObject readJson(Path directory) throws IOException {
        String content = Files.readString(
                directory.resolve(GenerationHistoryStore.MANIFEST_FILE_NAME),
                StandardCharsets.UTF_8
        );
        return JsonParser.parseString(content).getAsJsonObject();
    }

    private static void writeJson(Path directory, JsonObject json) throws IOException {
        Files.writeString(
                directory.resolve(GenerationHistoryStore.MANIFEST_FILE_NAME),
                json.toString(),
                StandardCharsets.UTF_8
        );
    }
}
