package art.arcane.iris.pack.datapack.export.lower;

import art.arcane.iris.pack.datapack.export.GenerationCost;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;

public final class GenerationBudget {
    public static final double CLIMATE_WORST_CASE_NODES = 5.0e5;
    public static final long FINAL_DENSITY_DISTINCT_NODES = 20_000L;
    public static final long DENSITY_FUNCTION_JSON_BYTES = 50L * 1024L * 1024L;
    private static final double MEGABYTE = 1024.0 * 1024.0;
    private static final String DENSITY_FUNCTION_RESOURCE = "worldgen/density_function";
    private static final Map<GenerationCost.Root, Double> WORST_CASE_LIMITS = worstCaseLimits();

    private GenerationBudget() {
    }

    public static OptionalDouble worstCaseLimit(GenerationCost.Root root) {
        Double limit = WORST_CASE_LIMITS.get(root);
        return limit == null ? OptionalDouble.empty() : OptionalDouble.of(limit);
    }

    public static List<ExportDiagnostic> check(GenerationCost cost, String dimension) {
        List<ExportDiagnostic> diagnostics = new ArrayList<>();
        checkWorstCase(cost, dimension, diagnostics);
        checkFinalDensity(cost, dimension, diagnostics);
        checkDensityFunctionJson(cost, diagnostics);
        return diagnostics;
    }

    private static void checkWorstCase(GenerationCost cost, String dimension, List<ExportDiagnostic> diagnostics) {
        for (Map.Entry<GenerationCost.Root, GenerationCost.RootCost> entry : cost.roots().entrySet()) {
            Double limit = WORST_CASE_LIMITS.get(entry.getKey());
            double nodes = entry.getValue().worstCaseNodes();
            if (limit == null || nodes <= limit) {
                continue;
            }
            String root = entry.getKey().id();
            diagnostics.add(warning(rootResource(dimension, entry.getKey()),
                    String.format(Locale.ROOT, "Router root '%s' evaluates up to %.2e density function nodes per uncached lookup, over the budget of %.2e;"
                            + " uncached biome lookups, carver biomes, structure placement and /locate slow down in proportion", root, nodes, limit)));
        }
    }

    private static void checkFinalDensity(GenerationCost cost, String dimension, List<ExportDiagnostic> diagnostics) {
        GenerationCost.RootCost finalDensity = cost.roots().get(GenerationCost.Root.FINAL_DENSITY);
        if (finalDensity == null || finalDensity.distinctNodes() <= FINAL_DENSITY_DISTINCT_NODES) {
            return;
        }
        diagnostics.add(warning(rootResource(dimension, GenerationCost.Root.FINAL_DENSITY),
                String.format(Locale.ROOT, "The final density has %d distinct density function nodes, over the budget of %d;"
                        + " noise fill, material rules and getBaseHeight slow down in proportion", finalDensity.distinctNodes(), FINAL_DENSITY_DISTINCT_NODES)));
    }

    private static void checkDensityFunctionJson(GenerationCost cost, List<ExportDiagnostic> diagnostics) {
        if (cost.densityFunctionBytes() <= DENSITY_FUNCTION_JSON_BYTES) {
            return;
        }
        diagnostics.add(warning(DENSITY_FUNCTION_RESOURCE,
                String.format(Locale.ROOT, "Density function JSON totals %.1f MB, over the budget of %.1f MB; registry load time and memory grow in proportion",
                        cost.densityFunctionBytes() / MEGABYTE, DENSITY_FUNCTION_JSON_BYTES / MEGABYTE)));
    }

    private static String rootResource(String dimension, GenerationCost.Root root) {
        return "noise_settings/" + dimension + "/" + root.id();
    }

    private static ExportDiagnostic warning(String resource, String message) {
        return new ExportDiagnostic(ExportDiagnostic.Severity.WARNING, ExportLimit.GENERATION_COST, resource, message);
    }

    private static Map<GenerationCost.Root, Double> worstCaseLimits() {
        Map<GenerationCost.Root, Double> limits = new EnumMap<>(GenerationCost.Root.class);
        for (GenerationCost.Root root : GenerationCost.Root.values()) {
            if (root.climate()) {
                limits.put(root, CLIMATE_WORST_CASE_NODES);
            }
        }
        return Collections.unmodifiableMap(limits);
    }
}
