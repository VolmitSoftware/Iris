package com.volmit.host;

import com.volmit.support.FakePlatformApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import static com.volmit.support.TestModuleJar.module;
import static org.junit.jupiter.api.Assertions.*;

class ToolingPartLoaderTest {
    private static final String PART = "pack.tools.bukkit.StudioPart";
    private static final String PART_HELPER = "pack.tools.bukkit.deep.Helper";
    private static final String GENERATION_CLASS = "pack.model.Thing";
    private static final String PLATFORM_CLASS = "org.bukkit.Bukkit";

    @TempDir
    Path pack;
    @TempDir
    Path work;

    private FakePlatformApi platform;
    private PackModules modules;

    @BeforeEach
    void loadPackWithABukkitPart() {
        platform = FakePlatformApi.of(work, PLATFORM_CLASS, "org.bukkit.craftbukkit.CraftServer", "net.minecraft.MinecraftServer",
                "com.mojang.Brigadier", "art.arcane.iris.Engine");
        module("pack.User", "user:core", "", "")
                .source(GENERATION_CLASS, "package pack.model; public final class Thing {}")
                .source(PART, """
                        package pack.tools.bukkit;
                        public final class StudioPart {
                            public static Class<?> thing() { return pack.model.Thing.class; }
                            public static Class<?> platform() { return org.bukkit.Bukkit.class; }
                            public static Class<?> helper() { return pack.tools.bukkit.deep.Helper.class; }
                        }
                        """)
                .source(PART_HELPER, "package pack.tools.bukkit.deep; public final class Helper {}")
                .compileAgainst(platform.jar())
                .toolingParts("bukkit=" + PART)
                .writeInto(pack, "user.jar");
        modules = PackModules.load(pack, List.of(), List.of(), work, PackModules.DEFAULT_LOGGER,
                Optional.of(platform.asApiOf(ToolingPlatform.BUKKIT)));
    }

    @AfterEach
    void close() {
        modules.close();
        platform.close();
    }

    private ClassLoader packLoader() {
        return modules.modules().getFirst().module().getClass().getClassLoader();
    }

    private ToolingPartLoader openThePart() {
        return modules.openToolingPart(modules.toolingParts().getFirst());
    }

    private static Class<?> call(Class<?> type, String method) throws ReflectiveOperationException {
        return (Class<?>) type.getMethod(method).invoke(null);
    }

    @Test
    void definesTheEntryClassAndTheRestOfThePartItselfFromTheSameJarCopy() throws Exception {
        try (var loader = openThePart()) {
            var entry = loader.loadEntryClass();
            var helper = call(entry, "helper");

            assertEquals(PART, entry.getName());
            assertNotSame(packLoader(), entry.getClassLoader());
            assertSame(entry.getClassLoader(), helper.getClassLoader());
            assertNotSame(packLoader().loadClass(PART), entry);
            assertEquals(modules.toolingParts().getFirst().jar().toUri().toURL(), entry.getProtectionDomain().getCodeSource().getLocation());
        }
    }

    @Test
    void childOfThePackLoader() throws Exception {
        try (var loader = openThePart()) {
            assertSame(packLoader(), loader.loadEntryClass().getClassLoader().getParent());
        }
    }

    @Test
    void delegatesPlatformPackagesToThePlatformLoader() throws Exception {
        try (var loader = openThePart()) {
            var fromPart = call(loader.loadEntryClass(), "platform");

            assertSame(platform.classes().loadClass(PLATFORM_CLASS), fromPart);
        }
    }

    @Test
    void returnsTheClassObjectsTheModuleRegisteredForGenerationClasses() throws Exception {
        try (var loader = openThePart()) {
            var fromPart = call(loader.loadEntryClass(), "thing");

            assertSame(packLoader().loadClass(GENERATION_CLASS), fromPart);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"org.bukkit.craftbukkit.CraftServer", "net.minecraft.MinecraftServer", "com.mojang.Brigadier",
            "art.arcane.iris.Engine", "com.volmit.host.ModuleHost"})
    void refusesWhatAPartMustNotSee(String denied) {
        try (var loader = openThePart()) {
            var partLoader = loader.loadEntryClass().getClassLoader();

            assertThrows(ClassNotFoundException.class, () -> partLoader.loadClass(denied));
        }
    }

    @Test
    void closingItLeavesThePackLoaderAndThePlatformOpen() throws Exception {
        var loader = openThePart();
        var partLoader = loader.loadEntryClass().getClassLoader();

        loader.close();

        assertSame(packLoader().loadClass(GENERATION_CLASS), packLoader().loadClass(GENERATION_CLASS));
        assertNotNull(platform.classes().loadClass(PLATFORM_CLASS));
        assertThrows(ClassNotFoundException.class, () -> partLoader.loadClass(PART_HELPER));
    }

    @Test
    void entryClassThatCannotBeLoadedIsReportedNamingTheJarAndThePart() {
        var unlisted = new ToolingPart("bukkit", "pack.tools.bukkit.Nowhere", new PackageName("pack.tools.bukkit"),
                "modules/user.jar", modules.toolingParts().getFirst().jar());
        try (var loader = ToolingPartLoader.open(unlisted, packLoader(), ExposedPackages.apiOnly(), platform.asApiOf(ToolingPlatform.BUKKIT))) {
            var failure = assertThrows(ModuleLoadException.class, loader::loadEntryClass);

            assertTrue(failure.getMessage().contains("modules/user.jar"), failure.getMessage());
            assertTrue(failure.getMessage().contains("bukkit=pack.tools.bukkit.Nowhere"), failure.getMessage());
        }
    }

    @Test
    void openingAPartThePackDoesNotListFails() {
        var stranger = new ToolingPart("bukkit", PART, new PackageName("pack.tools.bukkit"), "modules/other.jar", pack);

        assertThrows(IllegalArgumentException.class, () -> modules.openToolingPart(stranger));
    }

    @Test
    void loadedPackListsAndOpensThePartsOfTheHostsPlatform() throws Exception {
        var host = ModuleHost.of(List.of()).runningOn(platform.asApiOf(ToolingPlatform.BUKKIT));

        try (var loaded = LoadedPack.load(pack, host); var loader = loaded.openToolingPart(loaded.toolingParts().getFirst())) {
            assertEquals(PART, loader.loadEntryClass().getName());
        }
    }

    @Test
    void hostWithoutAPlatformHasNoPartsToOpen() {
        try (var loaded = LoadedPack.load(pack, ModuleHost.of(List.of()))) {
            assertEquals(List.of(), loaded.toolingParts());
        }
    }
}
