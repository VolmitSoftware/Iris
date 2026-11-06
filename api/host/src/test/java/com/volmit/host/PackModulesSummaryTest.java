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

    @Test
    void listsNameIdVersionAndJarOfEveryModuleInLoadOrder() {
        var modules = List.of(
                pack(new MossModule(), "modules/moss.jar"),
                pack(new SnowModule(), "modules/snow.jar"));

        assertEquals("""
                Pack overworld uses 2 modules:
                  Moss (example:moss 1.0.0) from modules/moss.jar
                  Snow (iris:snow test) from modules/snow.jar""",
                PackModulesSummary.describe("overworld", modules));
    }

    @Test
    void singleModuleReadsAsOneModule() {
        var modules = List.of(pack(new MossModule(), "modules/moss.jar"));

        assertEquals("""
                Pack overworld uses 1 module:
                  Moss (example:moss 1.0.0) from modules/moss.jar""",
                PackModulesSummary.describe("overworld", modules));
    }

    @Test
    void packWithoutModulesSaysSo() {
        assertEquals("Pack overworld uses no modules: no jars in its modules directory",
                PackModulesSummary.describe("overworld", List.of()));
    }
}
