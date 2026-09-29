package art.arcane.iris;

import art.arcane.iris.world.lifecycle.BukkitStartupPaths;
import art.arcane.iris.world.lifecycle.MissingWorldStorageLog;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * The server enumerates levels from disk and CraftBukkit falls back to the vanilla generator whenever the
 * plugin cannot supply one, so a world with storage Iris cannot use has to stop startup before any level is
 * created. A world with no storage at all is only reported.
 */
public class IrisBootstrapWorldStorageContractTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void clearOrphanReports() {
        MissingWorldStorageLog.reset();
    }

    @Test
    public void startupIsRefusedWhenAConfiguredWorldLostItsPackSnapshot() throws Exception {
        Path serverRoot = temporaryFolder.newFolder("broken-server").toPath();
        Files.createDirectories(serverRoot.resolve("world/dimensions/iris/orphan2/region"));
        Files.writeString(serverRoot.resolve("world/dimensions/iris/orphan2/region/r.0.0.mca"), "terrain");
        writeServerProperties(serverRoot);
        writeBukkitWorlds(serverRoot, "world_iris_orphan2");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> IrisBootstrap.requireUsableWorldStorage(startupPaths(serverRoot)));

        assertTrue(failure.getMessage(), failure.getMessage().contains("world_iris_orphan2"));
        assertTrue(failure.getMessage(),
                failure.getMessage().contains(serverRoot.toRealPath().resolve("world/dimensions/iris/orphan2")
                        .toString()));
    }

    /**
     * Deleting a live world's folder and letting the server save it back leaves a data/ skeleton with no
     * regions and no iris/ directory. It owns nothing, so refusing to boot over it only strands the server.
     */
    @Test
    public void startupContinuesForAServerRewrittenHuskAndReportsItOnce() throws Exception {
        Path serverRoot = temporaryFolder.newFolder("husk-server").toPath();
        Files.createDirectories(serverRoot.resolve("world/dimensions/iris/husk/data/paper"));
        Files.writeString(serverRoot.resolve("world/dimensions/iris/husk/data/paper/level_overrides.dat"), "x");
        writeServerProperties(serverRoot);
        writeBukkitWorlds(serverRoot, "world_iris_husk");

        IrisBootstrap.requireUsableWorldStorage(startupPaths(serverRoot));

        assertTrue(MissingWorldStorageLog.hasWarned("world_iris_husk"));
    }

    /**
     * Both classifications of a hot-deleted world's husk stop the boot: UNUSABLE trips Iris' fail-closed
     * guard, and EMPTY excludes it from the dimension registry, which trips Paper's interactive world
     * migration gate. The husk has to leave the dimensions tree before either can happen.
     */
    @Test
    public void aHotDeletedWorldsHuskIsMovedOutOfTheDimensionsTree() throws Exception {
        Path serverRoot = temporaryFolder.newFolder("husk-quarantine").toPath();
        Files.createDirectories(serverRoot.resolve("world/dimensions/iris/husk/data/paper"));
        Files.writeString(serverRoot.resolve("world/dimensions/iris/husk/data/paper/level_overrides.dat"), "x");
        writeServerProperties(serverRoot);
        writeBukkitWorlds(serverRoot, "world_iris_husk");
        List<String> warnings = new ArrayList<>();

        IrisBootstrap.quarantineWorthlessHusks(startupPaths(serverRoot), warnings::add);
        IrisBootstrap.requireUsableWorldStorage(startupPaths(serverRoot));

        assertFalse(Files.exists(serverRoot.resolve("world/dimensions/iris/husk")));
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst(),
                warnings.getFirst().contains("/iris remove world=world_iris_husk delete=true"));
        assertTrue(MissingWorldStorageLog.hasWarned("world_iris_husk"));
    }

    /**
     * Paper enumerates the dimensions tree from disk, so a husk whose bukkit.yml entry is already gone stops
     * the boot exactly the same way. The sweep is over the tree, not over the configuration.
     */
    @Test
    public void aHuskWithNoBukkitConfigurationEntryIsStillMovedOut() throws Exception {
        Path serverRoot = temporaryFolder.newFolder("husk-unconfigured").toPath();
        Files.createDirectories(serverRoot.resolve("world/dimensions/iris/husk/data/minecraft"));
        Files.writeString(serverRoot.resolve("world/dimensions/iris/husk/data/minecraft/raids.dat"), "x");
        writeServerProperties(serverRoot);
        writeBukkitWorlds(serverRoot);
        List<String> warnings = new ArrayList<>();

        IrisBootstrap.quarantineWorthlessHusks(startupPaths(serverRoot), warnings::add);

        assertFalse(Files.exists(serverRoot.resolve("world/dimensions/iris/husk")));
        assertEquals(1, warnings.size());
    }

    @Test
    public void aWorldWithRealDataIsNeverQuarantinedAndStillStopsStartup() throws Exception {
        Path serverRoot = temporaryFolder.newFolder("husk-with-data").toPath();
        Files.createDirectories(serverRoot.resolve("world/dimensions/iris/kept/region"));
        Files.writeString(serverRoot.resolve("world/dimensions/iris/kept/region/r.0.0.mca"), "terrain");
        writeServerProperties(serverRoot);
        writeBukkitWorlds(serverRoot, "world_iris_kept");
        List<String> warnings = new ArrayList<>();

        IrisBootstrap.quarantineWorthlessHusks(startupPaths(serverRoot), warnings::add);

        assertTrue(Files.isDirectory(serverRoot.resolve("world/dimensions/iris/kept")));
        assertTrue(warnings.isEmpty());
        assertThrows(
                IllegalStateException.class,
                () -> IrisBootstrap.requireUsableWorldStorage(startupPaths(serverRoot)));
    }

    /**
     * The engine rebuilds iris/engine-data under a world folder the server itself recreated during save-all,
     * so an iris/ directory with no pack snapshot is the ambiguous state, not a worthless one.
     */
    @Test
    public void anIrisMarkerWithNoPackSnapshotStillFailsClosed() throws Exception {
        Path serverRoot = temporaryFolder.newFolder("husk-marker").toPath();
        Files.createDirectories(serverRoot.resolve("world/dimensions/iris/marked/iris/engine-data"));
        writeServerProperties(serverRoot);
        writeBukkitWorlds(serverRoot, "world_iris_marked");
        List<String> warnings = new ArrayList<>();

        IrisBootstrap.quarantineWorthlessHusks(startupPaths(serverRoot), warnings::add);

        assertTrue(Files.isDirectory(serverRoot.resolve("world/dimensions/iris/marked/iris/engine-data")));
        assertTrue(warnings.isEmpty());
        assertThrows(
                IllegalStateException.class,
                () -> IrisBootstrap.requireUsableWorldStorage(startupPaths(serverRoot)));
    }

    @Test
    public void startupContinuesForAWorldWhoseFolderIsGoneAndReportsItOnce() throws Exception {
        Path serverRoot = temporaryFolder.newFolder("orphan-server").toPath();
        Files.createDirectories(serverRoot.resolve("world/dimensions/iris"));
        writeServerProperties(serverRoot);
        writeBukkitWorlds(serverRoot, "world_iris_orphan1");

        IrisBootstrap.requireUsableWorldStorage(startupPaths(serverRoot));

        assertTrue(MissingWorldStorageLog.hasWarned("world_iris_orphan1"));
    }

    @Test
    public void startupContinuesForHealthyWorlds() throws Exception {
        Path serverRoot = temporaryFolder.newFolder("healthy-server").toPath();
        Files.createDirectories(serverRoot.resolve("world/dimensions/iris/moon/iris/pack"));
        writeServerProperties(serverRoot);
        writeBukkitWorlds(serverRoot, "world_iris_moon");

        IrisBootstrap.requireUsableWorldStorage(startupPaths(serverRoot));

        assertFalse(MissingWorldStorageLog.hasWarned("world_iris_moon"));
    }

    /**
     * A vanilla slot that holds an Iris world is generated from its history like any Iris world, so a broken
     * history stops startup before the level exists instead of reaching the level-load refusal.
     */
    @Test
    public void startupIsRefusedWhenABoundVanillaSlotLostItsGenerationHistory() throws Exception {
        Path serverRoot = temporaryFolder.newFolder("broken-overworld-server").toPath();
        Files.createDirectories(serverRoot.resolve("world/dimensions/minecraft/overworld/iris/engine-data"));
        Files.createDirectories(serverRoot.resolve("world/dimensions/minecraft/overworld/region"));
        Files.writeString(serverRoot.resolve("world/dimensions/minecraft/overworld/region/r.0.0.mca"), "terrain");
        writeServerProperties(serverRoot);
        writeBukkitWorlds(serverRoot, "world");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> IrisBootstrap.requireUsableWorldStorage(startupPaths(serverRoot)));

        assertTrue(failure.getMessage(),
                failure.getMessage().contains(serverRoot.toRealPath().resolve("world/dimensions/minecraft/overworld")
                        .toString()));
        assertTrue(failure.getMessage(), failure.getMessage().contains("generation history"));
        assertFalse(failure.getMessage(), failure.getMessage().contains("Remove worlds."));
    }

    @Test
    public void unusableStorageNamesWhyTheHistoryCannotBeRead() throws Exception {
        Path serverRoot = temporaryFolder.newFolder("cut-history-server").toPath();
        Path generation = serverRoot.resolve("world/dimensions/minecraft/overworld/iris/generation");
        Files.createDirectories(generation);
        Files.writeString(generation.resolve("manifest.json"), "{\"schema\":1,\"epochs\":[{\"id", StandardCharsets.UTF_8);
        writeServerProperties(serverRoot);
        writeBukkitWorlds(serverRoot, "world");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> IrisBootstrap.requireUsableWorldStorage(startupPaths(serverRoot)));

        assertTrue(failure.getMessage(), failure.getMessage().contains("Invalid generation manifest"));
        assertFalse(failure.getMessage(), failure.getMessage().contains("\n"));
    }

    /**
     * Without its bukkit.yml binding the server builds an Iris world in a vanilla slot with the vanilla generator,
     * which writes vanilla terrain into it.
     */
    @Test
    public void startupIsRefusedWhenAnIrisWorldInAVanillaSlotLostItsBinding() throws Exception {
        Path serverRoot = temporaryFolder.newFolder("unbound-overworld-server").toPath();
        Files.createDirectories(serverRoot.resolve("world/dimensions/minecraft/overworld/iris/pack"));
        writeServerProperties(serverRoot);
        writeBukkitWorlds(serverRoot);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> IrisBootstrap.requireUsableWorldStorage(startupPaths(serverRoot)));

        assertTrue(failure.getMessage(), failure.getMessage().contains("worlds.world.generator"));
        assertTrue(failure.getMessage(),
                failure.getMessage().contains(serverRoot.toRealPath().resolve("world/dimensions/minecraft/overworld")
                        .toString()));
    }

    @Test
    public void startupContinuesForVanillaSlotsWithoutIrisContent() throws Exception {
        Path serverRoot = temporaryFolder.newFolder("vanilla-server").toPath();
        Files.createDirectories(serverRoot.resolve("world/dimensions/minecraft/overworld/region"));
        Files.writeString(serverRoot.resolve("world/dimensions/minecraft/overworld/region/r.0.0.mca"), "terrain");
        Files.createDirectories(serverRoot.resolve("world/dimensions/minecraft/the_nether/iris"));
        Files.writeString(serverRoot.resolve("world/dimensions/minecraft/the_nether/iris/.DS_Store"), "finder");
        writeServerProperties(serverRoot);
        writeBukkitWorlds(serverRoot);

        IrisBootstrap.requireUsableWorldStorage(startupPaths(serverRoot));
    }

    private static BukkitStartupPaths startupPaths(Path serverRoot) throws Exception {
        return BukkitStartupPaths.resolve(serverRoot, new String[0]);
    }

    private static void writeServerProperties(Path serverRoot) throws Exception {
        Files.writeString(serverRoot.resolve("server.properties"), "level-name=world\n", StandardCharsets.UTF_8);
    }

    private static void writeBukkitWorlds(Path serverRoot, String... configuredWorldNames) throws Exception {
        File configuration = serverRoot.resolve("bukkit.yml").toFile();
        YamlConfiguration yaml = new YamlConfiguration();
        for (String configuredWorldName : configuredWorldNames) {
            yaml.set("worlds." + configuredWorldName + ".generator", "Iris:overworld");
        }
        yaml.save(configuration);
    }
}
