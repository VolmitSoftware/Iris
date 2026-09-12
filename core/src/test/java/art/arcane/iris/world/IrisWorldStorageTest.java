package art.arcane.iris.world;

import art.arcane.iris.world.history.GenerationEpoch;
import art.arcane.iris.world.history.GenerationEpochContractFactory;
import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.iris.world.history.GenerationPackFingerprint;
import art.arcane.iris.world.history.GenerationRegistryContract;
import org.bukkit.NamespacedKey;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IrisWorldStorageTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void derivesPaperDimensionRootFromNamespacedKey() throws Exception {
        File levelRoot = temporaryFolder.newFolder("world");
        NamespacedKey key = new NamespacedKey("iris", "runtime/studio");

        File dimensionRoot = IrisWorldStorage.dimensionRoot(levelRoot, key);

        assertEquals(new File(levelRoot, "dimensions/iris/runtime/studio").getAbsoluteFile(), dimensionRoot);
        assertEquals(key, IrisWorldStorage.keyFromDimensionRoot(levelRoot, dimensionRoot).orElseThrow());
    }

    @Test
    public void separatesLevelRootFromDimensionRoot() throws Exception {
        File levelRoot = temporaryFolder.newFolder("world");
        File dimensionRoot = new File(levelRoot, "dimensions/minecraft/overworld");

        assertEquals(levelRoot.getAbsoluteFile(), IrisWorldStorage.levelRoot(dimensionRoot));
    }

    @Test
    public void mapsIrisWorldNamesToOwnedPaperKeys() {
        assertEquals(NamespacedKey.minecraft("overworld"), IrisWorldStorage.keyFromName("world", "world"));
        assertEquals(NamespacedKey.minecraft("the_nether"), IrisWorldStorage.keyFromName("world_nether", "world"));
        assertEquals(NamespacedKey.minecraft("the_end"), IrisWorldStorage.keyFromName("world_the_end", "world"));
        assertEquals(new NamespacedKey("iris", "iris_world"), IrisWorldStorage.keyFromName("Iris World", "world"));
    }

    @Test
    public void mapsPaperStartupNamesWithoutChangingLogicalNames() {
        NamespacedKey worldKey = new NamespacedKey("iris", "moon");

        assertEquals("world_iris_moon", IrisWorldStorage.configuredWorldName(worldKey, "world"));
        assertEquals(
                "world_iris_moon",
                IrisWorldStorage.configuredWorldName(new WorldSlotKey("iris", "moon"), "world")
        );
        assertEquals(worldKey, IrisWorldStorage.keyFromConfiguredWorldName("world_iris_moon", "world"));
        assertEquals("moon", IrisWorldStorage.logicalName(worldKey, "world"));
        assertEquals(NamespacedKey.minecraft("overworld"),
                IrisWorldStorage.keyFromConfiguredWorldName("world", "world"));
        assertEquals(NamespacedKey.minecraft("the_nether"),
                IrisWorldStorage.keyFromConfiguredWorldName("world_nether", "world"));
        assertEquals(NamespacedKey.minecraft("the_end"),
                IrisWorldStorage.keyFromConfiguredWorldName("world_the_end", "world"));
    }

    @Test
    public void configuredWorldNameRejectsUnsafeManagedKeys() {
        assertThrows(IllegalArgumentException.class,
                () -> IrisWorldStorage.keyFromConfiguredWorldName("world_iris_nested/world", "world"));
        assertThrows(IllegalArgumentException.class,
                () -> IrisWorldStorage.configuredWorldName(new NamespacedKey("other", "moon"), "world"));
    }

    @Test
    public void replacementKeyAcceptsExplicitCanonicalIdentities() {
        assertEquals(
                NamespacedKey.minecraft("overworld"),
                IrisWorldStorage.replacementKeyFromName("minecraft:overworld", "world")
        );
        assertEquals(
                NamespacedKey.minecraft("the_nether"),
                IrisWorldStorage.replacementKeyFromName("MINECRAFT:THE_NETHER", "world")
        );
        assertEquals(
                NamespacedKey.minecraft("the_end"),
                IrisWorldStorage.replacementKeyFromName("minecraft:the_end", "world")
        );
        assertEquals(
                new NamespacedKey("iris", "moon"),
                IrisWorldStorage.replacementKeyFromName("iris:moon", "world")
        );
        assertEquals(
                new NamespacedKey("iris", "main"),
                IrisWorldStorage.replacementKeyFromName("iris:main", "world")
        );
        assertEquals(
                new NamespacedKey("iris", "survival"),
                IrisWorldStorage.replacementKeyFromName("iris:survival", "survival")
        );
        assertEquals(
                new NamespacedKey("iris", "moon"),
                IrisWorldStorage.replacementKeyFromName("world_iris_moon", "world")
        );
    }

    @Test
    public void replacementKeyResolvesFriendlyVanillaAliases() {
        assertEquals(NamespacedKey.minecraft("overworld"),
                IrisWorldStorage.replacementKeyFromName("main", "world"));
        assertEquals(NamespacedKey.minecraft("overworld"),
                IrisWorldStorage.replacementKeyFromName("overworld", "world"));
        assertEquals(NamespacedKey.minecraft("the_nether"),
                IrisWorldStorage.replacementKeyFromName("nether", "world"));
        assertEquals(NamespacedKey.minecraft("the_nether"),
                IrisWorldStorage.replacementKeyFromName("the_nether", "world"));
        assertEquals(NamespacedKey.minecraft("the_end"),
                IrisWorldStorage.replacementKeyFromName("end", "world"));
        assertEquals(NamespacedKey.minecraft("the_end"),
                IrisWorldStorage.replacementKeyFromName("the_end", "world"));
    }

    @Test
    public void replacementKeyResolvesConfiguredVanillaNamesBeforeFriendlyAliases() {
        assertEquals(NamespacedKey.minecraft("overworld"),
                IrisWorldStorage.replacementKeyFromName("survival", "survival"));
        assertEquals(NamespacedKey.minecraft("the_nether"),
                IrisWorldStorage.replacementKeyFromName("survival_nether", "survival"));
        assertEquals(NamespacedKey.minecraft("the_end"),
                IrisWorldStorage.replacementKeyFromName("survival_the_end", "survival"));
        assertEquals(NamespacedKey.minecraft("overworld"),
                IrisWorldStorage.replacementKeyFromName("nether", "nether"));
    }

    @Test
    public void replacementKeyMapsOtherBareNamesIntoIrisNamespace() {
        assertEquals(
                new NamespacedKey("iris", "moon_base"),
                IrisWorldStorage.replacementKeyFromName("Moon Base", "world")
        );
    }

    @Test
    public void replacementKeyRejectsUnsafePathsAndInvalidIdentifiers() {
        assertThrows(IllegalArgumentException.class,
                () -> IrisWorldStorage.replacementKeyFromName("../world", "world"));
        assertThrows(IllegalArgumentException.class,
                () -> IrisWorldStorage.replacementKeyFromName("iris:nested/world", "world"));
        assertThrows(IllegalArgumentException.class,
                () -> IrisWorldStorage.replacementKeyFromName("iris:", "world"));
        assertThrows(IllegalArgumentException.class,
                () -> IrisWorldStorage.replacementKeyFromName("world", " "));
    }

    @Test
    public void managedKeyRejectsMainWorldsAndUnsafePaths() {
        assertEquals(new NamespacedKey("iris", "iris_world"),
                IrisWorldStorage.managedKeyFromName("Iris World", "world"));
        assertEquals(new NamespacedKey("iris", "iris_world"),
                IrisWorldStorage.managedKeyFromName("iris:iris_world", "world"));
        assertThrows(IllegalArgumentException.class,
                () -> IrisWorldStorage.managedKeyFromName("world", "world"));
        assertThrows(IllegalArgumentException.class,
                () -> IrisWorldStorage.managedKeyFromName("minecraft:overworld", "world"));
        assertThrows(IllegalArgumentException.class,
                () -> IrisWorldStorage.managedKeyFromName("../world", "world"));
        assertThrows(IllegalArgumentException.class,
                () -> IrisWorldStorage.managedKeyFromName("iris:nested/world", "world"));
        assertThrows(IllegalArgumentException.class,
                () -> IrisWorldStorage.managedKeyFromName("iris:unsafe.world", "world"));
    }

    @Test
    public void managedKeyAcceptsTheConfiguredBukkitStartupName() {
        assertEquals(new NamespacedKey("iris", "mvtest"),
                IrisWorldStorage.managedKeyFromName("world_iris_mvtest", "world"));
        assertEquals(new NamespacedKey("iris", "mvtest"),
                IrisWorldStorage.managedKeyFromName("survival_iris_mvtest", "survival"));
        assertEquals(new NamespacedKey("iris", "iris_mvtest"),
                IrisWorldStorage.managedKeyFromName("iris_mvtest", "world"));
        assertEquals(new NamespacedKey("iris", "world_iris_mvtest"),
                IrisWorldStorage.managedKeyFromName("world_iris_mvtest", "survival"));
    }

    @Test
    public void explicitManagedIdentityDoesNotRequireServerLevelLookup() {
        NamespacedKey worldKey = new NamespacedKey("iris", "probe");

        assertEquals(worldKey, IrisWorldStorage.managedKeyFromName(worldKey.toString()));
        assertEquals("probe", IrisWorldStorage.logicalName(worldKey));
    }

    @Test
    public void mapsOwnedPaperKeysBackToLogicalWorldNames() {
        assertEquals("world", IrisWorldStorage.logicalName(NamespacedKey.minecraft("overworld"), "world"));
        assertEquals("world_nether", IrisWorldStorage.logicalName(NamespacedKey.minecraft("the_nether"), "world"));
        assertEquals("world_the_end", IrisWorldStorage.logicalName(NamespacedKey.minecraft("the_end"), "world"));
        assertEquals("iris_world", IrisWorldStorage.logicalName(new NamespacedKey("iris", "iris_world"), "world"));
    }

    @Test
    public void readsLevelNameFromServerPropertiesFile() throws Exception {
        File serverProperties = temporaryFolder.newFile("server.properties");
        Files.write(serverProperties.toPath(), "level-name=psycho_world\nmotd=A Minecraft Server\n".getBytes(StandardCharsets.UTF_8));

        assertEquals("psycho_world", IrisWorldStorage.levelNameFromProperties(serverProperties));
    }

    @Test
    public void defaultsLevelNameWhenServerPropertiesMissing() {
        File missing = new File(temporaryFolder.getRoot(), "missing/server.properties");

        assertEquals("world", IrisWorldStorage.levelNameFromProperties(missing));
    }

    @Test
    public void defaultsLevelNameWhenPropertyAbsentOrBlank() {
        assertEquals("world", IrisWorldStorage.levelNameFromProperties(new Properties()));

        Properties blank = new Properties();
        blank.setProperty("level-name", "   ");
        assertEquals("world", IrisWorldStorage.levelNameFromProperties(blank));
    }

    @Test
    public void trimsLevelNameFromProperties() {
        Properties padded = new Properties();
        padded.setProperty("level-name", "  main_level  ");
        assertEquals("main_level", IrisWorldStorage.levelNameFromProperties(padded));
    }

    @Test
    public void rejectsKeysThatEscapeNamespaceStorage() throws Exception {
        File levelRoot = temporaryFolder.newFolder("world");
        NamespacedKey key = NamespacedKey.minecraft("../outside");

        assertThrows(IllegalArgumentException.class, () -> IrisWorldStorage.dimensionRoot(levelRoot, key));
    }

    @Test
    public void safeManagedDimensionRootRejectsSymlinkedStorage() throws Exception {
        File levelRoot = temporaryFolder.newFolder("managed-world");
        Path dimensions = Files.createDirectories(levelRoot.toPath().resolve("dimensions"));
        Path outside = temporaryFolder.newFolder("outside").toPath();
        Files.createSymbolicLink(dimensions.resolve("iris"), outside);
        NamespacedKey key = new NamespacedKey("iris", "probe");

        assertThrows(IllegalArgumentException.class, () -> IrisWorldStorage.requireSafeManagedDimensionRoot(
                levelRoot,
                key));
        assertFalse(IrisWorldStorage.isExistingManagedDimensionRoot(levelRoot, key));
    }

    @Test
    public void existingManagedDimensionRootRequiresRealDirectory() throws Exception {
        File levelRoot = temporaryFolder.newFolder("managed-world-admission");
        Path namespace = Files.createDirectories(levelRoot.toPath().resolve("dimensions/iris"));
        NamespacedKey missing = new NamespacedKey("iris", "missing");
        NamespacedKey file = new NamespacedKey("iris", "file");
        NamespacedKey directory = new NamespacedKey("iris", "directory");
        Files.writeString(namespace.resolve("file"), "not a directory");
        Files.createDirectory(namespace.resolve("directory"));

        assertFalse(IrisWorldStorage.isExistingManagedDimensionRoot(levelRoot, missing));
        assertFalse(IrisWorldStorage.isExistingManagedDimensionRoot(levelRoot, file));
        assertTrue(IrisWorldStorage.isExistingManagedDimensionRoot(levelRoot, directory));
    }

    @Test
    public void persistentDimensionRootAllowsOnlyManagedAndExactVanillaSlots() throws Exception {
        File levelRoot = temporaryFolder.newFolder("persistent-root");

        assertEquals(
                IrisWorldStorage.dimensionRoot(levelRoot, new NamespacedKey("iris", "moon")).getCanonicalFile(),
                IrisWorldStorage.requireSafePersistentDimensionRoot(
                        levelRoot,
                        new NamespacedKey("iris", "moon")
                )
        );
        assertEquals(
                IrisWorldStorage.dimensionRoot(levelRoot, NamespacedKey.minecraft("overworld")).getCanonicalFile(),
                IrisWorldStorage.requireSafePersistentDimensionRoot(
                        levelRoot,
                        NamespacedKey.minecraft("overworld")
                )
        );
        assertEquals(
                IrisWorldStorage.dimensionRoot(levelRoot, NamespacedKey.minecraft("the_nether")).getCanonicalFile(),
                IrisWorldStorage.requireSafePersistentDimensionRoot(
                        levelRoot,
                        NamespacedKey.minecraft("the_nether")
                )
        );
        assertEquals(
                IrisWorldStorage.dimensionRoot(levelRoot, NamespacedKey.minecraft("the_end")).getCanonicalFile(),
                IrisWorldStorage.requireSafePersistentDimensionRoot(
                        levelRoot,
                        NamespacedKey.minecraft("the_end")
                )
        );
        assertThrows(
                ExactWorldSlotPathPolicy.Rejection.class,
                () -> IrisWorldStorage.requireSafePersistentDimensionRoot(
                        levelRoot,
                        NamespacedKey.minecraft("custom")
                )
        );
        assertThrows(
                ExactWorldSlotPathPolicy.Rejection.class,
                () -> IrisWorldStorage.requireSafePersistentDimensionRoot(
                        levelRoot,
                        new NamespacedKey("foreign", "moon")
                )
        );
    }

    @Test
    public void frozenDimensionRootUsesCanonicalLevelStorageWhenPresent() throws Exception {
        File worldContainer = temporaryFolder.newFolder("server-direct");
        File levelRoot = Files.createDirectory(worldContainer.toPath().resolve("world")).toFile();
        NamespacedKey worldKey = new NamespacedKey("iris", "moon");
        File dimensionRoot = IrisWorldStorage.dimensionRoot(levelRoot, worldKey);
        Files.createDirectories(dimensionRoot.toPath());

        assertEquals(
                dimensionRoot,
                IrisWorldStorage.requireFrozenDimensionRoot(
                        worldContainer,
                        levelRoot,
                        "world_iris_moon",
                        worldKey
                )
        );
    }

    @Test
    public void frozenDimensionRootUsesCurrentCraftBukkitConfiguredStorage() throws Exception {
        File worldContainer = temporaryFolder.newFolder("server-configured");
        File levelRoot = Files.createDirectory(worldContainer.toPath().resolve("world")).toFile();
        NamespacedKey worldKey = new NamespacedKey("iris", "moon");
        File configuredLevelRoot = new File(worldContainer, "world_iris_moon");
        File configuredDimensionRoot = IrisWorldStorage.dimensionRoot(configuredLevelRoot, worldKey);
        Files.createDirectories(configuredDimensionRoot.toPath());

        assertEquals(
                configuredDimensionRoot,
                IrisWorldStorage.requireFrozenDimensionRoot(
                        worldContainer,
                        levelRoot,
                        "world_iris_moon",
                        worldKey
                )
        );
    }

    @Test
    public void frozenDimensionRootRejectsMissingAndAmbiguousStorage() throws Exception {
        File worldContainer = temporaryFolder.newFolder("server-ambiguous");
        File levelRoot = Files.createDirectory(worldContainer.toPath().resolve("world")).toFile();
        NamespacedKey worldKey = new NamespacedKey("iris", "moon");

        assertThrows(
                IllegalStateException.class,
                () -> IrisWorldStorage.requireFrozenDimensionRoot(
                        worldContainer,
                        levelRoot,
                        "world_iris_moon",
                        worldKey
                )
        );

        Files.createDirectories(IrisWorldStorage.dimensionRoot(levelRoot, worldKey).toPath());
        File configuredLevelRoot = new File(worldContainer, "world_iris_moon");
        Files.createDirectories(IrisWorldStorage.dimensionRoot(configuredLevelRoot, worldKey).toPath());

        assertThrows(
                IllegalStateException.class,
                () -> IrisWorldStorage.requireFrozenDimensionRoot(
                        worldContainer,
                        levelRoot,
                        "world_iris_moon",
                        worldKey
                )
        );
    }

    @Test
    public void frozenDimensionRootRejectsSymlinkedStorage() throws Exception {
        File worldContainer = temporaryFolder.newFolder("server-symlink");
        File levelRoot = Files.createDirectory(worldContainer.toPath().resolve("world")).toFile();
        Path namespaceRoot = Files.createDirectories(levelRoot.toPath().resolve("dimensions"));
        Path outside = temporaryFolder.newFolder("frozen-outside").toPath();
        Files.createSymbolicLink(namespaceRoot.resolve("iris"), outside);

        assertThrows(
                IllegalStateException.class,
                () -> IrisWorldStorage.requireFrozenDimensionRoot(
                        worldContainer,
                        levelRoot,
                        "world_iris_moon",
                        new NamespacedKey("iris", "moon")
                )
        );
    }

    @Test
    public void activeGenerationPackRequiresValidImmutableHistory() throws Exception {
        File dimensionRoot = temporaryFolder.newFolder("generation-history-world");

        assertThrows(
                IllegalStateException.class,
                () -> IrisWorldStorage.requireActiveGenerationPackRoot(dimensionRoot)
        );

        Path irisRoot = Files.createDirectory(dimensionRoot.toPath().resolve("iris"));
        Path externalGeneration = temporaryFolder.newFolder("external-generation").toPath();
        Files.createSymbolicLink(irisRoot.resolve("generation"), externalGeneration);

        assertThrows(
                IllegalStateException.class,
                () -> IrisWorldStorage.requireActiveGenerationPackRoot(dimensionRoot)
        );

        Files.delete(irisRoot.resolve("generation"));
        Path sourcePack = createGenerationPack("storage-active-pack");
        GenerationHistory history = createGenerationHistory(dimensionRoot.toPath(), sourcePack, 42L);
        assertEquals(
                history.activePackRoot().toFile(),
                IrisWorldStorage.requireActiveGenerationPackRoot(dimensionRoot, 42L)
        );
        assertThrows(
                IllegalStateException.class,
                () -> IrisWorldStorage.requireActiveGenerationPackRoot(dimensionRoot, 43L)
        );
    }

    @Test
    public void managedWorldStorageIsOnlyReportedForRealIrisWorldDirectories() throws Exception {
        File levelRoot = temporaryFolder.newFolder("managed-storage-level-root");

        assertFalse(IrisWorldStorage.hasManagedWorldStorage(null));
        assertFalse(IrisWorldStorage.hasManagedWorldStorage(levelRoot));

        Path namespace = Files.createDirectories(levelRoot.toPath().resolve("dimensions/iris"));
        assertFalse(IrisWorldStorage.hasManagedWorldStorage(levelRoot));

        Files.writeString(namespace.resolve("stray"), "not a world directory");
        assertFalse(IrisWorldStorage.hasManagedWorldStorage(levelRoot));

        Files.createDirectory(namespace.resolve("moon"));
        assertTrue(IrisWorldStorage.hasManagedWorldStorage(levelRoot));
    }

    private Path createGenerationPack(String name) throws IOException {
        Path pack = temporaryFolder.newFolder(name).toPath();
        Path dimensions = Files.createDirectories(pack.resolve("dimensions"));
        Files.writeString(dimensions.resolve("overworld.json"), "{}");
        return pack;
    }

    private static GenerationHistory createGenerationHistory(Path world, Path pack, long seed)
            throws IOException {
        String fingerprint = GenerationPackFingerprint.compute(
                pack,
                GenerationPackFingerprint.CURRENT_VERSION
        );
        GenerationEpoch.DimensionContract dimensionContract = new GenerationEpoch.DimensionContract(
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
                "c".repeat(64)
        );
        return GenerationHistory.create(
                world,
                pack,
                fingerprint,
                seed,
                dimensionContract,
                GenerationRegistryContract.empty()
        );
    }
}
