package com.volmit.host;

import com.volmit.support.FakePlatformApi;
import com.volmit.support.TestModuleJar;
import example.builtin.CarveModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import static com.volmit.support.TestModuleJar.module;
import static org.junit.jupiter.api.Assertions.*;

class ModuleJarCheckTest {
    private static final String NO_MEMBERS = "";
    private static final String NO_STAGES = "";
    private static final String USES_JUNIT = "static Object used() { return org.junit.jupiter.api.Test.class; }";
    private static final List<String> NO_EXPOSED_PACKAGES = List.of();

    @TempDir
    Path build;

    private static String failure(Executable check) {
        return assertThrows(ModuleLoadException.class, check).getMessage();
    }

    @Test
    void jarOfSeveralModulesPassesAndReturnsThemInListedOrder() {
        var jar = module("pack.First", "first:core", NO_MEMBERS, NO_STAGES)
                .alsoModule("pack.Second", "second:core", NO_MEMBERS, NO_STAGES)
                .writeInto(build, "two.jar");

        assertEquals(List.of("pack.First", "pack.Second"), ModuleJarCheck.check(jar, NO_EXPOSED_PACKAGES));
    }

    @Test
    void jarThatUsesAnExposedHostPackagePasses() {
        var jar = module("pack.User", "user:core", NO_MEMBERS,
                "graph.define(Stage.placement(\"user:core/carved\")).requires(example.builtin.CarveModule.CLEAR).run(context -> {});")
                .compileAgainstHostOf(CarveModule.class)
                .writeInto(build, "user.jar");

        assertEquals(List.of("pack.User"), ModuleJarCheck.check(jar, List.of(CarveModule.class.getPackageName())));
    }

    @Test
    void jarThatUsesAHiddenHostClassFailsNamingTheClass() {
        var jar = module("pack.User", "user:core", USES_JUNIT, NO_STAGES)
                .compileAgainstHostOf(Test.class)
                .writeInto(build, "user.jar");

        var message = failure(() -> ModuleJarCheck.check(jar, NO_EXPOSED_PACKAGES));

        assertTrue(message.contains("hidden from modules"), message);
        assertTrue(message.contains("pack.User -> org.junit.jupiter.api.Test"), message);
    }

    @Test
    void jarThatUsesAHostClassItsHostDoesNotExposeFails() {
        var jar = module("pack.User", "user:core", NO_MEMBERS,
                "graph.define(Stage.placement(\"user:core/carved\")).requires(example.builtin.CarveModule.CLEAR).run(context -> {});")
                .compileAgainstHostOf(CarveModule.class)
                .writeInto(build, "user.jar");

        var message = failure(() -> ModuleJarCheck.check(jar, NO_EXPOSED_PACKAGES));

        assertTrue(message.contains("pack.User -> example.builtin.CarveModule"), message);
    }

    @Test
    void moduleClassOutsideTheJarFailsNamingTheJarAndTheClass() {
        var jar = module("pack.User", "user:core", NO_MEMBERS, NO_STAGES)
                .entryPoint("pack.Elsewhere")
                .writeInto(build, "user.jar");

        var message = failure(() -> ModuleJarCheck.check(jar, NO_EXPOSED_PACKAGES));

        assertTrue(message.contains(jar.toString()), message);
        assertTrue(message.contains("lists pack.Elsewhere"), message);
    }

    @Test
    void jarThatListsNoModuleFails() {
        var jar = new TestModuleJar().source("pack.Plain", "package pack; public final class Plain {}").writeInto(build, "plain.jar");

        assertTrue(failure(() -> ModuleJarCheck.check(jar, NO_EXPOSED_PACKAGES)).contains("has no entry point"));
    }

    @Test
    void jarThatUsesAnExposedClassTheHostJarLeavesOutFailsNamingTheClass() {
        var jar = module("pack.User", "user:core", NO_MEMBERS,
                "graph.define(Stage.placement(\"user:core/carved\")).requires(example.builtin.CarveModule.CLEAR).run(context -> {});")
                .compileAgainstHostOf(CarveModule.class)
                .writeInto(build, "user.jar");
        var exposed = List.of(CarveModule.class.getPackageName());
        var leftOut = List.of("# stripped from the host jar", "example/builtin/CarveModule");

        var message = failure(() -> ModuleJarCheck.check(jar, exposed, leftOut));

        assertTrue(message.contains("leaves out"), message);
        assertTrue(message.contains("pack.User -> example.builtin.CarveModule"), message);
    }

