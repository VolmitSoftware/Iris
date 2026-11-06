package com.volmit.host;

import com.volmit.support.TestModuleJar;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.util.List;
import static com.volmit.support.TestModuleJar.module;
import static org.junit.jupiter.api.Assertions.*;

class ToolingPartTest {
    private static final String ORIGIN = "modules/user.jar";

    @TempDir
    Path pack;

    private static TestModuleJar jarWithPartClasses(String... classNames) {
        var jar = module("pack.User", "user:core", "", "");
        for (var className : classNames) {
            int dot = className.lastIndexOf('.');
            jar.source(className, "package " + className.substring(0, dot) + "; public final class " + className.substring(dot + 1) + " {}");
        }
        return jar;
    }

    private ModuleJar read(TestModuleJar jar) {
        return ModuleJar.read(new JarSnapshot.CopiedJar(ORIGIN, jar.writeInto(pack, "user.jar")));
    }

    private String failureOf(TestModuleJar jar) {
        return assertThrows(ModuleLoadException.class, () -> read(jar)).getMessage();
    }

    @Test
    void jarWithoutTheAttributeHasNoParts() {
        assertEquals(List.of(), read(jarWithPartClasses()).toolingParts());
    }

    @Test
    void blankAttributeHasNoParts() {
        assertEquals(List.of(), read(jarWithPartClasses().toolingParts("  ")).toolingParts());
    }

    @Test
    void severalEntriesAreReadInListedOrderAndAPlatformMayRepeat() {
        var jar = jarWithPartClasses("pack.tools.bukkit.StudioPart", "pack.other.bukkit.ImportPart", "pack.tools.modded.ModdedPart")
                .toolingParts("bukkit=pack.tools.bukkit.StudioPart  modded=pack.tools.modded.ModdedPart\tbukkit=pack.other.bukkit.ImportPart");

        var parts = read(jar).toolingParts();

        assertEquals(List.of("bukkit=pack.tools.bukkit.StudioPart", "modded=pack.tools.modded.ModdedPart",
                "bukkit=pack.other.bukkit.ImportPart"), parts.stream().map(ToolingPart::declaration).toList());
        assertEquals(new PackageName("pack.tools.bukkit"), parts.getFirst().packageName());
        assertEquals(ORIGIN, parts.getFirst().jarOrigin());
        assertTrue(parts.getFirst().contains("pack.tools.bukkit.deep.Helper"));
        assertFalse(parts.getFirst().contains("pack.tools.bukkitx.Helper"));
    }

    @Test
    void twoEntriesOfOnePlatformMayShareAPackage() {
        var jar = jarWithPartClasses("pack.tools.bukkit.StudioPart", "pack.tools.bukkit.ImportPart")
                .toolingParts("bukkit=pack.tools.bukkit.StudioPart bukkit=pack.tools.bukkit.ImportPart");

        assertEquals(2, read(jar).toolingParts().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"bukkit", "bukkit=", "=pack.tools.StudioPart", "Bukkit=pack.tools.StudioPart", "1bukkit=pack.tools.StudioPart",
            "bukkit=StudioPart", "bukkit=pack.tools.", "bukkit=pack..StudioPart", "bukkit=pack.tools.StudioPart=again", "bukkit:pack.tools.StudioPart"})
    void malformedEntryFailsNamingTheJarAndTheEntry(String entry) {
        var message = failureOf(jarWithPartClasses("pack.tools.StudioPart").toolingParts("modded=pack.tools.StudioPart " + entry));

        assertTrue(message.contains(ORIGIN), message);
        assertTrue(message.contains("'" + entry + "'"), message);
        assertTrue(message.contains("Iris-Tooling-Parts"), message);
    }

    @Test
    void entryClassThatTheJarDoesNotHoldFailsNamingTheJarAndTheEntry() {
        var message = failureOf(jarWithPartClasses("pack.tools.StudioPart").toolingParts("bukkit=pack.tools.MissingPart"));

        assertTrue(message.contains(ORIGIN), message);
        assertTrue(message.contains("bukkit=pack.tools.MissingPart"), message);
        assertTrue(message.contains("holds no class pack.tools.MissingPart"), message);
    }

    @Test
    void partsOfDifferentPlatformsInOnePackageFail() {
        var jar = jarWithPartClasses("pack.tools.BukkitPart", "pack.tools.ModdedPart")
                .toolingParts("bukkit=pack.tools.BukkitPart modded=pack.tools.ModdedPart");

        var message = failureOf(jar);

        assertTrue(message.contains(ORIGIN), message);
        assertTrue(message.contains("overlapping tooling parts bukkit=pack.tools.BukkitPart and modded=pack.tools.ModdedPart"), message);
    }

    @Test
    void partInsideAnotherPartsPackageFails() {
        var jar = jarWithPartClasses("pack.tools.StudioPart", "pack.tools.deep.ImportPart")
                .toolingParts("bukkit=pack.tools.StudioPart bukkit=pack.tools.deep.ImportPart");

        assertTrue(failureOf(jar).contains("overlapping tooling parts"));
    }
}
