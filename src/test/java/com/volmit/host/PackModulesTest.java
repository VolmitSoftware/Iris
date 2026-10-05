package com.volmit.host;

import com.volmit.example.placement.PlacementModules;
import com.volmit.support.TestModuleJar;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static com.volmit.support.TestModuleJar.module;
import static org.junit.jupiter.api.Assertions.*;

class PackModulesTest {
    private static final String ALPHA_MEMBERS =
            "public static final PlacementStage<Void> MARK = Stage.placement(\"alpha:core/mark\");";
    private static final String NO_MEMBERS = "";
    private static final String NO_STAGES = "";

    @TempDir
    Path pack;

    private static String origin(String jarName) {
        return Path.of(PackModules.MODULES_DIRECTORY, jarName).toString();
    }

    private static LoadedModules loadWithBuiltIns(PackModules packModules) {
        var modules = new ArrayList<>(PlacementModules.builtIns());
        modules.addAll(packModules.modules());
        return LoadedModules.load(modules, Map.of());
    }

    private static String failure(Executable load) {
        return assertThrows(IllegalArgumentException.class, load).getMessage();
    }

    private static void assertMentions(String message, String... parts) {
        for (var part : parts) assertTrue(message.contains(part), message);
    }

    @Test
    void packWithoutModulesDirectoryHasNoModules() {
        try (var modules = PackModules.load(pack)) {
            assertTrue(modules.modules().isEmpty());
        }
    }

    @Test
    void loadsOneModulePerJarInFileNameOrderAndResolvesThemWithBuiltIns(@TempDir Path build) throws Exception {
        var alpha = module("pack.alpha.AlphaModule", "alpha:core", ALPHA_MEMBERS,
                "graph.define(MARK).requires(com.volmit.example.placement.AnchorModule.HEIGHT).run(context -> {});");
        var alphaJar = alpha.writeInto(build, "alpha.jar");
        module("pack.beta.BetaModule", "beta:core", NO_MEMBERS,
                "graph.define(Stage.placement(\"beta:core/use\")).requires(pack.alpha.AlphaModule.MARK).run(context -> {});")
                .compileAgainst(alphaJar)
                .writeInto(pack, "beta.jar");
        alpha.writeInto(pack, "alpha.jar");
        Files.writeString(pack.resolve(PackModules.MODULES_DIRECTORY).resolve("notes.txt"), "not a module");

        try (var packModules = PackModules.load(pack)) {
            assertEquals(List.of(origin("alpha.jar"), origin("beta.jar")),
                    packModules.modules().stream().map(DiscoveredModule::origin).toList());
            assertEquals(List.of("alpha:core", "beta:core"),
                    packModules.modules().stream().map(found -> found.module().info().id().toString()).toList());
            assertNotSame(PackModules.class.getClassLoader(), packModules.modules().getFirst().module().getClass().getClassLoader());

            var order = loadWithBuiltIns(packModules).placement().stages().stream()
                    .map(stage -> stage.stage().id().toString()).toList();
            assertTrue(order.indexOf("iris:anchor/height") < order.indexOf("alpha:core/mark"), order.toString());
            assertTrue(order.indexOf("alpha:core/mark") < order.indexOf("beta:core/use"), order.toString());
        }
    }

    @Test
    void closingThePackStopsItsClassLoaderFromLoadingMoreClasses() throws Exception {
        module("pack.gamma.GammaModule", "gamma:core", NO_MEMBERS, NO_STAGES)
                .source("pack.gamma.Unused", "package pack.gamma; final class Unused {}")
                .writeInto(pack, "gamma.jar");

        ClassLoader loader;
        try (var packModules = PackModules.load(pack)) {
            loader = packModules.modules().getFirst().module().getClass().getClassLoader();
            assertNotNull(loader.loadClass("pack.gamma.GammaModule"));
        }
        assertThrows(ClassNotFoundException.class, () -> loader.loadClass("pack.gamma.Unused"));
    }