    private static final String BUKKIT_PART = "pack.tools.bukkit.StudioPart";
    private static final String MODDED_PART = "pack.tools.modded.ModdedPart";

    @TempDir
    Path work;

    private Path platformJar(String... classNames) {
        try (var platform = FakePlatformApi.of(work, classNames)) {
            return platform.jar();
        }
    }

    private static String partWith(String className, String members) {
        int dot = className.lastIndexOf('.');
        return "package " + className.substring(0, dot) + "; public final class " + className.substring(dot + 1) + " { " + members + " }";
    }

    private static String usingClass(String className) {
        return "static final Class<?> USED = " + className + ".class;";
    }

    private static TestModuleJar moduleWithPart(String part, String partMembers, String platform) {
        return module("pack.User", "user:core", NO_MEMBERS, NO_STAGES)
                .source(part, partWith(part, partMembers))
                .toolingParts(platform + "=" + part);
    }

    private static ModuleJarCheck.CheckedPlatform bukkitWith(Path apiJar) {
        return new ModuleJarCheck.CheckedPlatform(ToolingPlatform.BUKKIT, apiJar);
    }

    @Test
    void partsAreSkippedWithoutAPlatformAndTheOutputSaysSo() {
        var jar = moduleWithPart(BUKKIT_PART, usingClass("org.junit.jupiter.api.Test"), "bukkit")
                .compileAgainstHostOf(Test.class)
                .writeInto(build, "user.jar");

        var outcome = ModuleJarCheck.run(jar, NO_EXPOSED_PACKAGES, List.of(), Optional.empty());

        assertEquals(List.of("pack.User"), outcome.modules());
        assertTrue(outcome.summary().contains("passes the pack module checks: [pack.User]"), outcome.summary());
        assertTrue(outcome.summary().contains("Skipped tooling parts (name a platform with --platform=<name>:<platform api jar> to check them): "
                + "[bukkit=" + BUKKIT_PART + "]"), outcome.summary());
    }

    @Test
    void jarWithoutPartsHasNoSkipNote() {
        var jar = module("pack.User", "user:core", NO_MEMBERS, NO_STAGES).writeInto(build, "user.jar");

        assertFalse(ModuleJarCheck.run(jar, NO_EXPOSED_PACKAGES, List.of(), Optional.empty()).summary().contains("Skipped"));
    }

    @Test
    void generationClassUsingAPartFailsWithoutAPlatformToo() {
        var jar = module("pack.User", "user:core", "static Object leak() { return new " + BUKKIT_PART + "(); }", NO_STAGES)
                .source(BUKKIT_PART, partWith(BUKKIT_PART, "public StudioPart() {}"))
                .toolingParts("bukkit=" + BUKKIT_PART)
                .writeInto(build, "user.jar");

        var message = failure(() -> ModuleJarCheck.check(jar, NO_EXPOSED_PACKAGES));

        assertTrue(message.contains("pack.User -> " + BUKKIT_PART), message);
        assertTrue(message.contains("Generation code cannot use the bukkit part; move the call into the part."), message);
    }

    @Test
    void partOfTheNamedPlatformPassesAgainstThePlatformJar() {
        var api = platformJar("org.bukkit.Bukkit");
        var jar = moduleWithPart(BUKKIT_PART, usingClass("org.bukkit.Bukkit"), "bukkit")
                .compileAgainst(api)
                .writeInto(build, "user.jar");

        var outcome = ModuleJarCheck.run(jar, NO_EXPOSED_PACKAGES, List.of(), Optional.of(bukkitWith(api)));

        assertEquals(List.of("pack.User"), outcome.modules());
        assertEquals(List.of(), outcome.skippedParts());
        assertFalse(outcome.summary().contains("Skipped"), outcome.summary());
        assertEquals(List.of("pack.User"), ModuleJarCheck.check(jar, NO_EXPOSED_PACKAGES, List.of(), ToolingPlatform.BUKKIT, api));
    }

