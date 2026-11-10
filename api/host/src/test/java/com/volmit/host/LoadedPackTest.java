package com.volmit.host;

import com.volmit.config.Level;
import com.volmit.support.TestModuleJar;
import example.builtin.CarveModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static com.volmit.support.TestModuleJar.module;
import static org.junit.jupiter.api.Assertions.*;

class LoadedPackTest {
    private static final String LOADER_PROPERTY = "irisapi.test.packClassLoader";
    private static final String UNUSED_CLASS = "pack.user.Unused";
    private static final String NO_MEMBERS = "";
    private static final String NO_STAGES = "";
    private static final ModuleHost HOST = ModuleHost.of(List.of(new CarveModule()))
            .exposing(CarveModule.class.getPackageName());

    @TempDir
    Path pack;

    @AfterEach
    void forgetLoader() {
        System.getProperties().remove(LOADER_PROPERTY);
    }

    /** A pack module that hands its class loader to the test when constructed, plus a class nothing loads. */
    private void writeModule(String moduleId, String install) {
        module("pack.user.User", moduleId,
                "public User() { System.getProperties().put(\"" + LOADER_PROPERTY + "\", getClass().getClassLoader()); }",
                install)
                .source(UNUSED_CLASS, "package pack.user; final class Unused {}")
                .compileAgainstHostOf(CarveModule.class)
                .writeInto(pack, "user.jar");
    }

    private static ClassLoader packLoader() {
        return (ClassLoader) System.getProperties().get(LOADER_PROPERTY);
    }

    @Test
    void loadsPackModulesWithTheHostsBuiltInsAndResolvesBothPipelines() {
        writeModule("user:core", "graph.define(Stage.placement(\"user:core/carved\"))"
                + ".requires(example.builtin.CarveModule.CLEAR).run(context -> {});");

        try (var loaded = LoadedPack.load(pack, HOST)) {
            assertEquals(List.of("user:core"), loaded.packModules().stream().map(found -> found.info().id().toString()).toList());
            assertEquals(List.of("iris:carve/clear", "user:core/carved"),
                    loaded.placement().stages().stream().map(stage -> stage.stage().id().toString()).toList());
            assertTrue(loaded.chunk().stages().isEmpty());
            assertTrue(loaded.missingClasses().isEmpty());
            assertTrue(loaded.catalog().settings().isEmpty());
        }
    }

    @Test
    void closingThePackReleasesItsClassLoader() {
        writeModule("user:core", NO_STAGES);

        try (var ignored = LoadedPack.load(pack, HOST)) {
            assertNotNull(packLoader());
        }

        assertThrows(ClassNotFoundException.class, () -> packLoader().loadClass(UNUSED_CLASS));
    }

    @Test
    void failedLoadLeavesNothingOpen() {
        writeModule("iris:carve", NO_STAGES);

        var error = assertThrows(ModuleLoadException.class, () -> LoadedPack.load(pack, HOST));

        assertTrue(error.getMessage().contains("Duplicate module iris:carve"), error.getMessage());
        assertEquals("iris:carve", error.moduleId().orElseThrow().toString());
        assertTrue(error.origin().orElseThrow().startsWith("modules/"), error.origin().toString());
        assertNotNull(packLoader(), "the module was constructed before installation failed");
        assertThrows(ClassNotFoundException.class, () -> packLoader().loadClass(UNUSED_CLASS));
    }

    @Test
    void invalidHostConfigurationFailsWhenBuilt() {
        var host = ModuleHost.of(List.of());
        assertThrows(IllegalArgumentException.class, () -> host.exposing("org.*"));
        assertThrows(IllegalArgumentException.class, () -> host.reservingFields(Map.of(Level.BIOME, Set.of(" "))));
        assertThrows(IllegalArgumentException.class, () -> host.reservingFields(Map.of(Level.BIOME, Set.of("iris:carve"))));
        assertEquals(List.of(), host.builtIns());
        assertEquals(Set.of(), host.exposedHostPackages());
    }

