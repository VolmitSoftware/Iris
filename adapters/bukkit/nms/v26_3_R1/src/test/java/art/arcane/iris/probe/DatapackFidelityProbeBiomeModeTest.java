package art.arcane.iris.probe;

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
import static org.junit.Assert.assertTrue;

public final class DatapackFidelityProbeBiomeModeTest {
    @ClassRule
    public static final PaperRegistryIsolation registryIsolation = new PaperRegistryIsolation();

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test(timeout = 300_000L)
    public void biomeModeJudgesTheSeedMeanWithoutTheChunkScan() throws Exception {
        Path pack = DatapackFidelityProbeTest.pack(temporary);
        Path output = temporary.getRoot().toPath().resolve("reports").resolve("biomes.json");

        FidelityReport report = DatapackFidelityProbe.execute(
                new String[]{pack.toString(), "main", "1337", "0", "1", output.toString(), "256", "32", "3", "biomes"}, DatapackFidelityProbeTest.exporter());

        JsonObject json = JsonParser.parseString(Files.readString(output, StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(report.toTable(), report.passed());
        assertTrue(json.get("passed").getAsBoolean());
        assertEquals(1.0D, DatapackFidelityProbeTest.metric(report, "biome.share.plains").export(), 0.0D);
        assertEquals(1, report.sampling().size());
        assertEquals(List.of(1337L, 1338L, 1339L), report.sampling().getFirst().seeds());
        assertEquals(3, json.getAsJsonArray("seeds").size());
        assertEquals(3, json.getAsJsonObject("seedsPassing").getAsJsonObject("biome.share.plains").get("passing").getAsInt());
        assertTrue(report.observations().stream().anyMatch(observation -> observation.name().equals("biome.type.land")
                && observation.iris() == 1.0D && observation.export() == 1.0D));
        assertTrue(RealPackProbeSupport.drainReported().isEmpty());
    }
}
