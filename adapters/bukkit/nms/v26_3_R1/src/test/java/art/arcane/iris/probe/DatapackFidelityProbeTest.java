package art.arcane.iris.probe;

import art.arcane.iris.pack.datapack.export.WorldgenDatapackExporter;
import art.arcane.iris.pack.datapack.export.compile.DimensionStage;
import art.arcane.iris.pack.datapack.export.compile.ImageMapStage;
import art.arcane.iris.pack.datapack.export.compile.PackCompiler;
import art.arcane.iris.pack.datapack.export.emit.WorldgenJsonWriter;
import art.arcane.iris.probe.fidelity.FidelityMetric;
import art.arcane.iris.probe.fidelity.FidelityReport;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class DatapackFidelityProbeTest {
    @ClassRule
    public static final PaperRegistryIsolation registryIsolation = new PaperRegistryIsolation();

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test(timeout = 300_000L)
    public void probeExportsTheSyntheticPackAndComparesBothGenerators() throws Exception {
        Path pack = pack(temporary);
        Path output = temporary.getRoot().toPath().resolve("reports").resolve("fidelity.json");

        FidelityReport report = DatapackFidelityProbe.execute(
                new String[]{pack.toString(), "main", "1337", "1", "8", output.toString(), "256", "32", "2", "full"}, exporter());

        JsonObject json = JsonParser.parseString(Files.readString(output, StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(report.toTable(), report.passed());
        assertTrue(json.get("passed").getAsBoolean());
        assertEquals(report.metrics().size(), json.getAsJsonArray("metrics").size());
        FidelityMetric biome = metric(report, "biome.share.plains");
        assertEquals(1.0D, biome.iris(), 0.0D);
        assertEquals(1.0D, biome.export(), 0.0D);
        assertTrue(biome.pass());
        assertEquals(0.0D, metric(report, "biome.totalVariation").delta(), 0.0D);
        assertEquals(1.0D, metric(report, "height.landFraction").export(), 0.0D);
        JsonObject columns = json.getAsJsonArray("sampling").get(0).getAsJsonObject();
        assertEquals("columns", columns.get("scope").getAsString());
        assertEquals(256, columns.get("windowBlocks").getAsInt());
        assertEquals(32, columns.get("strideBlocks").getAsInt());
        assertEquals(2, columns.getAsJsonArray("seeds").size());
        assertEquals(128L, columns.get("columns").getAsLong());
        JsonObject chunks = json.getAsJsonArray("sampling").get(1).getAsJsonObject();
        assertEquals("chunks", chunks.get("scope").getAsString());
        assertEquals(48, chunks.get("windowBlocks").getAsInt());
        assertTrue(RealPackProbeSupport.drainReported().isEmpty());
    }

    @Test
    public void malformedArgumentsAreRejectedBeforeThePackOpens() throws Exception {
        Path pack = pack(temporary);
        String output = temporary.getRoot().toPath().resolve("unused.json").toString();

        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> DatapackFidelityProbe.execute(new String[]{pack.toString(), "main"}, exporter()));
        IllegalArgumentException stride = assertThrows(IllegalArgumentException.class,
                () -> DatapackFidelityProbe.execute(new String[]{pack.toString(), "main", "1337", "1", "17", output, "256", "32", "1", "full"}, exporter()));
        IllegalArgumentException survey = assertThrows(IllegalArgumentException.class,
                () -> DatapackFidelityProbe.execute(new String[]{pack.toString(), "main", "1337", "1", "8", output, "250", "32", "1", "full"}, exporter()));
        IllegalArgumentException seeds = assertThrows(IllegalArgumentException.class,
                () -> DatapackFidelityProbe.execute(new String[]{pack.toString(), "main", "1337", "1", "8", output, "256", "32", "0", "full"}, exporter()));
        IllegalArgumentException mode = assertThrows(IllegalArgumentException.class,
                () -> DatapackFidelityProbe.execute(new String[]{pack.toString(), "main", "1337", "1", "8", output, "256", "32", "1", "caves"}, exporter()));

        assertTrue(missing.getMessage(), missing.getMessage().startsWith("Usage:"));
        assertTrue(stride.getMessage(), stride.getMessage().contains("stride"));
        assertTrue(survey.getMessage(), survey.getMessage().contains("multiple"));
        assertTrue(seeds.getMessage(), seeds.getMessage().contains("surveySeeds"));
        assertTrue(mode.getMessage(), mode.getMessage().contains("caves"));
        assertFalse(Files.exists(Path.of(output)));
    }

    static WorldgenDatapackExporter exporter() {
        return new WorldgenDatapackExporter(WorldgenJsonWriter::forTarget, new PackCompiler(List.of(new DimensionStage(), new ImageMapStage())));
    }

    static FidelityMetric metric(FidelityReport report, String name) {
        for (FidelityMetric metric : report.metrics()) {
            if (metric.name().equals(name)) {
                return metric;
            }
        }
        throw new AssertionError("Report has no metric " + name + ": " + report.toJson());
    }

    static Path pack(TemporaryFolder temporary) throws Exception {
        Path pack = temporary.newFolder().toPath();
        for (String directory : List.of("dimensions", "regions", "biomes", "generators")) {
            Files.createDirectories(pack.resolve(directory));
        }
        Files.writeString(pack.resolve("dimensions/main.json"), """
                {"name":"Fidelity","focus":"plains","regions":["owned"],"landChance":1,"carvingEnabled":false}
                """);
        Files.writeString(pack.resolve("regions/owned.json"), "{\"landBiomes\":[\"plains\"]}");
        Files.writeString(pack.resolve("generators/flat.json"), "{\"composite\":[]}");
        Files.writeString(pack.resolve("biomes/plains.json"), """
                {"derivative":"minecraft:plains","generators":[{"generator":"flat","min":0,"max":0}]}
                """);
        return pack;
    }
}
