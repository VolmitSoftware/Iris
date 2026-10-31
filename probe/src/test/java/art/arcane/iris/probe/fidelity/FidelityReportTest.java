package art.arcane.iris.probe.fidelity;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FidelityReportTest {
    @Test
    public void tableListsEveryMetricWithItsVerdict() {
        FidelityReport report = new FidelityReport(List.of(
                new FidelityMetric("biome.totalVariation", 0.0D, 0.03125D, 0.03125D, true),
                new FidelityMetric("height.p50", 64.0D, 80.0D, 0.041666D, false)), List.of(), List.of());

        List<String> lines = report.toTable().lines().toList();

        assertEquals(4, lines.size());
        assertTrue(lines.get(0), lines.get(0).startsWith("metric"));
        assertTrue(lines.get(1), lines.get(1).startsWith("biome.totalVariation"));
        assertTrue(lines.get(1), lines.get(1).contains("0.0313"));
        assertTrue(lines.get(1), lines.get(1).endsWith("PASS"));
        assertTrue(lines.get(2), lines.get(2).startsWith("height.p50"));
        assertTrue(lines.get(2), lines.get(2).contains("64.0000"));
        assertTrue(lines.get(2), lines.get(2).contains("80.0000"));
        assertTrue(lines.get(2), lines.get(2).endsWith("FAIL"));
        assertEquals("overall FAIL: 1 of 2 metrics passed", lines.get(3));
        assertEquals(lines.get(1).indexOf("PASS"), lines.get(2).indexOf("FAIL"));
    }

    @Test
    public void passingReportEndsWithOverallPass() {
        FidelityReport report = new FidelityReport(List.of(new FidelityMetric("height.p10", 40.0D, 40.0D, 0.0D, true)), List.of(), List.of());

        List<String> lines = report.toTable().lines().toList();

        assertEquals("overall PASS: 1 of 1 metrics passed", lines.getLast());
    }

    @Test
    public void observationsAreListedWithoutAffectingTheVerdict() {
        FidelityReport report = new FidelityReport(List.of(new FidelityMetric("height.p10", 40.0D, 40.0D, 0.0D, true)), List.of(),
                List.of(new FidelityObservation("cave.lavaShare.belowY-54", 0.01D, 0.5D)));

        List<String> lines = report.toTable().lines().toList();
        JsonObject json = JsonParser.parseString(report.toJson()).getAsJsonObject();

        assertTrue(report.passed());
        assertTrue(lines.toString(), lines.stream().anyMatch(line -> line.startsWith("cave.lavaShare.belowY-54") && line.endsWith("INFO")));
        assertEquals("overall PASS: 1 of 1 metrics passed", lines.getLast());
        JsonObject observation = json.getAsJsonArray("observations").get(0).getAsJsonObject();
        assertEquals("cave.lavaShare.belowY-54", observation.get("name").getAsString());
        assertEquals(0.5D, observation.get("export").getAsDouble(), 0.0D);
        assertFalse(observation.has("pass"));
    }

    @Test
    public void samplingIsListedInTheTableAndJson() {
        FidelityReport report = new FidelityReport(List.of(new FidelityMetric("height.p10", 40.0D, 40.0D, 0.0D, true)),
                List.of(new FidelitySampling("columns", 65536, 256, List.of(1337L, 1338L), 65536L)), List.of());

        List<String> lines = report.toTable().lines().toList();
        JsonObject sampling = JsonParser.parseString(report.toJson()).getAsJsonObject().getAsJsonArray("sampling").get(0).getAsJsonObject();

        assertEquals("sampling columns window=65536 stride=256 seeds=[1337, 1338] columns=65536", lines.getFirst());
        assertTrue(lines.get(1), lines.get(1).startsWith("metric"));
        assertEquals("columns", sampling.get("scope").getAsString());
        assertEquals(65536, sampling.get("windowBlocks").getAsInt());
        assertEquals(256, sampling.get("strideBlocks").getAsInt());
        assertEquals(2, sampling.getAsJsonArray("seeds").size());
        assertEquals(65536L, sampling.get("columns").getAsLong());
    }

    @Test
    public void emptyReportFailsInTheTable() {
        List<String> lines = new FidelityReport(List.of(), List.of(), List.of()).toTable().lines().toList();

        assertEquals("overall FAIL: no metrics were compared", lines.getLast());
    }
}
