package art.arcane.iris.pack.datapack.export;

import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import org.junit.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class WorldgenExportResultTest {
    private static final Path OUTPUT = Path.of("exports", "datapacks", "overworld-26.3.zip");

    @Test
    public void exportedResultCarriesOutputAndDiagnostics() {
        List<ExportDiagnostic> diagnostics = new ArrayList<>();
        diagnostics.add(new ExportDiagnostic(ExportDiagnostic.Severity.WARNING, ExportLimit.IMAGE_MAP, "overworld", "Image map layer dropped"));
        Map<String, String> loadKeys = new HashMap<>();
        loadKeys.put("iris_export:plains", "Plains");
        ContentTrim trim = new ContentTrim(12, 4096L, 9, 3000L);
        Map<GenerationCost.Root, GenerationCost.RootCost> roots = new EnumMap<>(GenerationCost.Root.class);
        roots.put(GenerationCost.Root.TEMPERATURE, new GenerationCost.RootCost(90.0, 120.0, 40L));
        GenerationCost cost = new GenerationCost(roots, 4_096L);
        roots.clear();
        WorldgenExportResult result = WorldgenExportResult.exported(OUTPUT, diagnostics, loadKeys, trim, cost);
        diagnostics.clear();
        loadKeys.clear();
        assertSame(WorldgenExportResult.Status.EXPORTED, result.status());
        assertTrue(result.isSuccess());
        assertEquals(OUTPUT, result.output());
        assertEquals("", result.message());
        assertEquals(1, result.diagnostics().size());
        assertEquals(Map.of("iris_export:plains", "Plains"), result.loadKeyByBiomeId());
        assertSame(trim, result.trim());
        assertEquals(3, result.trim().filesRemoved());
        assertEquals(1096L, result.trim().bytesRemoved());
        assertEquals(120.0, result.cost().root(GenerationCost.Root.TEMPERATURE).orElseThrow().expandedNodes(), 0.0);
        assertTrue(result.cost().root(GenerationCost.Root.DEPTH).isEmpty());
        assertEquals(4_096L, result.cost().densityFunctionBytes());
    }

    @Test
    public void exportedResultRequiresOutput() {
        assertThrows(NullPointerException.class, () -> WorldgenExportResult.exported(null, List.of(), Map.of(), ContentTrim.NONE, GenerationCost.NONE));
    }

    @Test
    public void contentTrimCannotGrow() {
        assertThrows(IllegalArgumentException.class, () -> new ContentTrim(1, 10L, 2, 10L));
        assertThrows(IllegalArgumentException.class, () -> new ContentTrim(1, 10L, 1, 11L));
        assertThrows(IllegalArgumentException.class, () -> new ContentTrim(-1, 0L, -1, 0L));
    }

    @Test
    public void rejectedResultMayOmitOutput() {
        WorldgenExportResult result = WorldgenExportResult.rejected(null, "Pack is invalid", List.of());
        assertSame(WorldgenExportResult.Status.REJECTED, result.status());
        assertFalse(result.isSuccess());
        assertNull(result.output());
        assertEquals("Pack is invalid", result.message());
        assertTrue(result.loadKeyByBiomeId().isEmpty());
        assertSame(ContentTrim.NONE, result.trim());
        assertSame(GenerationCost.NONE, result.cost());
    }

    @Test
    public void failedResultKeepsMessage() {
        WorldgenExportResult result = WorldgenExportResult.failed(OUTPUT, "Publication failed", List.of());
        assertSame(WorldgenExportResult.Status.FAILED, result.status());
        assertEquals("Publication failed", result.message());
        assertSame(ContentTrim.NONE, result.trim());
    }

    @Test
    public void noneProgressIgnoresUpdates() {
        WorldgenExportProgress.NONE.stage("compile");
        WorldgenExportProgress.NONE.resources(1, 2);
    }
}
