package com.volmit.host;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ToolingPlatformTest {
    @ParameterizedTest
    @ValueSource(strings = {"com.volmit.tooling.bukkit.BukkitToolPart", "org.bukkit.Bukkit", "org.bukkit.entity.Player", "io.papermc.paper.Paper",
            "com.destroystokyo.paper.Event", "net.kyori.adventure.text.Component"})
    void bukkitApiPackagesAreThePlatformsApi(String className) {
        assertTrue(ToolingPlatform.BUKKIT.hasApiPackageOf(className));
    }

    @ParameterizedTest
    @ValueSource(strings = {"org.bukkitx.Other", "com.volmit.tooling.Command", "com.google.gson.Gson", "java.lang.String"})
    void otherClassesAreNotBukkitApi(String className) {
        assertFalse(ToolingPlatform.BUKKIT.hasApiPackageOf(className));
    }

    @ParameterizedTest
    @ValueSource(strings = {"art.arcane.iris.Iris", "art.arcane.iris.builtin.Thing", "net.minecraft.world.level.Level", "org.bukkit.craftbukkit.CraftServer",
            "org.bukkit.craftbukkit.v26.CraftWorld", "com.mojang.brigadier.Command", "com.volmit.host.PackModules"})
    void bukkitDeniesIrisInternalsTheServerAndTheHost(String className) {
        assertTrue(ToolingPlatform.BUKKIT.deniesPackageOf(className));
    }

    @Test
    void bukkitDoesNotDenyItsOwnApiOrTheIrisApi() {
        assertFalse(ToolingPlatform.BUKKIT.deniesPackageOf("org.bukkit.Bukkit"));
        assertFalse(ToolingPlatform.BUKKIT.deniesPackageOf("com.volmit.IrisModule"));
        assertFalse(ToolingPlatform.BUKKIT.deniesPackageOf("art.arcane.irisx.Other"));
    }

    @Test
    void namedFindsTheDescriptionsTheHostKnows() {
        assertSame(ToolingPlatform.BUKKIT, ToolingPlatform.named("bukkit"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> ToolingPlatform.named("modded")).getMessage()
                .contains("Unknown tooling platform 'modded'"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Bukkit", "1bukkit", "bu-kkit", "bukkit.paper"})
    void platformNameIsLowerCaseLettersAndDigits(String name) {
        assertThrows(IllegalArgumentException.class, () -> new ToolingPlatform(name, List.of(), List.of()));
    }
}