    @Test
    void partUsingAClassThePlatformJarLacksFailsNamingBothClasses() {
        var buildApi = platformJar("org.bukkit.Bukkit", "org.bukkit.entity.Player");
        var checkedApi = platformJar("org.bukkit.Bukkit");
        var jar = moduleWithPart(BUKKIT_PART, usingClass("org.bukkit.entity.Player"), "bukkit")
                .compileAgainst(buildApi)
                .writeInto(build, "user.jar");

        var message = failure(() -> ModuleJarCheck.check(jar, NO_EXPOSED_PACKAGES, List.of(), ToolingPlatform.BUKKIT, checkedApi));

        assertTrue(message.contains(BUKKIT_PART + " -> org.bukkit.entity.Player"), message);
    }

    @Test
    void partUsingADeniedClassFailsNamingTheJarAndBothClasses() {
        var api = platformJar("org.bukkit.craftbukkit.CraftServer");
        var jar = moduleWithPart(BUKKIT_PART, usingClass("org.bukkit.craftbukkit.CraftServer"), "bukkit")
                .compileAgainst(api)
                .writeInto(build, "user.jar");

        var message = failure(() -> ModuleJarCheck.check(jar, NO_EXPOSED_PACKAGES, List.of(), ToolingPlatform.BUKKIT, api));

        assertTrue(message.contains(jar.toString()), message);
        assertTrue(message.contains(BUKKIT_PART + " -> org.bukkit.craftbukkit.CraftServer"), message);
    }

    @Test
    void partOfAnotherPlatformIsSkippedAndListed() {
        var api = platformJar("org.bukkit.Bukkit");
        var jar = moduleWithPart(MODDED_PART, usingClass("org.junit.jupiter.api.Test"), "modded")
                .compileAgainstHostOf(Test.class)
                .writeInto(build, "user.jar");

        var outcome = ModuleJarCheck.run(jar, NO_EXPOSED_PACKAGES, List.of(), Optional.of(bukkitWith(api)));

        assertTrue(outcome.summary().contains("Skipped tooling parts of other platforms: [modded=" + MODDED_PART + "]"), outcome.summary());
    }

    @Test
    void missingPlatformJarFailsNamingIt() {
        var jar = module("pack.User", "user:core", NO_MEMBERS, NO_STAGES).writeInto(build, "user.jar");
        var missing = work.resolve("missing-api.jar");

        var failure = assertThrows(IllegalArgumentException.class,
                () -> ModuleJarCheck.check(jar, NO_EXPOSED_PACKAGES, List.of(), ToolingPlatform.BUKKIT, missing));

        assertTrue(failure.getMessage().contains(missing.toString()), failure.getMessage());
    }

    @Test
    void platformOptionNamesAKnownPlatformAndAJar() {
        var parsed = ModuleJarCheck.CheckedPlatform.parse("bukkit:libs/paper-api.jar");

        assertSame(ToolingPlatform.BUKKIT, parsed.platform());
        assertEquals(Path.of("libs/paper-api.jar"), parsed.apiJar());
    }

    @ParameterizedTest
    @ValueSource(strings = {"bukkit", "bukkit:", ":paper-api.jar", "modded:paper-api.jar"})
    void malformedPlatformOptionFails(String option) {
        assertThrows(IllegalArgumentException.class, () -> ModuleJarCheck.CheckedPlatform.parse(option));
    }

    @Test
    void jarThatUsesAnExposedClassNotLeftOutStillPasses() {
        var jar = module("pack.User", "user:core", NO_MEMBERS,
                "graph.define(Stage.placement(\"user:core/carved\")).requires(example.builtin.CarveModule.CLEAR).run(context -> {});")
                .compileAgainstHostOf(CarveModule.class)
                .writeInto(build, "user.jar");

        assertEquals(List.of("pack.User"), ModuleJarCheck.check(jar, List.of(CarveModule.class.getPackageName()),
                List.of("example/builtin/OtherModule")));
    }
}
