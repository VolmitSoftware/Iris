package art.arcane.iris.core.lifecycle;

import art.arcane.iris.core.ExactWorldSlotPathPolicy;
import art.arcane.iris.core.WorldSlotKey;
import art.arcane.iris.core.lifecycle.BukkitWorldConfiguration.WorldGeneratorSnapshot;
import art.arcane.iris.core.lifecycle.WorldReplacementFilesystem.ReplacementPaths;
import art.arcane.iris.core.lifecycle.WorldReplacementJournal.Phase;
import art.arcane.iris.core.lifecycle.WorldReplacementJournal.Transaction;
import art.arcane.iris.engine.history.GenerationEpoch;
import art.arcane.iris.engine.history.GenerationEpochContractFactory;
import art.arcane.iris.engine.history.GenerationHistory;
import art.arcane.iris.engine.history.GenerationPackFingerprint;
import art.arcane.iris.engine.history.GenerationRegistryContract;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class WorldReplacementBootstrapTest {
    private static final WorldSlotKey WORLD_KEY = WorldSlotKey.minecraft("the_nether");
    private static final long SEED = 4242424242L;

    @ClassRule
    public static final TemporaryFolder prototypeFolder = new TemporaryFolder();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static Path underworldStagePrototype;

    private Path serverRoot;
    private Path dataDirectory;
    private Path levelRoot;
    private Path bukkitConfiguration;
    private ExactWorldSlotPathPolicy.Target target;

    @BeforeClass
    public static void createStagePrototype() throws Exception {
        Path base = prototypeFolder.newFolder("underworld-stage").toPath();
        underworldStagePrototype = base.resolve("stage");
        createStagedHistory(underworldStagePrototype, "underworld", "replacement", SEED);
    }

    @Before
    public void setUp() throws Exception {
        serverRoot = temporaryFolder.newFolder("server").toPath();
        dataDirectory = Files.createDirectories(serverRoot.resolve("plugins/Iris"));
        levelRoot = Files.createDirectories(serverRoot.resolve("world"));
        bukkitConfiguration = Files.createFile(serverRoot.resolve("bukkit.yml"));
        target = ExactWorldSlotPathPolicy.resolve(levelRoot, WORLD_KEY);
        Files.createDirectories(target.namespaceRoot());
    }

    @Test
    public void publishesArmedReplacementBeforeRegistryCompilation() throws Exception {
        Transaction transaction = stagedTransaction(Phase.ARMED, true, "original");
        configureReplacement(transaction);

        WorldReplacementBootstrap.ReconcileResult result = reconcile();

        assertEquals(1, result.published());
        assertEquals("replacement", replacementContent(target.worldDirectory()));
        assertEquals("original", Files.readString(backup(transaction).resolve("original.txt")));
        assertEquals(Phase.PUBLISHED, loadSingle().phase());
    }

    @Test
    public void acceptsFinderMetadataAddedAfterImmutableEpochPublication() throws Exception {
        Transaction transaction = stagedTransaction(Phase.ARMED, true, "original");
        configureReplacement(transaction);
        Path dimensions = activePackRoot(paths(transaction).stage()).resolve("dimensions");
        Files.writeString(dimensions.resolve(".DS_Store"), "Finder metadata");

        WorldReplacementBootstrap.ReconcileResult result = reconcile();

        assertEquals(1, result.published());
        assertEquals("replacement", replacementContent(target.worldDirectory()));
        assertEquals(Phase.PUBLISHED, loadSingle().phase());
    }

    @Test
    public void rejectsNestedResourceAddedAfterImmutableEpochPublication() throws Exception {
        Transaction transaction = stagedTransaction(Phase.ARMED, true, "original");
        configureReplacement(transaction);
        Path dimensions = activePackRoot(paths(transaction).stage()).resolve("dimensions");
        Files.writeString(dimensions.resolve(".hidden-resource"), "changed resource");

        assertThrows(IOException.class, this::reconcile);

        assertTrue(Files.isDirectory(paths(transaction).stage()));
        assertEquals("original", Files.readString(target.worldDirectory().resolve("original.txt")));
        assertEquals(Phase.ARMED, loadSingle().phase());
    }

    @Test
    public void publishesArmedReplacementWhenOriginalConfigurationAlreadyMatchesReplacement() throws Exception {
        configureExistingReplacement();
        Transaction transaction = stagedTransaction(Phase.ARMED, true, "original");

        WorldReplacementBootstrap.ReconcileResult result = reconcile();

        assertEquals(1, result.published());
        assertEquals("replacement", replacementContent(target.worldDirectory()));
        assertEquals("original", Files.readString(backup(transaction).resolve("original.txt")));
        assertEquals(Phase.PUBLISHED, loadSingle().phase());
    }

    @Test
    public void retainsPublishedReplacementWhenOriginalConfigurationAlreadyMatchesReplacement() throws Exception {
        configureExistingReplacement();
        Transaction transaction = stagedTransaction(Phase.PUBLISHED, true, "original");
        WorldReplacementFilesystem.publish(paths(transaction), true, transaction.packFingerprint());

        WorldReplacementBootstrap.ReconcileResult result = reconcile();

        assertEquals(1, result.retained());
        assertEquals("replacement", replacementContent(target.worldDirectory()));
        assertEquals("original", Files.readString(backup(transaction).resolve("original.txt")));
        assertEquals(Phase.PUBLISHED, loadSingle().phase());
    }

    @Test
    public void cancelsPreparedReplacementWhenOriginalConfigurationAlreadyMatchesReplacement() throws Exception {
        configureExistingReplacement();
        Transaction transaction = stagedTransaction(Phase.PREPARED, true, "original");

        WorldReplacementBootstrap.ReconcileResult result = reconcile();

        assertEquals(1, result.rolledBack());
        assertEquals("original", Files.readString(target.worldDirectory().resolve("original.txt")));
        assertFalse(Files.exists(paths(transaction).stage()));
        assertTrue(WorldReplacementJournal.load(dataDirectory, levelRoot).isEmpty());
    }

    @Test
    public void resumesPublicationAfterOriginalMoveCrash() throws Exception {
        Transaction transaction = stagedTransaction(Phase.ARMED, true, "original");
        configureReplacement(transaction);
        ReplacementPaths paths = paths(transaction);
        Files.move(paths.target(), paths.backup());

        reconcile();

        assertEquals("replacement", replacementContent(paths.target()));
        assertEquals("original", Files.readString(paths.backup().resolve("original.txt")));
        assertEquals(Phase.PUBLISHED, loadSingle().phase());
    }

    @Test
    public void cancelsPreparedTransactionWhenConfigurationWasNotApplied() throws Exception {
        Transaction transaction = stagedTransaction(Phase.PREPARED, true, "original");

        WorldReplacementBootstrap.ReconcileResult result = reconcile();

        assertEquals(1, result.rolledBack());
        assertEquals("original", Files.readString(target.worldDirectory().resolve("original.txt")));
        assertFalse(Files.exists(paths(transaction).stage()));
        assertTrue(WorldReplacementJournal.load(dataDirectory, levelRoot).isEmpty());
    }

    @Test
    public void restoresPublishedWorldWhenConfigurationWasReverted() throws Exception {
        Transaction transaction = stagedTransaction(Phase.PUBLISHED, true, "original");
        configureReplacement(transaction);
        WorldReplacementFilesystem.publish(paths(transaction), true, transaction.packFingerprint());
        restoreOriginalConfiguration(transaction);

        WorldReplacementBootstrap.ReconcileResult result = reconcile();

        assertEquals(1, result.rolledBack());
        assertEquals("original", Files.readString(target.worldDirectory().resolve("original.txt")));
        assertFalse(Files.exists(paths(transaction).stage()));
        assertFalse(Files.exists(paths(transaction).backup()));
        assertTrue(WorldReplacementJournal.load(dataDirectory, levelRoot).isEmpty());
    }

    @Test
    public void rejectsThirdPartyConfigurationAfterPublicationWithoutMovingStorage() throws Exception {
        Transaction transaction = stagedTransaction(Phase.PUBLISHED, true, "original");
        configureReplacement(transaction);
        WorldReplacementFilesystem.publish(paths(transaction), true, transaction.packFingerprint());
        WorldGeneratorSnapshot replacement = WorldReplacementBootstrap.replacementSnapshot(transaction);
        BukkitWorldConfiguration.replaceIfMatching(
                bukkitConfiguration.toFile(),
                transaction.worldName(),
                replacement,
                "other",
                SEED
        );

        assertThrows(IOException.class, this::reconcile);

        assertEquals("replacement", replacementContent(target.worldDirectory()));
        assertEquals("original", Files.readString(backup(transaction).resolve("original.txt")));
        assertEquals(Phase.PUBLISHED, loadSingle().phase());
    }

    @Test
    public void completesRollbackAcrossPreparedStorageCrashBoundary() throws Exception {
        Transaction transaction = stagedTransaction(Phase.ROLLBACK_PENDING, true, "original");
        configureReplacement(transaction);
        WorldReplacementFilesystem.publish(paths(transaction), true, transaction.packFingerprint());
        WorldReplacementFilesystem.prepareRollback(paths(transaction), true);
        restoreOriginalConfiguration(transaction);
        WorldReplacementJournal.write(dataDirectory, transaction.withPhase(Phase.ROLLBACK_CLEANUP));

        reconcile();

        assertEquals("original", Files.readString(target.worldDirectory().resolve("original.txt")));
        assertFalse(Files.exists(paths(transaction).stage()));
        assertTrue(WorldReplacementJournal.load(dataDirectory, levelRoot).isEmpty());
    }

    @Test
    public void retainsVerifiedTargetWhenBackupWasAlreadyCleaned() throws Exception {
        Transaction transaction = stagedTransaction(Phase.CLEANUP_PENDING, true, "original");
        configureReplacement(transaction);
        WorldReplacementFilesystem.publish(paths(transaction), true, transaction.packFingerprint());
        WorldReplacementFilesystem.cleanupBackup(paths(transaction));

        WorldReplacementBootstrap.ReconcileResult result = reconcile();

        assertEquals(1, result.retained());
        assertEquals("replacement", replacementContent(target.worldDirectory()));
        assertEquals(Phase.CLEANUP_PENDING, loadSingle().phase());
    }

    @Test
    public void skipsChangedLevelRootWithoutTouchingStagedStorage() throws Exception {
        Transaction transaction = stagedTransaction(Phase.ARMED, true, "original");
        configureReplacement(transaction);
        Path otherLevelRoot = Files.createDirectories(serverRoot.resolve("renamed-world"));

        WorldReplacementBootstrap.ReconcileResult result = WorldReplacementBootstrap.reconcile(
                dataDirectory,
                otherLevelRoot,
                bukkitConfiguration,
                ignored -> {
                }
        );

        assertEquals(1, result.skipped());
        assertEquals(0, result.published());
        assertEquals(0, result.rolledBack());
        assertEquals("original", Files.readString(target.worldDirectory().resolve("original.txt")));
        assertTrue(Files.isDirectory(paths(transaction).stage()));
        assertFalse(Files.exists(paths(transaction).backup()));
    }

    @Test
    public void rejectsDuplicateWorldJournalsBeforePublishingEither() throws Exception {
        Transaction first = stagedTransaction(Phase.ARMED, true, "original");
        configureReplacement(first);
        Transaction second = new Transaction(
                UUID.randomUUID(),
                first.worldKey(),
                first.worldName(),
                first.levelRoot(),
                first.dimension(),
                first.seed(),
                first.packFingerprint(),
                first.originalConfiguration(),
                first.originalTargetPresent(),
                first.phase()
        );
        WorldReplacementJournal.write(dataDirectory, second);

        assertThrows(IOException.class, this::reconcile);

        assertTrue(Files.isDirectory(paths(first).stage()));
        assertEquals("original", Files.readString(target.worldDirectory().resolve("original.txt")));
        assertFalse(Files.exists(paths(first).backup()));
    }

    @Test
    public void publishesTwoDistinctArmedReplacementsInOneColdReconcile() throws Exception {
        Transaction nether = stagedTransaction(Phase.ARMED, true, "nether-original");
        configureReplacement(nether);

        WorldSlotKey endKey = WorldSlotKey.minecraft("the_end");
        ExactWorldSlotPathPolicy.Target endTarget = ExactWorldSlotPathPolicy.resolve(levelRoot, endKey);
        WorldGeneratorSnapshot endOriginal = BukkitWorldConfiguration.snapshot(
                bukkitConfiguration.toFile(),
                "world_the_end"
        );
        UUID endId = UUID.randomUUID();
        ReplacementPaths endPaths = WorldReplacementFilesystem.paths(endTarget, endId);
        writeOriginalTarget(endPaths, "end-original");
        Path endPack = createStagedHistory(endPaths.stage(), "theend", "end-replacement", SEED);
        String endFingerprint = WorldReplacementFilesystem.fingerprintPack(endPack);
        Transaction end = new Transaction(
                endId,
                endKey,
                "world_the_end",
                endTarget.levelRoot(),
                "theend",
                SEED,
                endFingerprint,
                endOriginal,
                true,
                Phase.ARMED
        );
        WorldReplacementJournal.write(dataDirectory, end);
        configureReplacement(end);

        WorldReplacementBootstrap.ReconcileResult result = reconcile();

        assertEquals(2, result.transactions());
        assertEquals(2, result.published());
        assertEquals(0, result.rolledBack());
        assertEquals(0, result.retained());
        assertEquals("replacement", replacementContent(target.worldDirectory()));
        assertEquals("end-replacement", Files.readString(activePackRoot(endTarget.worldDirectory())
                .resolve("dimensions/theend.json")));
        assertEquals("nether-original", Files.readString(backup(nether).resolve("original.txt")));
        assertEquals("end-original", Files.readString(endPaths.backup().resolve("original.txt")));
        List<Transaction> published = WorldReplacementJournal.load(dataDirectory, levelRoot);
        assertEquals(2, published.size());
        assertTrue(published.stream().allMatch(transaction -> transaction.phase() == Phase.PUBLISHED));
    }

    @Test
    public void publishesPreparedOverworldAndUnderworldIntoExactVanillaSlotsInOneColdReconcile()
            throws Exception {
        long overworldSeed = 81818181L;
        WorldSlotKey overworldKey = WorldSlotKey.minecraft("overworld");
        ExactWorldSlotPathPolicy.Target overworldTarget = ExactWorldSlotPathPolicy.resolve(levelRoot, overworldKey);
        WorldGeneratorSnapshot overworldOriginal = BukkitWorldConfiguration.snapshot(
                bukkitConfiguration.toFile(),
                "world"
        );
        UUID overworldId = UUID.randomUUID();
        ReplacementPaths overworldPaths = WorldReplacementFilesystem.paths(overworldTarget, overworldId);
        writeOriginalTarget(overworldPaths, "overworld-original");
        Path overworldPack = createStagedHistory(
                overworldPaths.stage(),
                "overworld",
                "overworld-replacement",
                overworldSeed
        );
        WorldReplacementEntryGuard.stage(levelRoot, overworldPaths.stage(), overworldId);
        String overworldFingerprint = WorldReplacementFilesystem.fingerprintPack(overworldPack);
        Transaction overworld = new Transaction(
                overworldId,
                overworldKey,
                "world",
                overworldTarget.levelRoot(),
                "overworld",
                overworldSeed,
                overworldFingerprint,
                overworldOriginal,
                true,
                Phase.ARMED
        );
        WorldReplacementJournal.write(dataDirectory, overworld);
        configureReplacement(overworld);

        Transaction nether = stagedTransaction(Phase.ARMED, true, "nether-original");
        configureReplacement(nether);

        WorldReplacementBootstrap.ReconcileResult result = reconcile();

        assertEquals(2, result.transactions());
        assertEquals(2, result.published());
        assertEquals(0, result.rolledBack());
        assertEquals(0, result.retained());
        assertEquals(0, result.skipped());
        assertEquals(
                levelRoot.toRealPath().resolve("dimensions/minecraft/overworld"),
                overworldTarget.worldDirectory()
        );
        assertEquals(
                levelRoot.toRealPath().resolve("dimensions/minecraft/the_nether"),
                target.worldDirectory()
        );
        assertEquals("overworld-replacement", Files.readString(
                activePackRoot(overworldTarget.worldDirectory()).resolve("dimensions/overworld.json")
        ));
        assertEquals("replacement", replacementContent(target.worldDirectory()));
        assertEquals("overworld-original", Files.readString(overworldPaths.backup().resolve("original.txt")));
        assertEquals("nether-original", Files.readString(backup(nether).resolve("original.txt")));

        WorldGeneratorSnapshot overworldConfiguration = BukkitWorldConfiguration.snapshot(
                bukkitConfiguration.toFile(),
                "world"
        );
        WorldGeneratorSnapshot netherConfiguration = BukkitWorldConfiguration.snapshot(
                bukkitConfiguration.toFile(),
                "world_nether"
        );
        assertEquals("Iris:overworld", overworldConfiguration.generator());
        assertEquals(Long.valueOf(overworldSeed), overworldConfiguration.seed());
        assertEquals("Iris:underworld", netherConfiguration.generator());
        assertEquals(Long.valueOf(SEED), netherConfiguration.seed());

        List<Transaction> published = WorldReplacementJournal.load(dataDirectory, levelRoot);
        assertEquals(2, published.size());
        assertTrue(published.stream().anyMatch(transaction ->
                transaction.worldKey().equals(overworldKey)
                        && transaction.dimension().equals("overworld")
                        && transaction.phase() == Phase.PUBLISHED));
        assertTrue(published.stream().anyMatch(transaction ->
                transaction.worldKey().equals(WORLD_KEY)
                        && transaction.dimension().equals("underworld")
                        && transaction.phase() == Phase.PUBLISHED));
    }

    @Test
    public void roundTripsBlankAndWhitespaceOriginalGenerators() throws Exception {
        for (String generator : List.of("", "   ")) {
            WorldGeneratorSnapshot original = new WorldGeneratorSnapshot(
                    true,
                    true,
                    true,
                    generator,
                    false,
                    null
            );
            Transaction transaction = transaction(UUID.randomUUID(), original, Phase.PREPARED, false, "replacement");
            WorldReplacementJournal.write(dataDirectory, transaction);

            Transaction loaded = loadSingle();

            assertEquals(generator, loaded.originalConfiguration().generator());
            WorldReplacementJournal.delete(dataDirectory, transaction.id());
        }
    }

    @Test
    public void usesPaperStartupAliasesForIrisReplacementJournals() {
        assertEquals(
                "world_iris_moon",
                WorldReplacementJournal.logicalWorldName(levelRoot, new WorldSlotKey("iris", "moon"))
        );
        assertEquals(
                "world_iris_world_nether",
                WorldReplacementJournal.logicalWorldName(levelRoot, new WorldSlotKey("iris", "world_nether"))
        );
    }

    @Test
    public void skipsJournalStagedAgainstAnotherLevelRootInsteadOfAborting() throws Exception {
        Transaction transaction = stagedTransaction(Phase.CLEANUP_PENDING, true, "original");
        Path otherLevelRoot = Files.createDirectories(serverRoot.resolve("renamed-world"));

        WorldReplacementBootstrap.ReconcileResult result = WorldReplacementBootstrap.reconcile(
                dataDirectory,
                otherLevelRoot,
                bukkitConfiguration,
                ignored -> {
                }
        );

        assertEquals(1, result.skipped());
        assertEquals(0, result.published());
        assertEquals(0, result.rolledBack());
        assertEquals(0, result.retained());
        assertTrue(Files.exists(dataDirectory
                .resolve(WorldReplacementJournal.DIRECTORY_NAME)
                .resolve(transaction.id() + ".properties")));
    }

    private WorldReplacementBootstrap.ReconcileResult reconcile() throws Exception {
        return WorldReplacementBootstrap.reconcile(
                dataDirectory,
                levelRoot,
                bukkitConfiguration,
                ignored -> {
                }
        );
    }

    private Transaction stagedTransaction(Phase phase, boolean originalPresent, String originalContent)
            throws Exception {
        WorldGeneratorSnapshot original = BukkitWorldConfiguration.snapshot(
                bukkitConfiguration.toFile(),
                "world_nether"
        );
        return transaction(UUID.randomUUID(), original, phase, originalPresent, originalContent);
    }

    private Transaction transaction(
            UUID id,
            WorldGeneratorSnapshot original,
            Phase phase,
            boolean originalPresent,
            String originalContent
    ) throws Exception {
        ReplacementPaths paths = WorldReplacementFilesystem.paths(target, id);
        if (originalPresent) {
            writeOriginalTarget(paths, originalContent);
        }
        Path pack = copyUnderworldStage(paths.stage());
        String fingerprint = WorldReplacementFilesystem.fingerprintPack(pack);
        Transaction transaction = new Transaction(
                id,
                WORLD_KEY,
                "world_nether",
                target.levelRoot(),
                "underworld",
                SEED,
                fingerprint,
                original,
                originalPresent,
                phase
        );
        WorldReplacementJournal.write(dataDirectory, transaction);
        return transaction;
    }

    private void configureReplacement(Transaction transaction) throws Exception {
        BukkitWorldConfiguration.GeneratorReplacement result = BukkitWorldConfiguration.replaceIfMatching(
                bukkitConfiguration.toFile(),
                transaction.worldName(),
                transaction.originalConfiguration(),
                transaction.dimension(),
                transaction.seed()
        );
        assertTrue(result.applied());
    }

    private void configureExistingReplacement() throws Exception {
        BukkitWorldConfiguration.register(
                bukkitConfiguration.toFile(),
                "world_nether",
                "underworld",
                SEED
        );
    }

    private void restoreOriginalConfiguration(Transaction transaction) throws Exception {
        assertTrue(BukkitWorldConfiguration.restoreIfMatching(
                bukkitConfiguration.toFile(),
                transaction.worldName(),
                WorldReplacementBootstrap.replacementSnapshot(transaction),
                transaction.originalConfiguration()
        ));
    }

    private Transaction loadSingle() throws Exception {
        return WorldReplacementJournal.load(dataDirectory, levelRoot).getFirst();
    }

    private ReplacementPaths paths(Transaction transaction) {
        return WorldReplacementFilesystem.paths(target, transaction.id());
    }

    private Path backup(Transaction transaction) {
        return paths(transaction).backup();
    }

    private void writeOriginalTarget(ReplacementPaths paths, String originalContent) throws Exception {
        Files.createDirectories(paths.target().resolve("data/paper"));
        Files.createDirectories(paths.target().resolve("data/minecraft"));
        Files.writeString(paths.target().resolve("original.txt"), originalContent);
        Files.writeString(paths.target().resolve("data/paper/metadata.dat"), "metadata");
        Files.writeString(paths.target().resolve("data/paper/level_overrides.dat"), "overrides");
        Files.writeString(paths.target().resolve("data/minecraft/world_gen_settings.dat"), "generation");
    }

    private String replacementContent(Path worldDirectory) throws Exception {
        return Files.readString(activePackRoot(worldDirectory).resolve("dimensions/underworld.json"));
    }

    private static Path copyUnderworldStage(Path world) throws Exception {
        Path source = world.resolveSibling(world.getFileName() + ".underworld.pack-source");
        Path dimension = source.resolve("dimensions/underworld.json");
        Files.createDirectories(dimension.getParent());
        Files.writeString(dimension, "replacement");
        try (Stream<Path> entries = Files.walk(underworldStagePrototype)) {
            for (Path entry : entries.toList()) {
                Path destination = world.resolve(
                        underworldStagePrototype.relativize(entry).toString());
                if (Files.isDirectory(entry)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(entry, destination, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
        return activePackRoot(world);
    }

    private static Path createStagedHistory(Path world, String dimensionKey, String content, long seed)
            throws Exception {
        Path source = world.resolveSibling(world.getFileName() + "." + dimensionKey + ".pack-source");
        Path dimension = source.resolve("dimensions").resolve(dimensionKey + ".json");
        Files.createDirectories(dimension.getParent());
        Files.writeString(dimension, content);
        String fingerprint = GenerationPackFingerprint.compute(
                source,
                GenerationPackFingerprint.CURRENT_VERSION
        );
        Files.createDirectory(world);
        return GenerationHistory.create(
                world,
                source,
                fingerprint,
                seed,
                generationContract(dimensionKey),
                GenerationRegistryContract.empty()
        ).activePackRoot();
    }

    private static Path activePackRoot(Path world) throws IOException {
        return GenerationHistory.open(world).activePackRoot();
    }

    private static GenerationEpoch.DimensionContract generationContract(String dimensionKey) {
        return new GenerationEpoch.DimensionContract(
                dimensionKey,
                "iris:" + dimensionKey + "_type",
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
                "c".repeat(64)
        );
    }
}
