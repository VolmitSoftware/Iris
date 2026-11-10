package com.volmit.host;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ExposedPackagesTest {
    @Test
    void apiIsAlwaysExposedWithItsSubpackages() {
        var exposed = ExposedPackages.apiOnly();

        assertEquals("com.volmit", ExposedPackages.API_PACKAGE);
        assertTrue(exposed.exposes("com.volmit.IrisModule"));
        assertTrue(exposed.exposes("com.volmit.stage.Stage"));
        assertTrue(ExposedPackages.apiAnd(List.of("example.builtin")).exposes("com.volmit.IrisModule"));
    }

    @Test
    void theHostSideIsNeverExposed() {
        var exposed = ExposedPackages.apiAnd(List.of("com"));

        assertEquals("com.volmit.host", ExposedPackages.HOST_PACKAGE);
        assertFalse(exposed.exposes("com.volmit.host.PackModules"));
        assertFalse(exposed.exposes("com.volmit.host.schema.PackSchema"));
        assertFalse(exposed.exposesResource("com/volmit/host/PackModules.class"));
        assertTrue(exposed.exposes("com.volmit.hosting.Other"));
        assertTrue(ExposedPackages.isApiClass("com.volmit.host.PackModules"));

        var failure = assertThrows(IllegalArgumentException.class,
                () -> ExposedPackages.apiAnd(List.of("com.volmit.host.schema")));
        assertEquals("Exposed package 'com.volmit.host.schema' is IrisApi's host side, which packs never see", failure.getMessage());
    }

    @Test
    void platformToolingPackagesAreNeverExposedButTheNeutralToolingApiIs() {
        var exposed = ExposedPackages.apiAnd(List.of("com"));

        assertEquals("com.volmit.tooling", ExposedPackages.TOOLING_PACKAGE);
        assertTrue(exposed.exposes("com.volmit.tooling.Command"));
        assertTrue(exposed.exposes("com.volmit.tooling.Command$Builder"));
        assertFalse(exposed.exposes("com.volmit.tooling.bukkit.BukkitToolPart"));
        assertFalse(exposed.exposes("com.volmit.tooling.modded.deep.ModdedToolPart"));
        assertTrue(exposed.exposes("com.volmit.tooling.world.ToolWorld"), "the world api for tools is neutral");
        assertFalse(exposed.exposes("com.volmit.tooling.worldly.BukkitToolPart"));
        assertTrue(exposed.exposes("com.volmit.toolingx.Other"));
        assertTrue(exposed.exposes("com.volmit.tool.bukkit.Other"));
    }

    @Test
    void platformToolingResourcesAreHiddenLikeTheirClasses() {
        var exposed = ExposedPackages.apiOnly();

        assertTrue(exposed.exposesResource("com/volmit/tooling/Command.class"));
        assertFalse(exposed.exposesResource("com/volmit/tooling/bukkit/BukkitToolPart.class"));
        assertFalse(exposed.exposesResource("com/volmit/tooling/bukkit/part.yml"));
    }

    @Test
    void onlyWholePackageNamesMatch() {
        var exposed = ExposedPackages.apiAnd(List.of("example.builtin"));

        assertTrue(exposed.exposes("example.builtin.CarveModule"));
        assertTrue(exposed.exposes("example.builtin.deep.Stage"));
        assertFalse(exposed.exposes("example.builtinx.CarveModule"));
        assertFalse(exposed.exposes("example.Other"));
        assertFalse(exposed.exposes("com.volmitx.Fake"));
        assertFalse(exposed.exposes("com.volmit"));
    }

    @Test
    void resourcesMatchByPath() {
        var exposed = ExposedPackages.apiAnd(List.of("example.builtin"));

        assertTrue(exposed.exposesResource("example/builtin/CarveModule.class"));
        assertTrue(exposed.exposesResource("example/builtin/carve.json"));
        assertTrue(exposed.exposesResource("com/volmit/IrisModule.class"));
        assertFalse(exposed.exposesResource("example/builtinx/carve.json"));
        assertFalse(exposed.exposesResource("META-INF/services/com.volmit.IrisModule"));
    }

    @Test
    void trailingDotIsAccepted() {
        assertTrue(ExposedPackages.apiAnd(List.of("example.builtin.")).exposes("example.builtin.CarveModule"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", ".", "example..builtin", ".example", "example.*", "example.builtin.**", "1example", "example builtin"})
    void rejectsWhatIsNotAPackageName(String name) {
        var message = assertThrows(IllegalArgumentException.class, () -> ExposedPackages.apiAnd(List.of(name))).getMessage();

        assertTrue(message.startsWith("Exposed package '" + name + "' is not a package name"), message);
    }

    @Test
    void namesTheMostSpecificExposedPackageOfAClass() {
        var exposed = ExposedPackages.apiAnd(List.of("example", "example.builtin"));

        assertEquals("example.builtin", exposed.packageOf("example.builtin.CarveModule"));
        assertEquals("example", exposed.packageOf("example.Other"));
        assertEquals("com.volmit", exposed.packageOf("com.volmit.IrisModule"));
    }
}
