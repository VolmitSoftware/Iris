package com.volmit.host;

import com.volmit.IrisModule;
import com.volmit.example.placement.MossModule;
import com.volmit.example.placement.SnowModule;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PackModulesSummaryTest {
    private static DiscoveredModule pack(IrisModule module, String origin) {
        return DiscoveredModule.fromJar(module, module.info(), origin);
    }

    private static DiscoveredModule bundled(IrisModule module) {
        return pack(module, "builtins.jar" + PackModules.BUNDLED_ORIGIN_SUFFIX);
    }

    @Test
    void listsNameIdVersionAndJarOfEveryModuleThePackBringsInLoadOrder() {
        var modules = List.of(
                pack(new MossModule(), "modules/moss.jar"),
                pack(new SnowModule(), "modules/snow.jar"));

        assertEquals("""
                Pack overworld uses 2 modules: all from the pack
                  Moss (example:moss 1.0.0) from modules/moss.jar
                  Snow (iris:snow test) from modules/snow.jar""",
                PackModulesSummary.describe("overworld", modules));
    }

    @Test
    void hostModulesAreOnlyCounted() {
        var modules = List.of(
                bundled(new SnowModule()),
                DiscoveredModule.builtIn(new SnowModule()));

        assertEquals("Pack overworld uses 2 modules: all from the host",
                PackModulesSummary.describe("overworld", modules));
    }

    @Test
    void aMixedPackCountsBothAndListsOnlyItsOwn() {
        var modules = List.of(
                bundled(new SnowModule()),
                pack(new MossModule(), "modules/moss.jar"));

        assertEquals("""
                Pack overworld uses 2 modules: 1 from the host, 1 from the pack
                  Moss (example:moss 1.0.0) from modules/moss.jar""",
                PackModulesSummary.describe("overworld", modules));
    }

    @Test
    void singleModuleReadsAsOneModule() {
        var modules = List.of(pack(new MossModule(), "modules/moss.jar"));

        assertEquals("""
                Pack overworld uses 1 module: all from the pack
                  Moss (example:moss 1.0.0) from modules/moss.jar""",
                PackModulesSummary.describe("overworld", modules));
    }

    @Test
    void packWithoutModulesSaysSo() {
        assertEquals("Pack overworld uses no modules: no jars in its modules directory",
                PackModulesSummary.describe("overworld", List.of()));
    }
}
