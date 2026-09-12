package art.arcane.iris.world;

import art.arcane.iris.world.lifecycle.MissingWorldStorageLog;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
}
