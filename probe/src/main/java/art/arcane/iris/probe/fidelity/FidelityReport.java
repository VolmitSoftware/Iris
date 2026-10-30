package art.arcane.iris.probe.fidelity;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Locale;

public record FidelityReport(List<FidelityMetric> metrics, List<FidelitySampling> sampling, List<FidelityObservation> observations) {
    private static final String NAME_HEADER = "metric";
    private static final String OBSERVATION_VERDICT = "INFO";

    public FidelityReport {
        metrics = List.copyOf(metrics);
        sampling = List.copyOf(sampling);
        observations = List.copyOf(observations);
    }

    public boolean passed() {
        if (metrics.isEmpty()) {
            return false;
        }
        for (FidelityMetric metric : metrics) {
            if (!metric.pass()) {
                return false;
            }
        }
        return true;
    }

    public String toJson() {
        return new GsonBuilder().setPrettyPrinting().create().toJson(toJsonObject());
    }

    public JsonObject toJsonObject() {
        JsonArray entries = new JsonArray(metrics.size());
        for (FidelityMetric metric : metrics) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", metric.name());
            entry.addProperty("iris", metric.iris());
            entry.addProperty("export", metric.export());
            entry.addProperty("delta", metric.delta());
            entry.addProperty("pass", metric.pass());
            entries.add(entry);
        }
        JsonArray samplingEntries = new JsonArray(sampling.size());
        for (FidelitySampling scope : sampling) {
            JsonObject entry = new JsonObject();
            entry.addProperty("scope", scope.scope());
            entry.addProperty("windowBlocks", scope.windowBlocks());
            entry.addProperty("strideBlocks", scope.strideBlocks());
            JsonArray seeds = new JsonArray(scope.seeds().size());
            for (long seed : scope.seeds()) {
                seeds.add(seed);
            }
            entry.add("seeds", seeds);
            entry.addProperty("columns", scope.columns());
            samplingEntries.add(entry);
        }
        JsonArray notes = new JsonArray(observations.size());
        for (FidelityObservation observation : observations) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", observation.name());
            entry.addProperty("iris", observation.iris());
            entry.addProperty("export", observation.export());
            notes.add(entry);
        }
        JsonObject root = new JsonObject();
        root.addProperty("passed", passed());
        root.add("sampling", samplingEntries);
        root.add("metrics", entries);
        root.add("observations", notes);
        return root;
    }

    public String toTable() {
        int width = NAME_HEADER.length();
        for (FidelityMetric metric : metrics) {
            width = Math.max(width, metric.name().length());
        }
        for (FidelityObservation observation : observations) {
            width = Math.max(width, observation.name().length());
        }
        String header = "%-" + width + "s %12s %12s %10s  %s\n";
        String row = "%-" + width + "s %12.4f %12.4f %10.4f  %s\n";
        StringBuilder table = new StringBuilder();
        for (FidelitySampling scope : sampling) {
            table.append(String.format(Locale.ROOT, "sampling %s window=%d stride=%d seeds=%s columns=%d\n", scope.scope(),
                    scope.windowBlocks(), scope.strideBlocks(), scope.seeds(), scope.columns()));
        }
        table.append(String.format(Locale.ROOT, header, NAME_HEADER, "iris", "export", "delta", "result"));
        int passing = 0;
        for (FidelityMetric metric : metrics) {
            if (metric.pass()) {
                passing++;
            }
            table.append(String.format(Locale.ROOT, row, metric.name(), metric.iris(), metric.export(), metric.delta(),
                    metric.pass() ? "PASS" : "FAIL"));
        }
        String note = "%-" + width + "s %12.4f %12.4f %10s  %s\n";
        for (FidelityObservation observation : observations) {
            table.append(String.format(Locale.ROOT, note, observation.name(), observation.iris(), observation.export(), "", OBSERVATION_VERDICT));
        }
        if (metrics.isEmpty()) {
            return table.append("overall FAIL: no metrics were compared\n").toString();
        }
        return table.append("overall ").append(passed() ? "PASS" : "FAIL").append(": ").append(passing).append(" of ")
                .append(metrics.size()).append(" metrics passed\n").toString();
    }
}
