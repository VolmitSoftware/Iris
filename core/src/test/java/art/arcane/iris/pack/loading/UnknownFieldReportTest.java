package art.arcane.iris.pack.loading;

import art.arcane.iris.localization.ChatReport;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UnknownFieldReportTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

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

        List<String> lines = plain(UnknownFieldReport.drainReports());

        assertEquals(List.of(
                "! Pack 'cavetest' has 1 unknown field. Iris ignores it.",
                "└ Biome 'caves'  1 file   biomes/carving/volcanic.json:144"
        ), lines);
    }

    @Test
    public void manyFilesWithTheSameFieldShowOneExampleAndACount() {
        recordBiomeCaves(SOURCE_PACK, "biomes/carving/deep-ravine.json", 157);
        recordBiomeCaves(SOURCE_PACK, "biomes/carving/ice-ravine.json", 149);
        recordBiomeCaves(SOURCE_PACK, "biomes/carving/volcanic.json", 144);
        UnknownFieldReport.record(SOURCE_PACK, new File(SOURCE_PACK, "dimensions/cavetest.json"), "Dimension", "caves", 856, null);

        List<String> lines = plain(UnknownFieldReport.drainReports());

        assertEquals(List.of(
                "! Pack 'cavetest' has 2 unknown fields. Iris ignores them.",
                "├ Biome 'caves'      3 files  e.g. biomes/carving/deep-ravine.json:157",
                "└ Dimension 'caves'  1 file   dimensions/cavetest.json:856"
        ), lines);
    }

    @Test
    public void typoKeepsItsSuggestion() {
        UnknownFieldReport.record(SOURCE_PACK, new File(SOURCE_PACK, "biomes/x.json"), "Biome", "carvng", 12, "carving");

        List<String> lines = plain(UnknownFieldReport.drainReports());

        assertTrue(lines.get(1).endsWith("biomes/x.json:12  Did you mean 'carving'?"));
    }

    @Test
    public void alreadyReportedFieldsStayQuietInLaterBatches() {
        recordBiomeCaves(SOURCE_PACK, "biomes/carving/volcanic.json", 144);
        UnknownFieldReport.drainReports();

        recordBiomeCaves(EPOCH_PACK, "biomes/carving/volcanic.json", 144);

        assertEquals(List.of(), UnknownFieldReport.drainReports());
    }

    @Test
    public void studioEpochIsLabelledByItsDimensionNotItsWorldFolder() throws Exception {
        Path epoch = temporaryFolder.getRoot().toPath()
                .resolve("world/dimensions/iris/iris-d4521eb8/iris/generation/epochs/d826fe90");
        Files.createDirectories(epoch.resolve("pack"));
        Files.writeString(epoch.resolve("epoch.json"), "{\"dimensionContract\":{\"dimensionKey\":\"cavetest\"}}");
        File studioPack = epoch.resolve("pack").toFile();

        recordBiomeCaves(SOURCE_PACK, "biomes/carving/volcanic.json", 144);
        recordBiomeCaves(studioPack, "biomes/carving/volcanic.json", 144);

        assertEquals(2, plain(UnknownFieldReport.drainReports()).size());
    }

    private static void recordBiomeCaves(File pack, String relativePath, int line) {
        UnknownFieldReport.record(pack, new File(pack, relativePath), "Biome", "caves", line, null);
    }

    private static List<String> plain(List<ChatReport> reports) {
        return reports.stream()
                .flatMap(report -> report.consoleLines().stream())
                .map(line -> line.replaceAll("(?i)\u00a7[0-9a-fk-orx]|<[^>]+>", ""))
                .toList();
    }
}
