package com.volmit.host;

import com.volmit.FeatureAttachment.Target;
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
            .exposing(CarveModule.class.getPackageName())
            .reservingNamespaces("iris");

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
        writeModule("iris:stolen", NO_STAGES);

        var error = assertThrows(IllegalArgumentException.class, () -> LoadedPack.load(pack, HOST));

        assertTrue(error.getMessage().contains("uses reserved namespace 'iris'"), error.getMessage());
        assertNotNull(packLoader(), "the module was constructed before installation failed");
        assertThrows(ClassNotFoundException.class, () -> packLoader().loadClass(UNUSED_CLASS));
    }

    @Test
    void invalidHostConfigurationFailsWhenBuilt() {
        var host = ModuleHost.of(List.of());
        assertThrows(IllegalArgumentException.class, () -> host.exposing("org.*"));
        assertThrows(IllegalArgumentException.class, () -> host.reservingNamespaces("iris:core"));
        assertThrows(IllegalArgumentException.class, () -> host.reservingFields(Map.of(Target.BIOME, Set.of(" "))));
        assertEquals(List.of(), host.builtIns());
        assertEquals(Set.of(), host.exposedHostPackages());
    }

    @Test
    void everyHostSettingAddsToWhatEarlierCallsConfigured() {
        var host = ModuleHost.of(List.of())
                .exposing("example.first")
                .exposing("example.second")
                .reservingNamespaces("iris")
                .reservingNamespaces("vanilla")
                .reservingFields(Map.of(Target.BIOME, Set.of("carving"), Target.REGION, Set.of("spawns")))
                .reservingFields(Map.of(Target.BIOME, Set.of("objects")));

        assertEquals(Set.of("example.first", "example.second"), host.exposedHostPackages());
        assertEquals(Set.of("iris", "vanilla"), host.reservedNamespaces());
        assertEquals(Map.of(Target.BIOME, Set.of("carving", "objects"), Target.REGION, Set.of("spawns")), host.reservedHostFields());
    }
}
