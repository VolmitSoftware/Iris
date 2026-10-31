package art.arcane.iris.probe.fidelity;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class BiomeShareReportTest {
    @Test
    public void tableListsEverySeedDeltaWithThePassingCount() {
        BiomeShareReport report = report();

        List<String> lines = report.toTable().lines().toList();

        assertTrue(lines.toString(), lines.contains(String.format(Locale.ROOT, "%-18s %10s %10s %8s", "seed", "1337", "1338", "passing")));
        assertTrue(lines.toString(), lines.contains(String.format(Locale.ROOT, "%-18s %10.4f %10.4f %8s", "biome.share.forest", 0.5D, 0.05D, "1/2")));
        assertTrue(lines.toString(), lines.contains(String.format(Locale.ROOT, "%-18s %10s %10.4f %8s", "biome.share.desert", "-", 0.0D, "1/1")));
        assertEquals("overall FAIL: 0 of 1 metrics passed", lines.getLast());
        assertFalse(report.passed());
    }

    @Test
    public void jsonCarriesTheMeanAndEverySeed() {
        JsonObject json = JsonParser.parseString(report().toJson()).getAsJsonObject();

        assertFalse(json.get("passed").getAsBoolean());
        JsonObject mean = json.getAsJsonObject("mean");
        assertEquals("biome.share.forest", mean.getAsJsonArray("metrics").get(0).getAsJsonObject().get("name").getAsString());
        JsonArray seeds = json.getAsJsonArray("seeds");
        assertEquals(2, seeds.size());
        assertEquals(1337L, seeds.get(0).getAsJsonObject().get("seed").getAsLong());
        assertEquals(1, seeds.get(0).getAsJsonObject().getAsJsonArray("metrics").size());
        JsonObject passing = json.getAsJsonObject("seedsPassing").getAsJsonObject("biome.share.forest");
        assertEquals(1, passing.get("passing").getAsInt());
        assertEquals(2, passing.get("compared").getAsInt());
        JsonObject desert = json.getAsJsonObject("shares").getAsJsonObject("desert");
        assertEquals(0.05D, desert.get("iris").getAsDouble(), 0.0D);
        assertEquals(0.01D, desert.get("export").getAsDouble(), 0.0D);
    }

    @Test
    public void reportNeedsAtLeastOneSeed() {
        FidelityReport mean = new FidelityReport(List.of(), List.of(), List.of());

        assertThrows(IllegalArgumentException.class, () -> new BiomeShareReport(mean, new TreeMap<>(), Map.of(), Map.of()));
    }

    private static BiomeShareReport report() {
        FidelityReport mean = new FidelityReport(List.of(new FidelityMetric("biome.share.forest", 0.5D, 0.4D, 0.2D, false)), List.of(), List.of());
        SortedMap<Long, FidelityReport> seeds = new TreeMap<>();
        seeds.put(1337L, new FidelityReport(List.of(new FidelityMetric("biome.share.forest", 0.5D, 0.25D, 0.5D, false)), List.of(), List.of()));
        seeds.put(1338L, new FidelityReport(List.of(
                new FidelityMetric("biome.share.forest", 0.5D, 0.525D, 0.05D, true),
                new FidelityMetric("biome.share.desert", 0.1D, 0.1D, 0.0D, true)), List.of(), List.of()));
        return new BiomeShareReport(mean, seeds, Map.of("forest", 0.5D, "desert", 0.05D), Map.of("forest", 0.4D, "desert", 0.01D));
    }
}
