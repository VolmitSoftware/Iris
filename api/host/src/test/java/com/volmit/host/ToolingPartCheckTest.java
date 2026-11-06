package com.volmit.host;

import com.volmit.support.FakePlatformApi;
import com.volmit.support.TestModuleJar;
import example.builtin.CarveModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import static com.volmit.support.TestModuleJar.module;
import static org.junit.jupiter.api.Assertions.*;

class ToolingPartCheckTest {
    private static final String BUKKIT_PART = "pack.tools.bukkit.StudioPart";
    private static final String MODDED_PART = "pack.tools.modded.ModdedPart";
    private static final String BUKKIT_PART_DECLARATION = "bukkit=" + BUKKIT_PART;
    private static final String GENERATION_CLASS = "pack.model.Thing";
    private static final String PART_PACKAGE_NOTICE = "Generation code cannot use the bukkit part; move the call into the part.";

    @TempDir
    Path pack;
    @TempDir
    Path work;

    private FakePlatformApi platform;

    @AfterEach
    void closePlatform() {
        if (platform != null) platform.close();
    }

    private PlatformApi bukkitApi(String... classNames) {
        platform = FakePlatformApi.of(work, classNames);
        return platform.asApiOf(ToolingPlatform.BUKKIT);
    }

    private static String classWith(String className, String body) {
        int dot = className.lastIndexOf('.');
        return "package " + className.substring(0, dot) + "; public final class " + className.substring(dot + 1) + " { " + body + " }";
    }

    private static String using(String className) {
        return "static final Class<?> " + className.replace('.', '_') + " = " + className + ".class; ";
    }

    private static TestModuleJar jar(String generationMembers) {
        return module("pack.User", "user:core", generationMembers, "")
                .source(GENERATION_CLASS, classWith(GENERATION_CLASS, ""));
    }

    private PackModules load(TestModuleJar jar, Optional<PlatformApi> runningPlatform) {
        jar.writeInto(pack, "user.jar");
        return PackModules.load(pack, List.of(), List.of(), work, PackModules.DEFAULT_LOGGER, runningPlatform);
    }

    private String loadFailure(TestModuleJar jar, Optional<PlatformApi> runningPlatform) {
        return assertThrows(ModuleLoadException.class, () -> load(jar, runningPlatform)).getMessage();
    }

    @Test
    void partOfTheRunningPlatformMayUseItsPackagesGenerationClassesAndThePlatformApi() {
        var api = bukkitApi("org.bukkit.Bukkit", "io.papermc.paper.Paper");
        var jar = jar("")
                .source(BUKKIT_PART, classWith(BUKKIT_PART, using("org.bukkit.Bukkit") + using("io.papermc.paper.Paper")
                        + using("pack.tools.bukkit.deep.Helper") + using(GENERATION_CLASS)))
                .source("pack.tools.bukkit.deep.Helper", classWith("pack.tools.bukkit.deep.Helper", ""))
                .compileAgainst(platform.jar())
                .toolingParts(BUKKIT_PART_DECLARATION);

        try (var modules = load(jar, Optional.of(api))) {
            assertEquals(List.of(BUKKIT_PART_DECLARATION), modules.toolingParts().stream().map(ToolingPart::declaration).toList());
        }
    }

    @Test
    void generationClassUsingAPartClassFailsNamingBothClassesAndTheMove() {
        var api = bukkitApi("org.bukkit.Bukkit");
        var jar = jar("static Object leak() { return new " + BUKKIT_PART + "(); }")
                .source(BUKKIT_PART, classWith(BUKKIT_PART, "public StudioPart() {}"))
                .toolingParts(BUKKIT_PART_DECLARATION);

        var message = loadFailure(jar, Optional.of(api));

        assertTrue(message.contains("modules/user.jar"), message);
        assertTrue(message.contains("pack.User -> " + BUKKIT_PART), message);
        assertTrue(message.contains(PART_PACKAGE_NOTICE), message);
    }

    @Test
    void generationClassUsingAPartClassFailsWhateverPlatformTheHostRunsOn() {
        var jar = jar("static Object leak() { return new " + MODDED_PART + "(); }")
                .source(MODDED_PART, classWith(MODDED_PART, "public ModdedPart() {}"))
                .toolingParts("modded=" + MODDED_PART);

        var message = loadFailure(jar, Optional.empty());

        assertTrue(message.contains("pack.User -> " + MODDED_PART), message);
        assertTrue(message.contains("Generation code cannot use the modded part; move the call into the part."), message);
    }

    @ParameterizedTest
    @ValueSource(strings = {"org.bukkit.craftbukkit.CraftServer", "net.minecraft.MinecraftServer", "com.mojang.Brigadier",
            "art.arcane.iris.Engine", "art.arcane.iris.engine.Deep"})
    void partClassUsingADeniedPackageFailsNamingBothClasses(String denied) {
        var api = bukkitApi("org.bukkit.craftbukkit.CraftServer", "net.minecraft.MinecraftServer", "com.mojang.Brigadier",
                "art.arcane.iris.Engine", "art.arcane.iris.engine.Deep");
        var jar = jar("")
                .source(BUKKIT_PART, classWith(BUKKIT_PART, using(denied)))
                .compileAgainst(platform.jar())
                .toolingParts(BUKKIT_PART_DECLARATION);

        var message = loadFailure(jar, Optional.of(api));

        assertTrue(message.contains("bukkit part classes that use classes parts cannot see"), message);
        assertTrue(message.contains(BUKKIT_PART + " -> " + denied), message);
    }