    @Test
    void everyHostSettingAddsToWhatEarlierCallsConfigured() {
        var host = ModuleHost.of(List.of())
                .exposing("example.first")
                .exposing("example.second")
                .reservingFields(Map.of(Level.BIOME, Set.of("carving"), Level.REGION, Set.of("spawns")))
                .reservingFields(Map.of(Level.BIOME, Set.of("objects")))
                .reservingFolders("biomes")
                .reservingFolders("regions");

        assertEquals(Set.of("example.first", "example.second"), host.exposedHostPackages());
        assertEquals(Map.of(Level.BIOME, Set.of("carving", "objects"), Level.REGION, Set.of("spawns")), host.reservedHostFields());
        assertEquals(Set.of("biomes", "regions"), host.reservedFolders());
    }

    @Test
    void copiesJarsWhereTheHostSaysAndLogsToTheHostsLogger(@TempDir Path cache) throws java.io.IOException {
        writeModule("user:core", NO_STAGES);
        var copies = cache.resolve("module-jars");
        var log = new RecordingLogger();

        try (var ignored = LoadedPack.load(pack, HOST.copyingJarsTo(copies).loggingTo(log))) {
            assertTrue(java.nio.file.Files.exists(copies), "a missing copy directory is created");
            try (var entries = java.nio.file.Files.list(copies)) {
                assertEquals(1, entries.filter(entry -> entry.getFileName().toString().startsWith(JarSnapshot.DIRECTORY_PREFIX)).count());
            }
        }

        assertEquals(List.of("INFO Pack " + pack.getFileName() + " uses 1 module: all from the pack"), log.levelsAndFirstLines());
    }

    @Test
    void bundledJarsLoadWithEveryPackBeforeItsOwnJarsInItsClassLoader(@TempDir Path build) {
        var bundledJar = module("host.bundled.BundledModule", "bundled:core",
                "public static final PlacementStage<Void> MARK = Stage.placement(\"bundled:core/mark\");",
                "graph.define(MARK).run(context -> {});")
                .writeInto(build, "bundled.jar");
        module("pack.user.User", "user:core", NO_MEMBERS,
                "graph.define(Stage.placement(\"user:core/after\")).requires(host.bundled.BundledModule.MARK).run(context -> {});")
                .compileAgainst(bundledJar)
                .writeInto(pack, "user.jar");

        try (var loaded = LoadedPack.load(pack, HOST.bundlingJars(bundledJar))) {
            assertEquals(List.of("bundled.jar" + PackModules.BUNDLED_ORIGIN_SUFFIX, Path.of("modules", "user.jar").toString()),
                    loaded.packModules().stream().map(DiscoveredModule::origin).toList());
            var order = loaded.placement().stages().stream().map(stage -> stage.stage().id().toString()).toList();
            assertTrue(order.indexOf("bundled:core/mark") < order.indexOf("user:core/after"), order.toString());
        }
    }

    @Test
    void aPackCopyOfABundledJarFailsNamingBothJars(@TempDir Path build) {
        var bundled = module("host.bundled.BundledModule", "bundled:core", NO_MEMBERS, NO_STAGES);
        var bundledJar = bundled.writeInto(build, "bundled.jar");
        bundled.writeInto(pack, "bundled.jar");

        var error = assertThrows(ModuleLoadException.class, () -> LoadedPack.load(pack, HOST.bundlingJars(bundledJar)));

        assertTrue(error.getMessage().contains("Class host.bundled.BundledModule is in both module jars bundled.jar"
                + PackModules.BUNDLED_ORIGIN_SUFFIX + " and " + Path.of("modules", "bundled.jar")), error.getMessage());
    }

    /** Keeps the level and first line of every message, which is enough to see where the summary went. */
    private static final class RecordingLogger implements System.Logger {
        private final List<String> lines = new java.util.ArrayList<>();

        List<String> levelsAndFirstLines() { return lines; }
        public String getName() { return "recording"; }
        public boolean isLoggable(Level level) { return true; }
        public void log(Level level, java.util.ResourceBundle bundle, String message, Throwable thrown) { record(level, message); }
        public void log(Level level, java.util.ResourceBundle bundle, String format, Object... parameters) { record(level, format); }

        private void record(Level level, String message) {
            lines.add(level + " " + message.lines().findFirst().orElse(""));
        }
    }
}
