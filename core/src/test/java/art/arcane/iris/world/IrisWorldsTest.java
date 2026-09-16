package art.arcane.iris.world;

import art.arcane.iris.world.lifecycle.MissingWorldStorageLog;
import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.volmlib.util.collection.KMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

public class IrisWorldsTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void registryFileBelongsToSelectedLevelRoot() throws Exception {
        Path levelRoot = temporaryFolder.newFolder("world").toPath();

        assertEquals(
                levelRoot.toAbsolutePath().normalize().resolve("iris/worlds.json"),
                IrisWorlds.registryFile(levelRoot));
    }

    @Test
    public void bukkitWorldFilteringUsesExactStorageInSelectedRoot() throws Exception {
        Path selectedRoot = temporaryFolder.newFolder("world").toPath();
        Path otherRoot = temporaryFolder.newFolder("archive").toPath();
        Files.createDirectories(selectedRoot.resolve("dimensions/minecraft/overworld"));
        Files.createDirectories(selectedRoot.resolve("dimensions/iris/moon"));
        Files.createDirectories(otherRoot.resolve("dimensions/iris/foreign"));
        Files.createDirectories(selectedRoot.resolve("dimensions/iris"));
        Files.writeString(selectedRoot.resolve("dimensions/iris/not_a_directory"), "not storage");

        Map<String, String> configuredWorlds = new LinkedHashMap<>();
        configuredWorlds.put("world", "overworld");
        configuredWorlds.put("world_nether", "underworld");
        configuredWorlds.put("world_iris_moon", "overworld");
        configuredWorlds.put("moon", "overworld");
        configuredWorlds.put("world_iris_foreign", "overworld");
        configuredWorlds.put("archive_iris_foreign", "overworld");
        configuredWorlds.put("foreign", "overworld");
        configuredWorlds.put("world_iris_not_a_directory", "overworld");
        configuredWorlds.put("world_iris_missing", "overworld");

        Map<String, String> selected = IrisWorlds.filterBukkitWorldsByStorage(selectedRoot, configuredWorlds);
        Map<String, String> other = IrisWorlds.filterBukkitWorldsByStorage(otherRoot, configuredWorlds);

        assertEquals(Set.of("world", "world_iris_moon"), selected.keySet());
        assertEquals(Set.of("archive_iris_foreign"), other.keySet());
    }

    @Test
    public void bukkitGeneratorStringsAreMatchedCaseInsensitively() {
        assertEquals("pack", IrisWorlds.generatorLoadKey("Iris:pack", "overworld"));
        assertEquals("pack", IrisWorlds.generatorLoadKey("iris:pack", "overworld"));
        assertEquals("pack", IrisWorlds.generatorLoadKey("IRIS:pack", "overworld"));
        assertEquals("overworld", IrisWorlds.generatorLoadKey("Iris", "overworld"));
        assertEquals("overworld", IrisWorlds.generatorLoadKey("iris", "overworld"));
        assertNull(IrisWorlds.generatorLoadKey("VoidGen", "overworld"));
        assertNull(IrisWorlds.generatorLoadKey("Irissy:pack", "overworld"));
        assertNull(IrisWorlds.generatorLoadKey(null, "overworld"));
    }

    @Test
    public void orphanedIrisWorldsAreReportedButVanillaSlotsAreNot() throws Exception {
        MissingWorldStorageLog.reset();
        Path levelRoot = temporaryFolder.newFolder("orphan-report", "world").toPath();
        Files.createDirectories(levelRoot.resolve("dimensions/iris/present"));

        Map<String, String> configuredWorlds = new LinkedHashMap<>();
        configuredWorlds.put("world_nether", "underworld");
        configuredWorlds.put("world_iris_present", "overworld");
        configuredWorlds.put("world_iris_gone", "overworld");

        assertEquals(
                Set.of("world_iris_present"),
                IrisWorlds.filterBukkitWorldsByStorage(levelRoot, configuredWorlds).keySet());
        assertTrue(MissingWorldStorageLog.hasWarned("world_iris_gone"));
        assertFalse("a vanilla slot that was never created is not an orphan",
                MissingWorldStorageLog.hasWarned("world_nether"));
        assertFalse(MissingWorldStorageLog.hasWarned("world_iris_present"));
        MissingWorldStorageLog.reset();
    }

    /**
     * The registry is built from a private constructor behind a static cache, so the isolation contract is
     * asserted against the source: one unusable world folder used to throw out of {@code clean()}, through
     * the constructor and into the cache, which returned null and NPE'd every caller.
     */
    @Test
    public void bukkitWorldFilteringRecognizesCurrentCraftBukkitConfiguredStorage() throws Exception {
        Path worldContainer = temporaryFolder.newFolder("configured-server").toPath();
        Path levelRoot = Files.createDirectory(worldContainer.resolve("world"));
        Files.createDirectories(
                worldContainer.resolve("world_iris_moon/dimensions/iris/moon")
        );

        Map<String, String> selected = IrisWorlds.filterBukkitWorldsByStorage(
                levelRoot,
                Map.of("world_iris_moon", "overworld")
        );

        assertEquals(Map.of("world_iris_moon", "overworld"), selected);
    }

    @Test
    public void registryHousekeepingDoesNotOpenFrozenPacks() throws Exception {
        Path levelRoot = temporaryFolder.newFolder("housekeeping").toPath();
        Path first = Files.createDirectories(levelRoot.resolve("dimensions/iris/first"));
        Files.createDirectories(first.resolve("iris/generation"));
        Files.writeString(first.resolve("iris/generation/manifest.json"), "invalid history");
        Files.createDirectories(levelRoot.resolve("dimensions/iris/second"));
        KMap<String, String> entries = new KMap<>();
        entries.put("iris:first", "overworld");
        IrisWorlds registry = registry(levelRoot, entries);

        try (MockedStatic<GenerationHistory> history = mockStatic(GenerationHistory.class)) {
            registry.clean();
            registry.save();
            registry.put("iris:second", "missing_dimension");
            registry.save();
            history.verifyNoInteractions();
        }

        assertEquals(Map.of("iris:first", "overworld", "iris:second", "missing_dimension"), entries);
        JsonObject saved = savedRegistry(levelRoot);
        assertEquals("overworld", saved.get("iris:first").getAsString());
        assertEquals("missing_dimension", saved.get("iris:second").getAsString());
    }

    @Test
    public void savingPersistsEntriesRemovedByStorageCleanup() throws Exception {
        Path levelRoot = temporaryFolder.newFolder("cleanup").toPath();
        Files.createDirectories(levelRoot.resolve("dimensions/iris/retained"));
        KMap<String, String> entries = new KMap<>();
        entries.put("iris:retained", "overworld");
        entries.put("iris:missing", "overworld");
        IrisWorlds registry = registry(levelRoot, entries);

        registry.save();

        assertEquals(Map.of("iris:retained", "overworld"), entries);
        assertEquals(Set.of("iris:retained"), savedRegistry(levelRoot).keySet());
    }

    @Test
    public void failedPutRestoresPreviousValueAndCanBeRetried() throws Exception {
        Path levelRoot = temporaryFolder.newFolder("put-rollback").toPath();
        Files.createDirectories(levelRoot.resolve("dimensions/iris/retained"));
        Path blockedParent = levelRoot.resolve("iris");
        Files.writeString(blockedParent, "not a directory");
        KMap<String, String> entries = new KMap<>();
        entries.put("iris:retained", "overworld");
        IrisWorlds registry = registry(levelRoot, entries);

        assertThrows(UncheckedIOException.class, () -> registry.put("iris:retained", "replacement"));
        assertEquals("overworld", entries.get("iris:retained"));
        Files.delete(blockedParent);
        registry.save();

        assertEquals("overworld", savedRegistry(levelRoot).get("iris:retained").getAsString());
    }

    @Test
    public void failedRemovalRestoresEntryAndCanBeRetried() throws Exception {
        Path levelRoot = temporaryFolder.newFolder("remove-rollback").toPath();
        Files.createDirectories(levelRoot.resolve("dimensions/iris/retained"));
        Path blockedParent = levelRoot.resolve("iris");
        Files.writeString(blockedParent, "not a directory");
        KMap<String, String> entries = new KMap<>();
        entries.put("iris:retained", "overworld");
        IrisWorlds registry = registry(levelRoot, entries);

        assertThrows(UncheckedIOException.class, () -> registry.remove("iris:retained"));
        assertEquals("overworld", entries.get("iris:retained"));
        Files.delete(blockedParent);
        assertTrue(registry.remove("iris:retained"));

        assertTrue(savedRegistry(levelRoot).isEmpty());
    }

    private static IrisWorlds registry(Path levelRoot, KMap<String, String> entries) throws Exception {
        IrisWorlds registry = mock(IrisWorlds.class, CALLS_REAL_METHODS);
        setField(registry, "levelRoot", levelRoot.toAbsolutePath().normalize());
        setField(registry, "registryFile", IrisWorlds.registryFile(levelRoot));
        setField(registry, "worlds", entries);
        return registry;
    }

    private static void setField(IrisWorlds registry, String name, Object value) throws Exception {
        Field field = IrisWorlds.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(registry, value);
    }

    private static JsonObject savedRegistry(Path levelRoot) throws Exception {
        return JsonParser.parseString(Files.readString(IrisWorlds.registryFile(levelRoot))).getAsJsonObject();
    }
}