    @Test
    void jarWithoutEntryPointFails() {
        new TestModuleJar().source("pack.Lonely", "package pack; public final class Lonely {}").writeInto(pack, "lonely.jar");

        assertMentions(failure(() -> PackModules.load(pack)),
                "Module jar " + origin("lonely.jar") + " has no entry point", ModuleJar.ENTRY_POINT_FILE);
    }

    @Test
    void jarNamingSeveralEntryPointsFails() {
        module("pack.First", "first:core", NO_MEMBERS, NO_STAGES)
                .source("pack.Second", "package pack; public final class Second {}")
                .entryPoint("pack.Second")
                .writeInto(pack, "two.jar");

        assertMentions(failure(() -> PackModules.load(pack)),
                "Module jar " + origin("two.jar") + " names 2 entry points [pack.First, pack.Second]");
    }

    @Test
    void brokenEntryClassesFailNamingTheJar() {
        var missing = pack.resolve("missing");
        new TestModuleJar().entryPoint("pack.Missing").writeInto(missing, "missing.jar");
        assertMentions(failure(() -> PackModules.load(missing)),
                "Module jar " + origin("missing.jar") + " has a broken entry point pack.Missing: class cannot be loaded");

        var unrelated = pack.resolve("unrelated");
        new TestModuleJar().source("pack.Plain", "package pack; public final class Plain {}")
                .entryPoint("pack.Plain").writeInto(unrelated, "plain.jar");
        assertMentions(failure(() -> PackModules.load(unrelated)),
                "Module jar " + origin("plain.jar") + " has a broken entry point pack.Plain: class does not implement com.volmit.IrisModule");

        var throwing = pack.resolve("throwing");
        module("pack.Throwing", "throwing:core", "public Throwing() { throw new IllegalStateException(\"boom\"); }", NO_STAGES)
                .writeInto(throwing, "throwing.jar");
        assertMentions(failure(() -> PackModules.load(throwing)),
                "Module jar " + origin("throwing.jar") + " has a broken entry point pack.Throwing: constructor threw",
                "boom");

        var shadowed = pack.resolve("shadowed");
        new TestModuleJar().entryPoint("com.volmit.example.placement.MossModule").writeInto(shadowed, "moss.jar");
        assertMentions(failure(() -> PackModules.load(shadowed)),
                "Module jar " + origin("moss.jar") + " has a broken entry point com.volmit.example.placement.MossModule",
                "not from this jar");
    }

    @Test
    void duplicateModuleIdsAcrossJarsFailNamingBothJars() {
        module("pack.one.Duplicate", "dup:core", NO_MEMBERS, NO_STAGES).writeInto(pack, "one.jar");
        module("pack.two.Duplicate", "dup:core", NO_MEMBERS, NO_STAGES).writeInto(pack, "two.jar");

        try (var packModules = PackModules.load(pack)) {
            assertMentions(failure(() -> LoadedModules.load(packModules.modules(), Map.of())),
                    "Duplicate module dup:core from " + origin("two.jar") + "; already loaded from " + origin("one.jar"));
        }
    }

    @Test
    void duplicateOfABuiltInModuleFailsNamingTheJar() {
        module("pack.Moss", "example:moss", NO_MEMBERS, NO_STAGES).writeInto(pack, "moss.jar");

        try (var packModules = PackModules.load(pack)) {
            assertMentions(failure(() -> loadWithBuiltIns(packModules)),
                    "Duplicate module example:moss from " + origin("moss.jar") + "; already loaded from host classpath");
        }
    }

    @Test
    void foreignStageNamespaceFailsNamingTheJar() {
        module("pack.Foreign", "mine:core", NO_MEMBERS,
                "graph.define(Stage.placement(\"theirs:core/work\")).run(context -> {});")
                .writeInto(pack, "foreign.jar");

        try (var packModules = PackModules.load(pack)) {
            assertMentions(failure(() -> LoadedModules.load(packModules.modules(), Map.of())),
                    "must use namespace 'mine' of its defining module mine:core from " + origin("foreign.jar"));
        }
    }
}
