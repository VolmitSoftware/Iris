package art.arcane.iris.probe.fidelity;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

public record BiomeShareReport(FidelityReport mean, SortedMap<Long, FidelityReport> seeds, Map<String, Double> irisShares,
                               Map<String, Double> exportShares) {
    private static final String SEED_HEADER = "seed";
    private static final String PASSING_HEADER = "passing";
    private static final String MISSING = "-";

    public BiomeShareReport {
        Objects.requireNonNull(mean, "mean");
        seeds = Collections.unmodifiableSortedMap(new TreeMap<>(seeds));
        if (seeds.isEmpty()) {
            throw new IllegalArgumentException("A biome share report needs at least one seed");
        }
        irisShares = Collections.unmodifiableSortedMap(new TreeMap<>(irisShares));
        exportShares = Collections.unmodifiableSortedMap(new TreeMap<>(exportShares));
    }

    public boolean passed() {
        return mean.passed();
    }

    public int seedsPassing(String metric) {
        int passing = 0;
        for (FidelityReport report : seeds.values()) {
            FidelityMetric found = find(report, metric);
            if (found != null && found.pass()) {
                passing++;
            }
        }
        return passing;
    }

    public int seedsCompared(String metric) {
        int compared = 0;
        for (FidelityReport report : seeds.values()) {
            if (find(report, metric) != null) {
                compared++;
            }
        }
        return compared;
    }

    public String toJson() {
        JsonArray seedEntries = new JsonArray(seeds.size());
        for (Map.Entry<Long, FidelityReport> entry : seeds.entrySet()) {
            JsonObject seed = entry.getValue().toJsonObject();
            seed.addProperty("seed", entry.getKey());
            seedEntries.add(seed);
        }
        JsonObject passing = new JsonObject();
        for (String metric : metricNames()) {
            JsonObject counts = new JsonObject();
            counts.addProperty("passing", seedsPassing(metric));
            counts.addProperty("compared", seedsCompared(metric));
            passing.add(metric, counts);
        }
        Set<String> biomes = new TreeSet<>(irisShares.keySet());
        biomes.addAll(exportShares.keySet());
        JsonObject shares = new JsonObject();
        for (String biome : biomes) {
            JsonObject share = new JsonObject();
            share.addProperty("iris", irisShares.getOrDefault(biome, 0.0D));
            share.addProperty("export", exportShares.getOrDefault(biome, 0.0D));
            shares.add(biome, share);
        }
        JsonObject root = new JsonObject();
        root.addProperty("passed", passed());
        root.add("mean", mean.toJsonObject());
        root.add("shares", shares);
        root.add("seedsPassing", passing);
        root.add("seeds", seedEntries);
        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    public String toTable() {
        List<String> names = metricNames();
        int width = SEED_HEADER.length();
        for (String name : names) {
            width = Math.max(width, name.length());
        }
        StringBuilder table = new StringBuilder();
        table.append(String.format(Locale.ROOT, "%-" + width + "s", SEED_HEADER));
        for (long seed : seeds.keySet()) {
            table.append(String.format(Locale.ROOT, " %10s", seed));
        }
        table.append(String.format(Locale.ROOT, " %8s\n", PASSING_HEADER));
        for (String name : names) {
            table.append(String.format(Locale.ROOT, "%-" + width + "s", name));
            for (FidelityReport report : seeds.values()) {
                FidelityMetric metric = find(report, name);
                table.append(metric == null ? String.format(Locale.ROOT, " %10s", MISSING) : String.format(Locale.ROOT, " %10.4f", metric.delta()));
            }
            table.append(String.format(Locale.ROOT, " %8s\n", seedsPassing(name) + "/" + seedsCompared(name)));
        }
        return table.append(mean.toTable()).toString();
    }

    private List<String> metricNames() {
        Set<String> names = new LinkedHashSet<>();
        for (FidelityMetric metric : mean.metrics()) {
            names.add(metric.name());
        }
        for (FidelityReport report : seeds.values()) {
            for (FidelityMetric metric : report.metrics()) {
                names.add(metric.name());
            }
        }
        return new ArrayList<>(names);
    }

    private static FidelityMetric find(FidelityReport report, String name) {
        for (FidelityMetric metric : report.metrics()) {
            if (metric.name().equals(name)) {
                return metric;
            }
        }
        return null;
    }
}
