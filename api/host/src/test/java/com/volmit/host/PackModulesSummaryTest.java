package com.volmit.host;

import com.volmit.IrisModule;
import com.volmit.example.placement.MossModule;
import com.volmit.example.placement.SnowModule;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackModulesSummaryTest {
    private static DiscoveredModule pack(IrisModule module, String origin) {
        return DiscoveredModule.fromJar(module, module.info(), origin);
    }

    @Test
    void listsTheVersionAndJarOfEveryModuleInLoadOrder() {
        var modules = List.of(
                pack(new MossModule(), "modules/moss.jar"),
                pack(new SnowModule(), "modules/snow.jar"));

        assertEquals("""
                Pack overworld uses 2 modules:
                  Moss 1.0.0 (modules/moss.jar), Snow test (modules/snow.jar)""",
                PackModulesSummary.describe("overworld", modules));
    }

    @Test
    void wrapsALongListAtTheSameIndent() {
        var modules = Collections.nCopies(20, pack(new SnowModule(), "modules/snow.jar"));

        String[] lines = PackModulesSummary.describe("overworld", modules).split("\n");

        assertTrue(lines.length > 2);
        assertTrue(lines[1].startsWith("  Snow test (modules/snow.jar), Snow"));
        assertTrue(lines[2].startsWith("  Snow"));
        for (String line : lines) {
            assertTrue(line.length() <= 100, line);
        }
    }

    @Test
    void singleModuleReadsAsOneModule() {
        var modules = List.of(pack(new MossModule(), "modules/moss.jar"));

        assertEquals("""
                Pack overworld uses 1 module:
                  Moss 1.0.0 (modules/moss.jar)""",
                PackModulesSummary.describe("overworld", modules));
    }

    @Test
    void packWithoutModulesSaysSo() {
        assertEquals("Pack overworld uses no modules: no jars in its modules directory",
                PackModulesSummary.describe("overworld", List.of()));
    }
}
