package com.volmit.host;

import com.volmit.IrisModule;
import com.volmit.example.placement.AnchorModule;
import com.volmit.example.placement.PlacementModules;
import com.volmit.support.TestModuleJar;
import example.builtin.CarveModule;
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
    private static final String SHARED_CLASS = "pack.shared.Util";
    private static final String SHARED_SOURCE = "package pack.shared; public final class Util {}";
    private static final byte[] UNUSED_CLASS_BYTES = {(byte) 0xCA, (byte) 0xFE};
    private static final String VERSIONED_SHARED_CLASS = "META-INF/versions/9/pack/shared/Util.class";
    private static final String DEFINED_PROPERTY = "irisapi.test.packModuleConstructed";
    private static final String OPTIONAL_CLASS = "org.optional.Feature";
    private static final String BUILT_IN_PACKAGE = CarveModule.class.getPackageName();
    private static final String REQUIRES_BUILT_IN_STAGE =
            "graph.define(Stage.placement(\"user:core/carved\")).requires(example.builtin.CarveModule.CLEAR).run(context -> {});";

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

    /** Versioned entries of a multi-release jar are read like any class, so they must be real class files. */
    private byte[] sharedClassFile() {
        return TestModuleJar.compileClass(pack, SHARED_CLASS, SHARED_SOURCE);
    }

    /** A jar of empty public classes, for modules to compile against without shipping it. */
    private static Path library(Path build, String jarName, String... classNames) {
        var library = new TestModuleJar();
        for (var className : classNames) {
            int dot = className.lastIndexOf('.');
            library.source(className, "package %s; public final class %s {}".formatted(className.substring(0, dot), className.substring(dot + 1)));
        }
        return library.writeInto(build, jarName);
    }

    /** Module members that use each class, so its class file references them. */
    private static String using(String... classNames) {
        return "static Object[] used() { return new Object[] {" + String.join(".class, ", classNames) + ".class}; }";
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
                "graph.define(MARK).requires(com.volmit.example.placement.AnchorModule.HEIGHT).run(context -> {});")
                .compileAgainstHostOf(AnchorModule.class);
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
            assertTrue(packModules.missingClasses().isEmpty());

            var order = loadWithBuiltIns(packModules).placement().stages().stream()
                    .map(stage -> stage.stage().id().toString()).toList();
            assertTrue(order.indexOf("iris:anchor/height") < order.indexOf("alpha:core/mark"), order.toString());
            assertTrue(order.indexOf("alpha:core/mark") < order.indexOf("beta:core/use"), order.toString());
        }
    }

    @Test
    void packClassLoaderSeesOnlyTheApiTheJdkAndThePack() throws Exception {
        module("pack.gamma.GammaModule", "gamma:core", NO_MEMBERS, NO_STAGES).writeInto(pack, "gamma.jar");

        try (var packModules = PackModules.load(pack)) {
            var loader = packModules.modules().getFirst().module().getClass().getClassLoader();
            assertSame(IrisModule.class, loader.loadClass(IrisModule.class.getName()));
            assertSame(List.class, loader.loadClass(List.class.getName()));
            assertThrows(ClassNotFoundException.class, () -> loader.loadClass(Test.class.getName()));
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
    void sameClassInTwoJarsFailsBeforeAnyModuleIsConstructed() {
        var constructorMarker = "public %s() { System.setProperty(\"" + DEFINED_PROPERTY + "\", \"true\"); }";
        module("pack.alpha.Alpha", "alpha:core", constructorMarker.formatted("Alpha"), NO_STAGES)
                .source(SHARED_CLASS, SHARED_SOURCE).writeInto(pack, "alpha.jar");
        module("pack.beta.Beta", "beta:core", constructorMarker.formatted("Beta"), NO_STAGES)
                .source(SHARED_CLASS, SHARED_SOURCE).writeInto(pack, "beta.jar");

        try {
            assertMentions(failure(() -> PackModules.load(pack)),
                    "Class " + SHARED_CLASS + " is in both module jars " + origin("alpha.jar") + " and " + origin("beta.jar"));
            assertNull(System.getProperty(DEFINED_PROPERTY), "a module was constructed before the duplicate was found");
        } finally {
            System.clearProperty(DEFINED_PROPERTY);
        }
    }

    @Test
    void moduleDescriptorsMayRepeatAcrossJars() {
        module("pack.alpha.Alpha", "alpha:core", NO_MEMBERS, NO_STAGES)
                .resource("module-info.class", UNUSED_CLASS_BYTES).writeInto(pack, "alpha.jar");
        module("pack.beta.Beta", "beta:core", NO_MEMBERS, NO_STAGES)
                .resource("module-info.class", UNUSED_CLASS_BYTES).writeInto(pack, "beta.jar");

        try (var packModules = PackModules.load(pack)) {
            assertEquals(2, packModules.modules().size());
        }
    }

    @Test
    void versionedClassOfAMultiReleaseJarCountsUnderItsBaseName() {
        module("pack.alpha.Alpha", "alpha:core", NO_MEMBERS, NO_STAGES)
                .source(SHARED_CLASS, SHARED_SOURCE).writeInto(pack, "alpha.jar");
        module("pack.beta.Beta", "beta:core", NO_MEMBERS, NO_STAGES)
                .multiRelease()
                .resource(VERSIONED_SHARED_CLASS, sharedClassFile())
                .writeInto(pack, "beta.jar");

        assertMentions(failure(() -> PackModules.load(pack)),
                "Class " + SHARED_CLASS + " is in both module jars " + origin("alpha.jar") + " and " + origin("beta.jar"));
    }

    @Test
    void versionedEntriesOutsideAMultiReleaseJarAreNotClasses() {
        module("pack.alpha.Alpha", "alpha:core", NO_MEMBERS, NO_STAGES)
                .source(SHARED_CLASS, SHARED_SOURCE)
                .resource(VERSIONED_SHARED_CLASS, UNUSED_CLASS_BYTES)
                .writeInto(pack, "alpha.jar");
        module("pack.beta.Beta", "beta:core", NO_MEMBERS, NO_STAGES)
                .resource(VERSIONED_SHARED_CLASS, UNUSED_CLASS_BYTES)
                .writeInto(pack, "beta.jar");

        try (var packModules = PackModules.load(pack)) {
            assertEquals(2, packModules.modules().size());
        }
    }

    @Test
    void multiReleaseJarMayHoldAVersionedCopyOfItsOwnClass() {
        module("pack.alpha.Alpha", "alpha:core", NO_MEMBERS, NO_STAGES)
                .source(SHARED_CLASS, SHARED_SOURCE)
                .multiRelease()
                .resource(VERSIONED_SHARED_CLASS, sharedClassFile())
                .writeInto(pack, "alpha.jar");

        try (var packModules = PackModules.load(pack)) {
            assertEquals(1, packModules.modules().size());
        }
    }

    @Test
    void apiClassInAJarFailsNamingTheJarAndTheClass() {
        module("pack.alpha.Alpha", "alpha:core", NO_MEMBERS, NO_STAGES)
                .source("com.volmit.extra.Helper", "package com.volmit.extra; public final class Helper {}")
                .source("com.volmit.extra.Other", "package com.volmit.extra; public final class Other {}")
                .writeInto(pack, "alpha.jar");

        assertEquals("Module jar " + origin("alpha.jar") + " contains Iris API class com.volmit.extra.Helper and 1 more;"
                        + " Iris provides the API at runtime, so mark the Iris API dependency compileOnly (Maven: provided)"
                        + " and keep module classes out of com.volmit.*",
                failure(() -> PackModules.load(pack)));
    }

    @Test
    void referenceToAHostLibraryFailsNamingTheJarAndBothClasses() {
        module("pack.user.User", "user:core", "static Object probe() { return org.junit.jupiter.api.Test.class; }", NO_STAGES)
                .compileAgainstHostOf(Test.class)
                .writeInto(pack, "user.jar");

        assertEquals("Module jar " + origin("user.jar") + " uses classes that exist in Iris but are hidden from modules:\n"
                        + "  pack.user.User -> org.junit.jupiter.api.Test\n"
                        + "Modules cannot use Iris's own classes or its copies of libraries; ship the library in a module"
                        + " jar of the pack, or use the Iris API.",
                failure(() -> PackModules.load(pack)));
    }

    @Test
    void referenceToAJdkModuleOutsideThePlatformLoaderFailsNamingTheModule() {
        module("pack.user.User", "user:core", using("com.sun.source.tree.Tree", "java.sql.Connection"), NO_STAGES)
                .writeInto(pack, "user.jar");

        assertEquals("Module jar " + origin("user.jar") + " uses classes of JDK modules that are not available to modules:\n"
                        + "  pack.user.User -> com.sun.source.tree.Tree (JDK module jdk.compiler)\n"
                        + "Modules can use only the JDK modules of the platform class loader, such as java.base, java.sql and"
                        + " java.net.http.",
                failure(() -> PackModules.load(pack)));
    }

    @Test
    void oneFailureReportsEveryKindOfUnusableClass() {
        module("pack.user.User", "user:core", using("org.junit.jupiter.api.Test", "com.sun.source.tree.Tree"), NO_STAGES)
                .compileAgainstHostOf(Test.class)
                .writeInto(pack, "user.jar");

        assertEquals("Module jar " + origin("user.jar") + " uses classes that exist in Iris but are hidden from modules:\n"
                        + "  pack.user.User -> org.junit.jupiter.api.Test\n"
                        + "Modules cannot use Iris's own classes or its copies of libraries; ship the library in a module"
                        + " jar of the pack, or use the Iris API.\n"
                        + "Module jar " + origin("user.jar") + " uses classes of JDK modules that are not available to modules:\n"
                        + "  pack.user.User -> com.sun.source.tree.Tree (JDK module jdk.compiler)\n"
                        + "Modules can use only the JDK modules of the platform class loader, such as java.base, java.sql and"
                        + " java.net.http.",
                failure(() -> PackModules.load(pack)));
    }

    @Test
    void classesNobodyHasAreReportedWithoutFailingTheLoad(@TempDir Path build) {
        var optionalLibrary = new TestModuleJar()
                .source(OPTIONAL_CLASS, "package org.optional; public final class Feature { public static void use() {} }")
                .writeInto(build, "optional.jar");
        module("pack.user.User", "user:core", "static void useWhenPresent() { org.optional.Feature.use(); }",
                "graph.define(Stage.placement(\"user:core/work\")).run(context -> {});")
                .compileAgainst(optionalLibrary)
                .writeInto(pack, "user.jar");

        try (var packModules = PackModules.load(pack)) {
            assertEquals(List.of(new MissingClasses(origin("user.jar"), List.of(OPTIONAL_CLASS))), packModules.missingClasses());
            assertDoesNotThrow(() -> LoadedModules.load(packModules.modules(), Map.of()));
        }
    }

    @Test
    void hiddenHostClassFailsEvenWhenOtherMissingClassesAreOptional(@TempDir Path build) {
        var optionalLibrary = new TestModuleJar()
                .source(OPTIONAL_CLASS, "package org.optional; public final class Feature {}")
                .writeInto(build, "optional.jar");
        module("pack.user.User", "user:core", """
                static Object optional() { return org.optional.Feature.class; }
                static Object hidden() { return org.junit.jupiter.api.Test.class; }
                """, NO_STAGES)
                .compileAgainst(optionalLibrary)
                .compileAgainstHostOf(Test.class)
                .writeInto(pack, "user.jar");

        var message = failure(() -> PackModules.load(pack));
        assertMentions(message, "pack.user.User -> org.junit.jupiter.api.Test");
        assertFalse(message.contains(OPTIONAL_CLASS), message);
    }

    @Test
    void missingClassesInDeclaredOptionalPackagesDoNotWarn(@TempDir Path build) {
        module("pack.user.User", "user:core", List.of("org.optional", "org.other."), using(OPTIONAL_CLASS, "org.other.deep.Thing"), NO_STAGES)
                .compileAgainst(library(build, "optional.jar", OPTIONAL_CLASS, "org.other.deep.Thing"))
                .writeInto(pack, "user.jar");

        try (var packModules = PackModules.load(pack)) {
            assertEquals(List.of(), packModules.missingClasses());
        }
    }

    @Test
    void missingClassesOutsideDeclaredOptionalPackagesStillWarn(@TempDir Path build) {
        module("pack.user.User", "user:core", List.of("org.optional"), using(OPTIONAL_CLASS, "org.optionalx.Other", "org.slf4j.Logger"), NO_STAGES)
                .compileAgainst(library(build, "optional.jar", OPTIONAL_CLASS, "org.optionalx.Other", "org.slf4j.Logger"))
                .writeInto(pack, "user.jar");

        try (var packModules = PackModules.load(pack)) {
            assertEquals(List.of(new MissingClasses(origin("user.jar"), List.of("org.optionalx.Other", "org.slf4j.Logger"))),
                    packModules.missingClasses());
        }
    }

    @Test
    void optionalPackagesExcuseOnlyTheirOwnJar(@TempDir Path build) {
        var optionalLibrary = library(build, "optional.jar", OPTIONAL_CLASS);
        module("pack.alpha.Alpha", "alpha:core", List.of("org.optional"), using(OPTIONAL_CLASS), NO_STAGES)
                .compileAgainst(optionalLibrary)
                .writeInto(pack, "alpha.jar");
        module("pack.beta.Beta", "beta:core", using(OPTIONAL_CLASS), NO_STAGES)
                .compileAgainst(optionalLibrary)
                .writeInto(pack, "beta.jar");

        try (var packModules = PackModules.load(pack)) {
            assertEquals(List.of(new MissingClasses(origin("beta.jar"), List.of(OPTIONAL_CLASS))), packModules.missingClasses());
        }
    }

    @Test
    void optionalPackagesNeverExcuseALoadError(@TempDir Path build) {
        var hidden = pack.resolve("hidden");
        module("pack.user.User", "user:core", List.of("org.junit"), using("org.junit.jupiter.api.Test"), NO_STAGES)
                .compileAgainstHostOf(Test.class)
                .writeInto(hidden, "user.jar");
        assertMentions(failure(() -> PackModules.load(hidden)),
                "uses classes that exist in Iris but are hidden from modules:\n  pack.user.User -> org.junit.jupiter.api.Test");

        var newerApi = pack.resolve("newer-api");
        module("pack.user.User", "user:core", List.of("com.volmit"), using("com.volmit.Newer"), NO_STAGES)
                .compileAgainst(library(build, "newer-api.jar", "com.volmit.Newer"))
                .writeInto(newerApi, "user.jar");
        assertMentions(failure(() -> PackModules.load(newerApi)),
                "uses classes that this Iris does not have, in packages Iris provides to modules:\n  pack.user.User -> com.volmit.Newer");
    }

    @Test
    void invalidOptionalPackageFailsNamingTheJar() {
        module("pack.user.User", "user:core", List.of("org.*"), NO_MEMBERS, NO_STAGES).writeInto(pack, "user.jar");

        assertMentions(failure(() -> PackModules.load(pack)),
                "Module jar " + origin("user.jar") + " has a broken entry point pack.user.User: info() failed",
                "Optional package 'org.*' of module user:core is not a package name");
    }

    @Test
    void referenceToAMissingApiClassPointsAtADifferentApiBuild(@TempDir Path build) {
        var newerApi = new TestModuleJar()
                .source("com.volmit.Newer", "package com.volmit; public final class Newer {}")
                .writeInto(build, "newer-api.jar");
        module("pack.user.User", "user:core", "static Object probe() { return com.volmit.Newer.class; }", NO_STAGES)
                .compileAgainst(newerApi)
                .writeInto(pack, "user.jar");

        assertMentions(failure(() -> PackModules.load(pack)),
                "uses classes that this Iris does not have, in packages Iris provides to modules:\n  pack.user.User -> com.volmit.Newer",
                "The jar was built against a different Iris API or Iris version.");
    }

    @Test
    void referencesToTheJdkTheApiAndOtherPackJarsLoad(@TempDir Path build) throws Exception {
        var marker = new TestModuleJar()
                .source("pack.marker.Marker", """
                        package pack.marker;
                        @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
                        public @interface Marker {}
                        """)
                .writeInto(build, "marker.jar");
        var alpha = module("pack.alpha.Alpha", "alpha:core", NO_MEMBERS, NO_STAGES)
                .source("pack.alpha.Shared", """
                        package pack.alpha;
                        public record Shared(int[] values, String[][] names) {
                            public enum Kind { SMALL, LARGE }
                        }
                        """);
        var alphaJar = alpha.writeInto(build, "alpha.jar");
        alpha.writeInto(pack, "alpha.jar");
        module("pack.beta.Beta", "beta:core", """
                @pack.marker.Marker
                static final pack.alpha.Shared[] SHARED = { new pack.alpha.Shared(new int[] {1}, new String[][] {{"a"}}) };

                static int size(pack.alpha.Shared.Kind kind) {
                    return switch (kind) { case SMALL -> 1; case LARGE -> 2; };
                }
                """, """
                java.util.function.IntSupplier size = () -> size(pack.alpha.Shared.Kind.LARGE) + SHARED[0].values().length;
                graph.define(Stage.placement("beta:core/size")).run(context -> java.util.List.of(size).forEach(java.util.function.IntSupplier::getAsInt));
                """)
                .compileAgainst(alphaJar)
                .compileAgainst(marker)
                .writeInto(pack, "beta.jar");

        try (var packModules = PackModules.load(pack)) {
            var loaded = LoadedModules.load(packModules.modules(), Map.of());
            assertTrue(loaded.placement().stages().stream().anyMatch(stage -> stage.stage().id().toString().equals("beta:core/size")));
            var beta = packModules.modules().getLast().module().getClass();
            assertEquals(0, beta.getDeclaredField("SHARED").getAnnotations().length,
                    "the annotation type is not in the pack, so the JVM skips it");
        }
    }

    @Test
    void packJarRequiresAStageOfABuiltInModuleInAnExposedPackage() {
        module("pack.user.User", "user:core", NO_MEMBERS, REQUIRES_BUILT_IN_STAGE)
                .compileAgainstHostOf(CarveModule.class)
                .writeInto(pack, "user.jar");

        try (var packModules = PackModules.load(pack, List.of(BUILT_IN_PACKAGE))) {
            var modules = new ArrayList<>(List.of(DiscoveredModule.builtIn(new CarveModule())));
            modules.addAll(packModules.modules());
            var order = LoadedModules.load(modules, Map.of()).placement().stages().stream()
                    .map(stage -> stage.stage().id().toString()).toList();
            assertEquals(List.of("iris:carve/clear", "user:core/carved"), order);
        }
    }

    @Test
    void builtInModuleOutsideTheExposedPackagesIsHidden() {
        module("pack.user.User", "user:core", NO_MEMBERS, REQUIRES_BUILT_IN_STAGE)
                .compileAgainstHostOf(CarveModule.class)
                .writeInto(pack, "user.jar");

        assertMentions(failure(() -> PackModules.load(pack)),
                "Module jar " + origin("user.jar") + " uses classes that exist in Iris but are hidden from modules:\n"
                        + "  pack.user.User -> example.builtin.CarveModule\n");
    }

    @Test
    void referenceToAMissingClassOfAnExposedPackagePointsAtADifferentBuild(@TempDir Path build) {
        var newerBuiltIns = new TestModuleJar()
                .source("example.builtin.Newer", "package example.builtin; public final class Newer {}")
                .writeInto(build, "newer-built-ins.jar");
        module("pack.user.User", "user:core", "static Object probe() { return example.builtin.Newer.class; }", NO_STAGES)
                .compileAgainst(newerBuiltIns)
                .writeInto(pack, "user.jar");

        assertMentions(failure(() -> PackModules.load(pack, List.of(BUILT_IN_PACKAGE))),
                "pack.user.User -> example.builtin.Newer", "built against a different Iris API or Iris version");
    }

    @Test
    void classOfAnExposedHostPackageInAJarFails() {
        module("pack.alpha.Alpha", "alpha:core", NO_MEMBERS, NO_STAGES)
                .source("example.builtin.Copy", "package example.builtin; public final class Copy {}")
                .writeInto(pack, "alpha.jar");

        assertEquals("Module jar " + origin("alpha.jar") + " contains class example.builtin.Copy of package example.builtin,"
                        + " which Iris provides to modules at runtime; compile against it compileOnly (Maven: provided)"
                        + " instead of shipping it, and keep module classes out of example.builtin.*",
                failure(() -> PackModules.load(pack, List.of(BUILT_IN_PACKAGE))));
    }

    @Test
    void invalidExposedPackageFailsBeforeReadingJars() {
        assertMentions(failure(() -> PackModules.load(pack, List.of("example.*"))),
                "Exposed package 'example.*' is not a package name");
    }

    @Test
    void methodMissingAtInstallFailsNamingTheJar(@TempDir Path build) {
        var libraryWithMethod = new TestModuleJar()
                .source("pack.lib.Lib", "package pack.lib; public final class Lib { public static void added() {} }")
                .writeInto(build, "lib.jar");
        module("pack.alpha.Alpha", "alpha:core", NO_MEMBERS, NO_STAGES)
                .source("pack.lib.Lib", "package pack.lib; public final class Lib {}")
                .writeInto(pack, "alpha.jar");
        module("pack.beta.Beta", "beta:core", NO_MEMBERS, "pack.lib.Lib.added();")
                .compileAgainst(libraryWithMethod)
                .writeInto(pack, "beta.jar");

        try (var packModules = PackModules.load(pack)) {
            assertMentions(failure(() -> LoadedModules.load(packModules.modules(), Map.of())),
                    "Module beta:core from " + origin("beta.jar") + " failed to link or initialize a class while installing",
                    "java.lang.NoSuchMethodError", "pack.lib.Lib.added()");
        }
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
