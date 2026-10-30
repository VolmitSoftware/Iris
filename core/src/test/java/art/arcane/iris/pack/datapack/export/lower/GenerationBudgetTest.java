package art.arcane.iris.pack.datapack.export.lower;

import art.arcane.iris.pack.datapack.export.GenerationCost;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import org.junit.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class GenerationBudgetTest {
    @Test
    public void everyClimateRootHasTheClimateBudget() {
        for (GenerationCost.Root root : GenerationCost.Root.values()) {
            if (root.climate()) {
                assertEquals(root.id(), GenerationBudget.CLIMATE_WORST_CASE_NODES, GenerationBudget.worstCaseLimit(root).orElseThrow(), 0.0);
            }
        }
    }

    @Test
    public void climateRootOverBudgetIsReported() {
        Map<GenerationCost.Root, GenerationCost.RootCost> roots = new EnumMap<>(GenerationCost.Root.class);
        roots.put(GenerationCost.Root.TEMPERATURE, new GenerationCost.RootCost(4.2e9, 8.4e9, 1_200L));
        roots.put(GenerationCost.Root.VEGETATION, new GenerationCost.RootCost(GenerationBudget.CLIMATE_WORST_CASE_NODES, 1.0e9, 90L));

        List<ExportDiagnostic> diagnostics = GenerationBudget.check(new GenerationCost(roots, 0L), "overworld");

        assertEquals(1, diagnostics.size());
        ExportDiagnostic diagnostic = diagnostics.getFirst();
        assertSame(ExportLimit.GENERATION_COST, diagnostic.limit());
        assertSame(ExportDiagnostic.Severity.WARNING, diagnostic.severity());
        assertEquals("noise_settings/overworld/temperature", diagnostic.resource());
        assertTrue(diagnostic.message(), diagnostic.message().contains("temperature"));
        assertTrue(diagnostic.message(), diagnostic.message().contains("4.20e+09"));
        assertFalse(diagnostic.message(), diagnostic.message().contains("8.40e+09"));
        assertTrue(diagnostic.message(), diagnostic.message().contains("5.00e+05"));
    }

    @Test
    public void rootsWithoutABudgetAreNotReported() {
        Map<GenerationCost.Root, GenerationCost.RootCost> roots = new EnumMap<>(GenerationCost.Root.class);
        roots.put(GenerationCost.Root.AQUIFER_BARRIER, new GenerationCost.RootCost(1.0e12, 1.0e12, 10L));

        assertTrue(GenerationBudget.check(new GenerationCost(roots, 0L), "overworld").isEmpty());
        assertTrue(GenerationBudget.worstCaseLimit(GenerationCost.Root.AQUIFER_BARRIER).isEmpty());
        assertTrue(GenerationBudget.check(GenerationCost.NONE, "overworld").isEmpty());
    }

    @Test
    public void finalDensityOverTheDistinctNodeBudgetIsReported() {
        Map<GenerationCost.Root, GenerationCost.RootCost> roots = new EnumMap<>(GenerationCost.Root.class);
        roots.put(GenerationCost.Root.FINAL_DENSITY, new GenerationCost.RootCost(1.0e9, 1.0e10, 39_800L));

        List<ExportDiagnostic> diagnostics = GenerationBudget.check(new GenerationCost(roots, 0L), "overworld");

        assertEquals(diagnostics.toString(), 1, diagnostics.size());
        ExportDiagnostic diagnostic = diagnostics.getFirst();
        assertSame(ExportLimit.GENERATION_COST, diagnostic.limit());
        assertSame(ExportDiagnostic.Severity.WARNING, diagnostic.severity());
        assertEquals("noise_settings/overworld/final_density", diagnostic.resource());
        assertTrue(diagnostic.message(), diagnostic.message().contains("39800"));
        assertTrue(diagnostic.message(), diagnostic.message().contains("20000"));
    }

    @Test
    public void finalDensityAtTheDistinctNodeBudgetIsNotReported() {
        Map<GenerationCost.Root, GenerationCost.RootCost> roots = new EnumMap<>(GenerationCost.Root.class);
        roots.put(GenerationCost.Root.FINAL_DENSITY, new GenerationCost.RootCost(1.0e9, 1.0e10, GenerationBudget.FINAL_DENSITY_DISTINCT_NODES));

        assertTrue(GenerationBudget.check(new GenerationCost(roots, 0L), "overworld").isEmpty());
    }

    @Test
    public void densityFunctionJsonOverTheSizeBudgetIsReported() {
        long bytes = 60L * 1024L * 1024L;

        List<ExportDiagnostic> diagnostics = GenerationBudget.check(new GenerationCost(Map.of(), bytes), "overworld");

        assertEquals(diagnostics.toString(), 1, diagnostics.size());
        ExportDiagnostic diagnostic = diagnostics.getFirst();
        assertSame(ExportLimit.GENERATION_COST, diagnostic.limit());
        assertEquals("worldgen/density_function", diagnostic.resource());
        assertTrue(diagnostic.message(), diagnostic.message().contains("60.0 MB"));
        assertTrue(diagnostic.message(), diagnostic.message().contains("50.0 MB"));
    }

    @Test
    public void densityFunctionJsonAtTheSizeBudgetIsNotReported() {
        assertEquals(50L * 1024L * 1024L, GenerationBudget.DENSITY_FUNCTION_JSON_BYTES);
        assertTrue(GenerationBudget.check(new GenerationCost(Map.of(), GenerationBudget.DENSITY_FUNCTION_JSON_BYTES), "overworld").isEmpty());
    }

    @Test
    public void densityFunctionBytesCountOnlyDensityFunctionJson() {
        Map<String, byte[]> files = Map.of(
                "data/iris_export/worldgen/density_function/overworld/final_density.json", new byte[120],
                "data/iris_export/worldgen/density_function/height.json", new byte[30],
                "data/minecraft/worldgen/density_function/shift_x.json", new byte[7],
                "data/iris_export/worldgen/noise/height.json", new byte[500],
                "data/iris_export/worldgen/density_function/notes.txt", new byte[900]);

        assertEquals(157L, GenerationCost.densityFunctionBytes(files));
    }
}
