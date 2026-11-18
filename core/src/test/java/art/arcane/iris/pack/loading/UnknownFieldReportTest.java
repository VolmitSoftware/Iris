package art.arcane.iris.pack.loading;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UnknownFieldReportTest {
    private static final File SOURCE_PACK = new File("/server/plugins/Iris/packs/cavetest");
    private static final File EPOCH_PACK = new File("/server/world/dimensions/iris/cavetest/iris/generation/epochs/"
            + "ba9f3038756c38bc3f3a3174f3e7c575b766f76f422a129d6e8eb98380c33b8b/pack");

    @Before
    public void reset() {
        UnknownFieldReport.resetForTests();
    }

    @After
    public void cleanUp() {
        UnknownFieldReport.resetForTests();
    }

    @Test
    public void epochCopyAndRepeatedLoadsCollapseIntoOneEntry() {
        recordBiomeCaves(SOURCE_PACK, "biomes/carving/volcanic.json", 144);
        recordBiomeCaves(EPOCH_PACK, "biomes/carving/volcanic.json", 144);
        recordBiomeCaves(SOURCE_PACK, "biomes/carving/volcanic.json", 144);

        List<String> lines = plain(UnknownFieldReport.drainReportLines());

        assertEquals(List.of(
                "cavetest: unknown fields, ignored",
                "  Biome \"caves\"  1 file   biomes/carving/volcanic.json:144"
        ), lines);
    }

    @Test
    public void manyFilesWithTheSameFieldShowOneExampleAndACount() {
        recordBiomeCaves(SOURCE_PACK, "biomes/carving/deep-ravine.json", 157);
        recordBiomeCaves(SOURCE_PACK, "biomes/carving/ice-ravine.json", 149);
        recordBiomeCaves(SOURCE_PACK, "biomes/carving/volcanic.json", 144);
        UnknownFieldReport.record(SOURCE_PACK, new File(SOURCE_PACK, "dimensions/cavetest.json"), "Dimension", "caves", 856, null);

        List<String> lines = plain(UnknownFieldReport.drainReportLines());

        assertEquals(List.of(
                "cavetest: unknown fields, ignored",
                "  Biome \"caves\"      3 files  e.g. biomes/carving/deep-ravine.json:157",
                "  Dimension \"caves\"  1 file   dimensions/cavetest.json:856"
        ), lines);
    }

    @Test
    public void typoKeepsItsSuggestion() {
        UnknownFieldReport.record(SOURCE_PACK, new File(SOURCE_PACK, "biomes/x.json"), "Biome", "carvng", 12, "carving");

        List<String> lines = plain(UnknownFieldReport.drainReportLines());

        assertTrue(lines.get(1).endsWith("biomes/x.json:12 (did you mean \"carving\"?)"));
    }

    @Test
    public void alreadyReportedFieldsStayQuietInLaterBatches() {
        recordBiomeCaves(SOURCE_PACK, "biomes/carving/volcanic.json", 144);
        UnknownFieldReport.drainReportLines();

        recordBiomeCaves(EPOCH_PACK, "biomes/carving/volcanic.json", 144);

        assertEquals(List.of(), UnknownFieldReport.drainReportLines());
    }

    private static void recordBiomeCaves(File pack, String relativePath, int line) {
        UnknownFieldReport.record(pack, new File(pack, relativePath), "Biome", "caves", line, null);
    }

    private static List<String> plain(List<String> lines) {
        return lines.stream().map(line -> line.replaceAll("(?i)\u00a7[0-9a-fk-orx]|<[^>]+>", "")).toList();
    }
}
