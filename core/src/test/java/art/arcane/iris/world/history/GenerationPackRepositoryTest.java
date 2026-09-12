package art.arcane.iris.world.history;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mockStatic;

public class GenerationPackRepositoryTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void publishesAnImmutableFilteredPack() throws Exception {
        Path dimensionRoot = temporaryFolder.newFolder("world").toPath();
        Path source = createPack("source", "{}");
        Files.createDirectories(source.resolve(".git"));
        Files.writeString(source.resolve(".git/config"), "local");
        Files.writeString(source.resolve("pack.code-workspace"), "{}");
        String fingerprint = fingerprint(source);
        GenerationPackRepository repository = new GenerationPackRepository(dimensionRoot);
        String epochId = digest('a');

        Path published = repository.publish(epochId, fingerprint, GenerationPackFingerprint.CURRENT_VERSION, source);

        assertTrue(Files.isRegularFile(published.resolve("dimensions/main.json")));
        assertFalse(Files.exists(published.resolve(".git")));
        assertFalse(Files.exists(published.resolve("pack.code-workspace")));
        assertEquals(published, repository.publish(
                epochId,
                fingerprint,
                GenerationPackFingerprint.CURRENT_VERSION,
                source
        ));
    }

    @Test
    public void rejectsMutationOfAnExistingEpoch() throws Exception {
        Path dimensionRoot = temporaryFolder.newFolder("mutation-world").toPath();
        Path source = createPack("first", "{}");
        String fingerprint = fingerprint(source);
        GenerationPackRepository repository = new GenerationPackRepository(dimensionRoot);
        String epochId = digest('b');
        Path published = repository.publish(epochId, fingerprint, GenerationPackFingerprint.CURRENT_VERSION, source);
        Files.writeString(published.resolve("dimensions/main.json"), "{\"changed\":true}");

        assertThrows(IOException.class, () -> repository.requireExactPack(
                epochId,
                fingerprint,
                GenerationPackFingerprint.CURRENT_VERSION
        ));
        assertThrows(IOException.class, () -> repository.publish(
                epochId,
                fingerprint,
                GenerationPackFingerprint.CURRENT_VERSION,
                source
        ));
    }

    @Test
    public void finderMetadataCreatedDuringPublicationDoesNotChangeVersionTwoIdentity() throws Exception {
        Path dimensionRoot = temporaryFolder.newFolder("finder-world").toPath();
        Path source = createPack("finder-source", "{}");
        String fingerprint = fingerprint(source);
        GenerationPackRepository repository = new GenerationPackRepository(dimensionRoot);
        String epochId = digest('b');
        AtomicBoolean injected = new AtomicBoolean();

        try (MockedStatic<GenerationPackFingerprint> ignored = mockStatic(GenerationPackFingerprint.class, invocation -> {
            if (invocation.getMethod().getName().equals("compute")) {
                Path pack = invocation.getArgument(0);
                if (pack.getFileName().toString().startsWith(".pack-")) {
                    Files.writeString(pack.resolve(".DS_Store"), "root metadata");
                    Files.writeString(pack.resolve("dimensions/.DS_Store"), "nested metadata");
                    injected.set(true);
                }
            }
            return invocation.callRealMethod();
        })) {
            Path published = repository.publish(epochId, fingerprint, 2, source);
            assertTrue(injected.get());
            assertTrue(Files.isRegularFile(published.resolve("dimensions/.DS_Store")));
            assertEquals(published, repository.requireExactPack(epochId, fingerprint, 2));
            Files.writeString(published.resolve("dimensions/main.json"), "{\"changed\":true}");
            assertThrows(IOException.class, () -> repository.requireExactPack(epochId, fingerprint, 2));
        }
    }

    @Test
    public void versionOnePublicationsKeepTheirOriginalMetadataContract() throws Exception {
        Path dimensionRoot = temporaryFolder.newFolder("version-one-world").toPath();
        Path source = createPack("version-one-source", "{}");
        Files.writeString(source.resolve("dimensions/.DS_Store"), "original metadata");
        String fingerprint = GenerationPackFingerprint.compute(source, 1);
        GenerationPackRepository repository = new GenerationPackRepository(dimensionRoot);
        String epochId = digest('a');

        Path published = repository.publish(epochId, fingerprint, 1, source);
        assertEquals(published, repository.requireExactPack(epochId, fingerprint, 1));
        Files.writeString(published.resolve("dimensions/.DS_Store"), "changed metadata");
        assertThrows(IOException.class, () -> repository.requireExactPack(epochId, fingerprint, 1));
    }

    @Test
    public void rejectsNestedSymbolicLinks() throws Exception {
        Path dimensionRoot = temporaryFolder.newFolder("linked-world").toPath();
        Path source = createPack("linked", "{}");
        Path outside = temporaryFolder.newFile("outside.json").toPath();
        try {
            Files.createSymbolicLink(source.resolve("dimensions/link.json"), outside);
        } catch (IOException | UnsupportedOperationException exception) {
            Assume.assumeNoException(exception);
        }
        GenerationPackRepository repository = new GenerationPackRepository(dimensionRoot);

        assertThrows(IOException.class, () -> repository.publish(
                digest('c'),
                digest('d'),
                GenerationPackFingerprint.CURRENT_VERSION,
                source));
    }

    @Test
    public void rejectsInvalidEpochIdentity() throws Exception {
        Path dimensionRoot = temporaryFolder.newFolder("invalid-world").toPath();
        GenerationPackRepository repository = new GenerationPackRepository(dimensionRoot);

        assertThrows(IllegalArgumentException.class, () -> repository.packRoot("not-a-digest"));
        assertThrows(IllegalArgumentException.class, () -> repository.packRoot(digest('A')));
    }

    @Test
    public void rejectsSymbolicLinksAtEveryStorageComponent() throws Exception {
        Path probe = temporaryFolder.newFolder("symlink-probe").toPath();
        Path probeTarget = temporaryFolder.newFolder("symlink-probe-target").toPath();
        try {
            Files.createSymbolicLink(probe.resolve("link"), probeTarget);
        } catch (IOException | UnsupportedOperationException exception) {
            Assume.assumeNoException(exception);
        }

        for (String component : new String[]{"dimension", "iris", "generation", "epochs", "epoch", "pack"}) {
            assertStorageLinkRejected(component);
        }
    }

    @Test
    public void concurrentRepositoryInstancesCannotClobberAnEpoch() throws Exception {
        Path dimensionRoot = temporaryFolder.newFolder("race-world").toPath();
        Path firstSource = createPack("race-first", "{\"winner\":1}");
        Path secondSource = createPack("race-second", "{\"winner\":2}");
        String firstFingerprint = fingerprint(firstSource);
        String secondFingerprint = fingerprint(secondSource);
        String epochId = digest('e');
        GenerationPackRepository firstRepository = new GenerationPackRepository(dimensionRoot);
        GenerationPackRepository secondRepository = new GenerationPackRepository(dimensionRoot);
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> publishAfterBarrier(
                    firstRepository,
                    epochId,
                    firstFingerprint,
                    firstSource,
                    barrier
            ));
            Future<Boolean> second = executor.submit(() -> publishAfterBarrier(
                    secondRepository,
                    epochId,
                    secondFingerprint,
                    secondSource,
                    barrier
            ));

            assertTrue(first.get() ^ second.get());
            String content = Files.readString(firstRepository.packRoot(epochId).resolve("dimensions/main.json"));
            assertTrue(content.equals("{\"winner\":1}") || content.equals("{\"winner\":2}"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void publicationAndActivationRetainEveryFrozenPack() throws Exception {
        Path dimensionRoot = temporaryFolder.newFolder("retention-world").toPath();
        Path firstSource = createPack("retention-first", "{\"version\":1}");
        Path secondSource = createPack("retention-second", "{\"version\":2}");
        Path pendingSource = createPack("retention-pending", "{\"version\":3}");
        GenerationPackRepository repository = new GenerationPackRepository(dimensionRoot);
        GenerationEpoch first = epoch(fingerprint(firstSource));
        GenerationEpoch second = epoch(fingerprint(secondSource));
        GenerationEpoch pending = epoch(fingerprint(pendingSource));
        repository.publish(first.epochId(), first.packFingerprint(), first.packFingerprintVersion(), firstSource);
        GenerationHistoryStore store = GenerationHistoryStore.initialize(repository.generationRoot(), first);
        repository.publish(second.epochId(), second.packFingerprint(), second.packFingerprintVersion(), secondSource);
        GenerationActivation activation = store.preparePendingActivation(second, 256);
        store.completePendingTransition(activation.activationId(), digest('d'), digest('e'));
        store.activatePending(activation.activationId());
        repository.publish(pending.epochId(), pending.packFingerprint(), pending.packFingerprintVersion(), pendingSource);
        store.preparePendingActivation(pending, 256);

        assertTrue(Files.isDirectory(repository.packRoot(first.epochId())));
        assertEquals(first.packFingerprint(), fingerprint(repository.packRoot(first.epochId())));
        assertTrue(Files.isRegularFile(repository.epochRoot(first.epochId()).resolve("epoch.json")));
        assertTrue(Files.isDirectory(repository.packRoot(second.epochId())));
        assertTrue(Files.isDirectory(repository.packRoot(pending.epochId())));
        assertTrue(Files.isRegularFile(firstSource.resolve("dimensions/main.json")));
        assertEquals(store.manifest(), GenerationHistoryStore.open(repository.generationRoot()).manifest());
        Files.writeString(firstSource.resolve("dimensions/main.json"), "{\"version\":99}");
        assertEquals("{\"version\":1}", Files.readString(repository.packRoot(first.epochId()).resolve("dimensions/main.json")));
        assertEquals(repository.packRoot(first.epochId()), repository.requireExactPack(
                first.epochId(), first.packFingerprint(), first.packFingerprintVersion()));
    }

    private static GenerationEpoch epoch(String fingerprint) {
        GenerationEpoch.DimensionContract contract = new GenerationEpoch.DimensionContract(
                "overworld", "iris:overworld_type", "NORMAL", "OVERWORLD", 127,
                -64, 384, 384, 1D, false, "none", 0, "0".repeat(64),
                GenerationEpochContractFactory.CURRENT_DIMENSION_TYPE_FINGERPRINT_SCHEMA, digest('c'));
        return GenerationEpoch.create(new GenerationEpoch.Spec(fingerprint, GenerationPackFingerprint.CURRENT_VERSION,
                42L, 1, 1, 1, GenerationKernelV1.IMPLEMENTATION_FINGERPRINT, contract, GenerationRegistryContract.empty()));
    }

    private void assertStorageLinkRejected(String component) throws Exception {
        Path container = temporaryFolder.newFolder("linked-storage-" + component).toPath();
        Path dimensionRoot = container.resolve("dimension");
        Path external = temporaryFolder.newFolder("linked-storage-target-" + component).toPath();
        String epochId = digest('f');
        if (component.equals("dimension")) {
            Files.createSymbolicLink(dimensionRoot, external);
        } else {
            Files.createDirectory(dimensionRoot);
            Path iris = dimensionRoot.resolve("iris");
            if (component.equals("iris")) {
                Files.createSymbolicLink(iris, external);
            } else {
                Files.createDirectory(iris);
                Path generation = iris.resolve("generation");
                if (component.equals("generation")) {
                    Files.createSymbolicLink(generation, external);
                } else {
                    Files.createDirectory(generation);
                    Path epochs = generation.resolve("epochs");
                    if (component.equals("epochs")) {
                        Files.createSymbolicLink(epochs, external);
                    } else {
                        Files.createDirectory(epochs);
                        Path epoch = epochs.resolve(epochId);
                        if (component.equals("epoch")) {
                            Files.createSymbolicLink(epoch, external);
                        } else {
                            Files.createDirectory(epoch);
                            Files.createSymbolicLink(epoch.resolve("pack"), external);
                        }
                    }
                }
            }
        }
        Path source = createPack("linked-storage-source-" + component, "{}");
        String fingerprint = fingerprint(source);
        GenerationPackRepository repository = new GenerationPackRepository(dimensionRoot);

        assertThrows(IOException.class, () -> repository.publish(
                epochId,
                fingerprint,
                GenerationPackFingerprint.CURRENT_VERSION,
                source
        ));
    }

    private static boolean publishAfterBarrier(
            GenerationPackRepository repository,
            String epochId,
            String fingerprint,
            Path source,
            CyclicBarrier barrier
    ) throws Exception {
        barrier.await();
        try {
            repository.publish(
                    epochId,
                    fingerprint,
                    GenerationPackFingerprint.CURRENT_VERSION,
                    source
            );
            return true;
        } catch (IOException expected) {
            return false;
        }
    }

    private Path createPack(String name, String dimensionJson) throws IOException {
        Path source = temporaryFolder.newFolder(name).toPath();
        Files.createDirectories(source.resolve("dimensions"));
        Files.writeString(source.resolve("dimensions/main.json"), dimensionJson);
        return source;
    }

    private static String digest(char value) {
        return String.valueOf(value).repeat(64);
    }

    private static String fingerprint(Path source) throws IOException {
        return GenerationPackFingerprint.compute(source, GenerationPackFingerprint.CURRENT_VERSION);
    }
}
