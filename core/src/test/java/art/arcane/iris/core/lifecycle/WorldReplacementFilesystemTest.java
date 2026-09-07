package art.arcane.iris.core.lifecycle;

import art.arcane.iris.engine.history.GenerationEpoch;
import art.arcane.iris.engine.history.GenerationEpochContractFactory;
import art.arcane.iris.engine.history.GenerationHistory;
import art.arcane.iris.engine.history.GenerationPackFingerprint;
import art.arcane.iris.engine.history.GenerationRegistryContract;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class WorldReplacementFilesystemTest {
    private static final UUID TRANSACTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_TRANSACTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @ClassRule
    public static final TemporaryFolder prototypeFolder = new TemporaryFolder();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static StagePrototype replacementStage;
    private static StagePrototype originalStage;

    @BeforeClass
    public static void createStagePrototypes() throws Exception {
        replacementStage = createStagePrototype("replacement");
        originalStage = createStagePrototype("original-stage");
    }

    @Test
    public void publishesReplacementAndRetainsOriginalBackup() throws Exception {
        WorldReplacementFilesystem.ReplacementPaths paths = paths("publish-existing", TRANSACTION_ID);
        writeOriginalTarget(paths, "original");
        Files.createDirectories(paths.target().resolve("region"));
        Files.createDirectories(paths.target().resolve("entities"));
        Files.createDirectories(paths.target().resolve("poi"));
        Files.writeString(paths.target().resolve("region/r.0.0.mca"), "old-region");
        Files.writeString(paths.target().resolve("entities/r.0.0.mca"), "old-entities");
        Files.writeString(paths.target().resolve("poi/r.0.0.mca"), "old-poi");
        String fingerprint = writeStage(paths, "replacement");

        WorldReplacementFilesystem.publish(paths, true, fingerprint);

        assertEquals("replacement", readPackContent(paths.target()));
        assertEquals("original", Files.readString(paths.backup().resolve("original.txt")));
        assertEquals("metadata", Files.readString(paths.target().resolve("data/paper/metadata.dat")));
        assertEquals("overrides", Files.readString(paths.target().resolve("data/paper/level_overrides.dat")));
        assertEquals(
                "generation",
                Files.readString(paths.target().resolve("data/minecraft/world_gen_settings.dat"))
        );
        assertFalse(Files.exists(paths.target().resolve("region/r.0.0.mca")));
        assertFalse(Files.exists(paths.target().resolve("entities/r.0.0.mca")));
        assertFalse(Files.exists(paths.target().resolve("poi/r.0.0.mca")));
        assertTrue(Files.exists(paths.backup().resolve("region/r.0.0.mca")));
        assertTrue(Files.exists(paths.backup().resolve("entities/r.0.0.mca")));
        assertTrue(Files.exists(paths.backup().resolve("poi/r.0.0.mca")));
        assertFalse(Files.exists(paths.stage()));
    }

    @Test
    public void publishesStagedWorldGenerationSettingsWithoutChangingTheRetainedBackup() throws Exception {
        WorldReplacementFilesystem.ReplacementPaths paths = paths("publish-seed-override", TRANSACTION_ID);
        writeOriginalTarget(paths, "original");
        String fingerprint = writeStage(paths, "replacement");
        Path stagedSettings = paths.stage().resolve("data/minecraft/world_gen_settings.dat");
        Files.createDirectories(stagedSettings.getParent());
        Files.writeString(stagedSettings, "replacement-generation");

        WorldReplacementFilesystem.publish(paths, true, fingerprint);

        assertEquals("replacement-generation", Files.readString(
                paths.target().resolve("data/minecraft/world_gen_settings.dat")));
        assertEquals("generation", Files.readString(
                paths.backup().resolve("data/minecraft/world_gen_settings.dat")));

        WorldReplacementFilesystem.rollback(paths, true);

        assertEquals("generation", Files.readString(
                paths.target().resolve("data/minecraft/world_gen_settings.dat")));
        assertFalse(Files.exists(paths.backup()));
    }

    @Test
    public void rejectsAbsentTargetForReplacementAdmission() throws Exception {
        WorldReplacementFilesystem.ReplacementPaths paths = paths("admit-absent", TRANSACTION_ID);

        IOException failure = assertThrows(
                IOException.class,
                () -> WorldReplacementFilesystem.requireExistingTarget(paths)
        );

        assertEquals(
                "/iris replace requires an existing exact world slot; use /iris create for a new world.",
                failure.getMessage()
        );
        assertFalse(Files.exists(paths.target()));
        assertFalse(Files.exists(paths.stage()));
        assertFalse(Files.exists(paths.backup()));
    }

    @Test
    public void completesMetadataForAlreadyPublishedReplacement() throws Exception {
        WorldReplacementFilesystem.ReplacementPaths paths = paths("published-metadata", TRANSACTION_ID);
        writeOriginalTarget(paths, "original");
        String fingerprint = writeStage(paths, "replacement");
        Files.move(paths.target(), paths.backup());
        Files.move(paths.stage(), paths.target());

        WorldReplacementFilesystem.publish(paths, true, fingerprint);

        assertEquals("metadata", Files.readString(paths.target().resolve("data/paper/metadata.dat")));
        assertEquals("overrides", Files.readString(paths.target().resolve("data/paper/level_overrides.dat")));
        assertEquals(
                "generation",
                Files.readString(paths.target().resolve("data/minecraft/world_gen_settings.dat"))
        );
    }

    @Test
    public void rejectsIncompleteCurrentPaperWorldBeforePublication() throws Exception {
        WorldReplacementFilesystem.ReplacementPaths paths = paths("missing-paper-metadata", TRANSACTION_ID);
        Files.createDirectories(paths.target());
        Files.writeString(paths.target().resolve("original.txt"), "original");
        String fingerprint = writeStage(paths, "replacement");

        assertThrows(
                IOException.class,
                () -> WorldReplacementFilesystem.publish(paths, true, fingerprint)
        );

        assertTrue(Files.isDirectory(paths.target()));
        assertTrue(Files.isDirectory(paths.stage()));
        assertFalse(Files.exists(paths.backup()));
    }

    @Test
    public void rejectsIncompleteCurrentPaperWorldAtAdmission() throws Exception {
        WorldReplacementFilesystem.ReplacementPaths paths = paths("admit-missing-paper-metadata", TRANSACTION_ID);
        Files.createDirectories(paths.target());
        Files.writeString(paths.target().resolve("original.txt"), "original");

        IOException failure = assertThrows(
                IOException.class,
                () -> WorldReplacementFilesystem.requireExistingTarget(paths)
        );

        assertTrue(failure.getMessage().contains("missing Paper world metadata"));
        assertTrue(failure.getMessage().contains("load the world once on this server"));
        assertFalse(Files.exists(paths.stage()));
        assertFalse(Files.exists(paths.backup()));
    }

    @Test
    public void publishesReplacementWithoutCreatingBackupForAbsentTarget() throws Exception {
        WorldReplacementFilesystem.ReplacementPaths paths = paths("publish-absent", TRANSACTION_ID);
        String fingerprint = writeStage(paths, "replacement");

        WorldReplacementFilesystem.publish(paths, false, fingerprint);

        assertEquals("replacement", readPackContent(paths.target()));
        assertFalse(Files.exists(paths.stage()));
        assertFalse(Files.exists(paths.backup()));
    }

    @Test
    public void retriesPublicationAfterOriginalWasMovedToBackup() throws Exception {
        WorldReplacementFilesystem.ReplacementPaths paths = paths("retry-first-move", TRANSACTION_ID);
        writeOriginalTarget(paths, "original");
        String fingerprint = writeStage(paths, "replacement");
        Files.move(paths.target(), paths.backup());

        WorldReplacementFilesystem.publish(paths, true, fingerprint);

        assertEquals("replacement", readPackContent(paths.target()));
        assertEquals("original", Files.readString(paths.backup().resolve("original.txt")));
        assertFalse(Files.exists(paths.stage()));
    }

    @Test
    public void retriesPublicationAfterStageWasMovedToTarget() throws Exception {
        WorldReplacementFilesystem.ReplacementPaths paths = paths("retry-second-move", TRANSACTION_ID);
        writeOriginalTarget(paths, "original");
        String fingerprint = writeStage(paths, "replacement");
        Files.move(paths.target(), paths.backup());
        Files.move(paths.stage(), paths.target());

        WorldReplacementFilesystem.publish(paths, true, fingerprint);

        assertEquals("replacement", readPackContent(paths.target()));
        assertEquals("original", Files.readString(paths.backup().resolve("original.txt")));
        assertFalse(Files.exists(paths.stage()));
    }

    @Test
    public void retriesAbsentTargetPublicationAfterStageWasMoved() throws Exception {
        WorldReplacementFilesystem.ReplacementPaths paths = paths("retry-absent", TRANSACTION_ID);
        String fingerprint = writeStage(paths, "replacement");
        Files.move(paths.stage(), paths.target());

        WorldReplacementFilesystem.publish(paths, false, fingerprint);

        assertEquals("replacement", readPackContent(paths.target()));
        assertFalse(Files.exists(paths.stage()));
        assertFalse(Files.exists(paths.backup()));
    }

    @Test
    public void rollbackRestoresOriginalAfterCompletedPublication() throws Exception {
        WorldReplacementFilesystem.ReplacementPaths paths = paths("rollback-existing", TRANSACTION_ID);
        writeOriginalTarget(paths, "original");
        String fingerprint = writeStage(paths, "replacement");
        WorldReplacementFilesystem.publish(paths, true, fingerprint);

        WorldReplacementFilesystem.rollback(paths, true);

        assertEquals("original", Files.readString(paths.target().resolve("original.txt")));
        assertFalse(Files.exists(paths.target().resolve("iris/generation")));
        assertFalse(Files.exists(paths.stage()));
        assertFalse(Files.exists(paths.backup()));
    }

    @Test
    public void rollbackRemovesPublishedReplacementForOriginallyAbsentTarget() throws Exception {
        WorldReplacementFilesystem.ReplacementPaths paths = paths("rollback-absent", TRANSACTION_ID);
        String fingerprint = writeStage(paths, "replacement");
        WorldReplacementFilesystem.publish(paths, false, fingerprint);

        WorldReplacementFilesystem.rollback(paths, false);

        assertFalse(Files.exists(paths.target()));
        assertFalse(Files.exists(paths.stage()));
        assertFalse(Files.exists(paths.backup()));
    }

    @Test
    public void rollbackRestoresOriginalFromFirstMoveCrashState() throws Exception {
        WorldReplacementFilesystem.ReplacementPaths paths = paths("rollback-first-move", TRANSACTION_ID);
        writeOriginalTarget(paths, "original");
        writeStage(paths, "replacement");
        Files.move(paths.target(), paths.backup());

        WorldReplacementFilesystem.rollback(paths, true);

        assertEquals("original", Files.readString(paths.target().resolve("original.txt")));
        assertFalse(Files.exists(paths.stage()));
        assertFalse(Files.exists(paths.backup()));
    }

    @Test
    public void preparedRollbackCanRepublishWhenConfigurationRestoreFails() throws Exception {
        WorldReplacementFilesystem.ReplacementPaths paths = paths("rollback-republish", TRANSACTION_ID);
        writeOriginalTarget(paths, "original");
        String fingerprint = writeStage(paths, "replacement");
        WorldReplacementFilesystem.publish(paths, true, fingerprint);

        WorldReplacementFilesystem.prepareRollback(paths, true);
        WorldReplacementFilesystem.publish(paths, true, fingerprint);

        assertEquals("replacement", readPackContent(paths.target()));
        assertEquals("original", Files.readString(paths.backup().resolve("original.txt")));
        assertFalse(Files.exists(paths.stage()));
    }

    @Test
    public void preparedRollbackCleanupIsRetryableAfterStageDeletion() throws Exception {
        WorldReplacementFilesystem.ReplacementPaths paths = paths("rollback-cleanup-retry", TRANSACTION_ID);
        writeOriginalTarget(paths, "original");
        String fingerprint = writeStage(paths, "replacement");
        WorldReplacementFilesystem.publish(paths, true, fingerprint);
        WorldReplacementFilesystem.prepareRollback(paths, true);

        WorldReplacementFilesystem.finishPreparedRollback(paths, true);
        WorldReplacementFilesystem.finishPreparedRollback(paths, true);

        assertEquals("original", Files.readString(paths.target().resolve("original.txt")));
        assertFalse(Files.exists(paths.stage()));
        assertFalse(Files.exists(paths.backup()));
    }

    @Test
    public void rejectsPackMutationBeforePublication() throws Exception {
        WorldReplacementFilesystem.ReplacementPaths paths = paths("fingerprint-mutation", TRANSACTION_ID);
        String fingerprint = writeStage(paths, "original-stage");
        Files.writeString(packContent(paths.stage()), "mutated-stage");

        assertThrows(
                IOException.class,
                () -> WorldReplacementFilesystem.publish(paths, false, fingerprint)
        );

        assertTrue(Files.isDirectory(paths.stage()));
        assertFalse(Files.exists(paths.target()));
        assertFalse(Files.exists(paths.backup()));
    }

    @Test
    public void rejectsSymlinkOutsidePackBeforePublication() throws Exception {
        WorldReplacementFilesystem.ReplacementPaths paths = paths("outside-pack-link", TRANSACTION_ID);
        String fingerprint = writeStage(paths, "replacement");
        Path outside = temporaryFolder.newFile("outside.txt").toPath();
        Path region = Files.createDirectories(paths.stage().resolve("region"));
        Files.createSymbolicLink(region.resolve("linked.mca"), outside);

        assertThrows(
                IOException.class,
                () -> WorldReplacementFilesystem.publish(paths, false, fingerprint)
        );

        assertTrue(Files.isDirectory(paths.stage()));
        assertFalse(Files.exists(paths.target()));
    }

    @Test
    public void rejectsSpecialEntryOutsidePackBeforePublication() throws Exception {
        Path shortTemp = Path.of("/tmp");
        Assume.assumeTrue(Files.isDirectory(shortTemp));
        Path parent = Files.createTempDirectory(shortTemp, "iw");
        String name = "u";
        String stem = artifactStem(name, TRANSACTION_ID);
        WorldReplacementFilesystem.ReplacementPaths paths = new WorldReplacementFilesystem.ReplacementPaths(
                parent.resolve(name),
                parent.resolve(stem + ".stage"),
                parent.resolve(stem + ".backup")
        );
        try {
            String fingerprint = writeStage(paths, "replacement");
            Path socket = paths.stage().resolve("unsafe.sock");
            try (ServerSocketChannel channel = openUnixSocket(socket)) {
                assertThrows(
                        IOException.class,
                        () -> WorldReplacementFilesystem.publish(paths, false, fingerprint)
                );
            }

            assertTrue(Files.isDirectory(paths.stage()));
            assertFalse(Files.exists(paths.target()));
        } finally {
            deleteTestTree(parent);
        }
    }

    @Test
    public void rejectsSymlinkInsidePackAndNonDirectoryArtifacts() throws Exception {
        WorldReplacementFilesystem.ReplacementPaths linkedPaths = paths("inside-pack-link", TRANSACTION_ID);
        Path pack = Files.createDirectories(linkedPaths.stage().resolve("iris/pack"));
        Path nestedObjects = Files.createDirectories(pack.resolve("objects/oak"));
        Path outside = temporaryFolder.newFile("outside-pack.txt").toPath();
        Files.createSymbolicLink(nestedObjects.resolve("linked.json"), outside);

        assertThrows(IOException.class, () -> WorldReplacementFilesystem.fingerprintPack(pack));

        WorldReplacementFilesystem.ReplacementPaths filePaths = paths("file-stage", OTHER_TRANSACTION_ID);
        Files.createFile(filePaths.stage());
        assertThrows(
                IOException.class,
                () -> WorldReplacementFilesystem.publish(filePaths, false, "0".repeat(64))
        );
    }

    @Test
    public void fingerprintRemainsCompatibleAcrossNestedCreationOrder() throws Exception {
        Path firstPack = temporaryFolder.newFolder("fingerprint-order-first").toPath();
        Path secondPack = temporaryFolder.newFolder("fingerprint-order-second").toPath();
        writeFingerprintFixture(firstPack, false);
        writeFingerprintFixture(secondPack, true);

        String firstFingerprint = WorldReplacementFilesystem.fingerprintPack(firstPack);
        String secondFingerprint = WorldReplacementFilesystem.fingerprintPack(secondPack);

        assertEquals("215206c4a5e74c7731fbd8d2da9265332bfb06a0f74b32e47d02dab3dd878b4c", firstFingerprint);
        assertEquals(firstFingerprint, secondFingerprint);
    }

    @Test
    public void ignoresGeneratedAuthoringMetadataWithoutIgnoringNestedPackContent() throws Exception {
        Path pack = temporaryFolder.newFolder("generated-metadata").toPath();
        Path objects = Files.createDirectories(pack.resolve("objects"));
        Files.writeString(objects.resolve("tree.iob"), "tree");
        String expected = WorldReplacementFilesystem.fingerprintPack(pack);

        Files.createDirectories(pack.resolve(".iris/schema"));
        Files.writeString(pack.resolve(".iris/schema/dimensions-schema.json"), "schema");
        Files.createDirectories(pack.resolve(".idea"));
        Files.writeString(pack.resolve(".idea/jsonSchemas.xml"), "generated UUIDs");
        Files.writeString(pack.resolve("pack.code-workspace"), "workspace");
        Files.writeString(objects.resolve("editor.code-workspace"), "nested workspace");

        assertEquals(expected, WorldReplacementFilesystem.fingerprintPack(pack));

        Path nestedPackContent = Files.createDirectories(objects.resolve(".iris"));
        Files.writeString(nestedPackContent.resolve("semantic.json"), "pack content");

        assertNotEquals(expected, WorldReplacementFilesystem.fingerprintPack(pack));
    }

    @Test
    public void ignoresNestedFinderMetadataAddedAfterFingerprinting() throws Exception {
        Path pack = temporaryFolder.newFolder("nested-finder-metadata").toPath();
        Path objects = Files.createDirectories(pack.resolve("objects/oak"));
        Files.writeString(objects.resolve("tree.iob"), "tree");
        String expected = WorldReplacementFilesystem.fingerprintPack(pack);

        Files.writeString(objects.resolve(".DS_Store"), "Finder metadata");

        assertEquals(expected, WorldReplacementFilesystem.fingerprintPack(pack));
    }

    @Test
    public void rejectsNestedFinderMetadataSymlink() throws Exception {
        Path pack = temporaryFolder.newFolder("unsafe-nested-finder-metadata").toPath();
        Path objects = Files.createDirectories(pack.resolve("objects/oak"));
        Path outside = temporaryFolder.newFile("outside-finder-metadata.txt").toPath();
        Files.createSymbolicLink(objects.resolve(".DS_Store"), outside);

        assertThrows(IOException.class, () -> WorldReplacementFilesystem.fingerprintPack(pack));
    }

    @Test
    public void validatesExcludedAuthoringMetadataForUnsafeEntries() throws Exception {
        Path pack = temporaryFolder.newFolder("unsafe-generated-metadata").toPath();
        Path metadata = Files.createDirectories(pack.resolve(".iris"));
        Path outside = temporaryFolder.newFile("outside-generated-metadata.txt").toPath();
        Files.createSymbolicLink(metadata.resolve("linked.json"), outside);

        assertThrows(IOException.class, () -> WorldReplacementFilesystem.fingerprintPack(pack));
    }

    @Test
    public void rejectsMalformedAndCrossTransactionPaths() throws Exception {
        Path parent = temporaryFolder.newFolder("invalid-paths").toPath();
        Path target = parent.resolve("underworld");
        String firstStem = artifactStem("underworld", TRANSACTION_ID);
        String secondStem = artifactStem("underworld", OTHER_TRANSACTION_ID);

        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldReplacementFilesystem.ReplacementPaths(
                        target,
                        parent.resolve("invalid.stage"),
                        parent.resolve(firstStem + ".backup")
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldReplacementFilesystem.ReplacementPaths(
                        target,
                        parent.resolve(firstStem + ".stage"),
                        parent.resolve(secondStem + ".backup")
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldReplacementFilesystem.ReplacementPaths(
                        target,
                        parent.resolve(firstStem + ".stage"),
                        Files.createDirectories(parent.resolve("other")).resolve(firstStem + ".backup")
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldReplacementFilesystem.ReplacementPaths(
                        parent.resolve("safe/../underworld"),
                        parent.resolve(firstStem + ".stage"),
                        parent.resolve(firstStem + ".backup")
                )
        );
    }

    @Test
    public void rejectsImpossibleCombinedPublicationState() throws Exception {
        WorldReplacementFilesystem.ReplacementPaths paths = paths("combined-state", TRANSACTION_ID);
        writeOriginalTarget(paths, "original");
        String fingerprint = writeStage(paths, "replacement");
        Files.createDirectories(paths.backup());

        assertThrows(
                IOException.class,
                () -> WorldReplacementFilesystem.publish(paths, true, fingerprint)
        );
    }

    private WorldReplacementFilesystem.ReplacementPaths paths(String name, UUID transactionId) throws Exception {
        Path parent = temporaryFolder.newFolder(name).toPath();
        String stem = artifactStem(name, transactionId);
        return new WorldReplacementFilesystem.ReplacementPaths(
                parent.resolve(name),
                parent.resolve(stem + ".stage"),
                parent.resolve(stem + ".backup")
        );
    }

    private String writeStage(WorldReplacementFilesystem.ReplacementPaths paths, String content) throws Exception {
        Path source = paths.stage().resolveSibling(paths.stage().getFileName() + ".pack-source");
        Path contentFile = source.resolve("dimensions/underworld.json");
        Files.createDirectories(contentFile.getParent());
        Files.writeString(contentFile, content);
        StagePrototype prototype = stagePrototype(content);
        copyTree(prototype.stage(), paths.stage());
        return prototype.fingerprint();
    }

    private static StagePrototype stagePrototype(String content) {
        if ("replacement".equals(content)) {
            return replacementStage;
        }
        if ("original-stage".equals(content)) {
            return originalStage;
        }
        throw new AssertionError("No staged generation history prototype for " + content);
    }

    private static StagePrototype createStagePrototype(String content) throws Exception {
        Path base = prototypeFolder.newFolder("stage-" + content).toPath();
        Path source = base.resolve("pack-source");
        Path contentFile = source.resolve("dimensions/underworld.json");
        Files.createDirectories(contentFile.getParent());
        Files.writeString(contentFile, content);
        String generationFingerprint = GenerationPackFingerprint.compute(
                source,
                GenerationPackFingerprint.CURRENT_VERSION
        );
        Path stage = base.resolve("stage");
        Files.createDirectory(stage);
        GenerationHistory history = GenerationHistory.create(
                stage,
                source,
                generationFingerprint,
                42L,
                generationContract("underworld"),
                GenerationRegistryContract.empty()
        );
        return new StagePrototype(stage, WorldReplacementFilesystem.fingerprintPack(history.activePackRoot()));
    }

    private static void copyTree(Path source, Path target) throws IOException {
        try (Stream<Path> entries = Files.walk(source)) {
            for (Path entry : entries.toList()) {
                Path destination = target.resolve(source.relativize(entry).toString());
                if (Files.isDirectory(entry)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(entry, destination, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private record StagePrototype(Path stage, String fingerprint) {
    }

    private void writeOriginalTarget(WorldReplacementFilesystem.ReplacementPaths paths, String content) throws Exception {
        Files.createDirectories(paths.target());
        Files.writeString(paths.target().resolve("original.txt"), content);
        Files.createDirectories(paths.target().resolve("data/paper"));
        Files.createDirectories(paths.target().resolve("data/minecraft"));
        Files.writeString(paths.target().resolve("data/paper/metadata.dat"), "metadata");
        Files.writeString(paths.target().resolve("data/paper/level_overrides.dat"), "overrides");
        Files.writeString(paths.target().resolve("data/minecraft/world_gen_settings.dat"), "generation");
    }

    private void writeFingerprintFixture(Path pack, boolean reverseOrder) throws Exception {
        if (reverseOrder) {
            Files.createDirectories(pack.resolve("regions"));
            Files.writeString(pack.resolve("regions/default.json"), "{}\n");
            Files.createDirectories(pack.resolve("empty"));
            Files.createDirectories(pack.resolve("objects/oak"));
            Files.write(pack.resolve("objects/oak/tree.iob"), new byte[]{0, 1, 2, (byte) 255});
            Files.createDirectories(pack.resolve("dimensions"));
            Files.writeString(pack.resolve("dimensions/overworld.json"), "{\"name\":\"world\"}");
        } else {
            Files.createDirectories(pack.resolve("dimensions"));
            Files.writeString(pack.resolve("dimensions/overworld.json"), "{\"name\":\"world\"}");
            Files.createDirectories(pack.resolve("objects/oak"));
            Files.write(pack.resolve("objects/oak/tree.iob"), new byte[]{0, 1, 2, (byte) 255});
            Files.createDirectories(pack.resolve("empty"));
            Files.createDirectories(pack.resolve("regions"));
            Files.writeString(pack.resolve("regions/default.json"), "{}\n");
        }
        Files.createDirectories(pack.resolve(".iris/schema"));
        Files.writeString(pack.resolve(".iris/schema/dimensions-schema.json"), "generated");
        Files.writeString(pack.resolve("editor.code-workspace"), "generated");
    }

    private String readPackContent(Path worldDirectory) throws Exception {
        return Files.readString(packContent(worldDirectory));
    }

    private Path packContent(Path worldDirectory) throws IOException {
        return GenerationHistory.open(worldDirectory)
                .activePackRoot()
                .resolve("dimensions/underworld.json");
    }

    private static GenerationEpoch.DimensionContract generationContract(String dimensionKey) {
        return new GenerationEpoch.DimensionContract(
                dimensionKey,
                "iris:" + dimensionKey + "_type",
                "NETHER",
                "OVERWORLD",
                0,
                0,
                256,
                256,
                1D,
                false,
                "none",
                0,
                "0".repeat(64),
                GenerationEpochContractFactory.CURRENT_DIMENSION_TYPE_FINGERPRINT_SCHEMA,
                "c".repeat(64)
        );
    }

    private String artifactStem(String name, UUID transactionId) {
        return ".iris-replace-" + name + "-" + transactionId;
    }

    private ServerSocketChannel openUnixSocket(Path path) throws Exception {
        ServerSocketChannel channel = null;
        try {
            channel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            channel.bind(UnixDomainSocketAddress.of(path));
            return channel;
        } catch (UnsupportedOperationException exception) {
            if (channel != null) {
                channel.close();
            }
            Assume.assumeNoException(exception);
            throw exception;
        } catch (Exception | Error failure) {
            if (channel != null) {
                channel.close();
            }
            throw failure;
        }
    }

    private void deleteTestTree(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