    @Test
    void partClassUsingTheHostSideOfIrisApiFails() {
        var api = bukkitApi("org.bukkit.Bukkit");
        var jar = jar("")
                .source(BUKKIT_PART, classWith(BUKKIT_PART, using("com.volmit.host.ModuleHost")))
                .compileAgainstHostOf(ModuleHost.class)
                .toolingParts(BUKKIT_PART_DECLARATION);

        var message = loadFailure(jar, Optional.of(api));

        assertTrue(message.contains(BUKKIT_PART + " -> com.volmit.host.ModuleHost"), message);
    }

    /** A platform that denies all of {@code example}, where the built-in module classes of the tests live. */
    private PlatformApi platformDenyingExample() {
        platform = FakePlatformApi.of(work, "org.bukkit.Bukkit");
        var denied = List.of(new PackageName("example"));
        return platform.asApiOf(new ToolingPlatform("bukkit", List.of(new PackageName("org.bukkit")), denied));
    }

    private TestModuleJar partUsingTheBuiltInModule() {
        return jar("")
                .source(BUKKIT_PART, classWith(BUKKIT_PART, using("example.builtin.CarveModule")))
                .compileAgainstHostOf(CarveModule.class)
                .toolingParts(BUKKIT_PART_DECLARATION);
    }

    @Test
    void deniedPackageStaysDeniedWhenTheHostDoesNotExposeIt() {
        var message = loadFailure(partUsingTheBuiltInModule(), Optional.of(platformDenyingExample()));

        assertTrue(message.contains(BUKKIT_PART + " -> example.builtin.CarveModule"), message);
    }

    @Test
    void deniedPackageIsUsableWhenTheHostExposesTheClassToModules() {
        partUsingTheBuiltInModule().writeInto(pack, "user.jar");
        var exposed = List.of(CarveModule.class.getPackageName());

        try (var modules = PackModules.load(pack, exposed, List.of(), work, PackModules.DEFAULT_LOGGER, Optional.of(platformDenyingExample()))) {
            assertEquals(1, modules.toolingParts().size());
        }
    }

    @Test
    void platformApiClassThePlatformLacksFailsNamingBothClasses() {
        var buildApi = FakePlatformApi.of(work, "org.bukkit.Bukkit", "org.bukkit.entity.Player");
        var jar = jar("")
                .source(BUKKIT_PART, classWith(BUKKIT_PART, using("org.bukkit.entity.Player")))
                .compileAgainst(buildApi.jar())
                .toolingParts(BUKKIT_PART_DECLARATION);
        buildApi.close();

        var message = loadFailure(jar, Optional.of(bukkitApi("org.bukkit.Bukkit")));

        assertTrue(message.contains("bukkit API classes this server does not have"), message);
        assertTrue(message.contains(BUKKIT_PART + " -> org.bukkit.entity.Player"), message);
    }

    @Test
    void partClassUsingAClassOfAnotherPartFails() {
        var api = bukkitApi("org.bukkit.Bukkit");
        var otherPart = "pack.other.bukkit.ImportPart";
        var jar = jar("")
                .source(BUKKIT_PART, classWith(BUKKIT_PART, using(otherPart)))
                .source(otherPart, classWith(otherPart, ""))
                .toolingParts(BUKKIT_PART_DECLARATION + " bukkit=" + otherPart);

        var message = loadFailure(jar, Optional.of(api));

        assertTrue(message.contains("bukkit part classes that use classes of another part"), message);
        assertTrue(message.contains(BUKKIT_PART + " -> " + otherPart), message);
    }

    @Test
    void partOfAnotherPlatformIsSkippedUnread() {
        var api = bukkitApi("org.bukkit.Bukkit");
        var jar = jar("")
                .source(MODDED_PART, classWith(MODDED_PART, using("org.junit.jupiter.api.Test") + using("net.minecraft.MinecraftServer")))
                .compileAgainstHostOf(org.junit.jupiter.api.Test.class)
                .source("net.minecraft.MinecraftServer", classWith("net.minecraft.MinecraftServer", ""))
                .toolingParts("modded=" + MODDED_PART);

        try (var modules = load(jar, Optional.of(api))) {
            assertEquals(List.of(), modules.toolingParts());
            assertEquals(List.of(), modules.missingClasses());
        }
    }

    @Test
    void partsAreSkippedWhenTheHostRunsOnNoPlatform() {
        var jar = jar("")
                .source(BUKKIT_PART, classWith(BUKKIT_PART, using("org.junit.jupiter.api.Test")))
                .compileAgainstHostOf(org.junit.jupiter.api.Test.class)
                .toolingParts(BUKKIT_PART_DECLARATION);

        try (var modules = load(jar, Optional.empty())) {
            assertEquals(List.of(), modules.toolingParts());
        }
    }

    @Test
    void sameClassOutsideAPartIsStillCheckedAsGenerationCode() {
        var api = bukkitApi("org.bukkit.Bukkit");
        var jar = jar(using("org.junit.jupiter.api.Test"))
                .compileAgainstHostOf(org.junit.jupiter.api.Test.class)
                .source(MODDED_PART, classWith(MODDED_PART, ""))
                .toolingParts("modded=" + MODDED_PART);

        var message = loadFailure(jar, Optional.of(api));

        assertTrue(message.contains("pack.User -> org.junit.jupiter.api.Test"), message);
    }

    @Test
    void partClassUsingAHiddenClassFailsLikeGenerationCode() {
        var api = bukkitApi("org.bukkit.Bukkit");
        var jar = jar("")
                .source(BUKKIT_PART, classWith(BUKKIT_PART, using("org.junit.jupiter.api.Test")))
                .compileAgainstHostOf(org.junit.jupiter.api.Test.class)
                .toolingParts(BUKKIT_PART_DECLARATION);

        var message = loadFailure(jar, Optional.of(api));

        assertTrue(message.contains("hidden from modules"), message);
        assertTrue(message.contains(BUKKIT_PART + " -> org.junit.jupiter.api.Test"), message);
    }
}
