package art.arcane.iris.pack.datapack.export.ir;

import com.google.gson.JsonObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class DimensionDefTest {
    @Test
    public void unalignedMinYIsAlignedDown() {
        List<ExportDiagnostic> diagnostics = new ArrayList<>();
        DimensionDef dimension = aligned(-60, 384, 384, diagnostics);
        assertEquals(-64, dimension.minY());
        assertEquals(400, dimension.height());
        assertEquals(400, dimension.logicalHeight());
        assertEquals(1, diagnostics.size());
        ExportDiagnostic diagnostic = diagnostics.getFirst();
        assertSame(ExportLimit.DIMENSION_HEIGHT, diagnostic.limit());
        assertSame(ExportDiagnostic.Severity.WARNING, diagnostic.severity());
        assertEquals("overworld", diagnostic.resource());
    }

    @Test
    public void alignedDimensionHasNoDiagnostic() {
        List<ExportDiagnostic> diagnostics = new ArrayList<>();
        DimensionDef dimension = aligned(-64, 384, 256, diagnostics);
        assertEquals(-64, dimension.minY());
        assertEquals(384, dimension.height());
        assertEquals(256, dimension.logicalHeight());
        assertTrue(diagnostics.isEmpty());
    }

    @Test
    public void unalignedHeightRoundsUp() {
        List<ExportDiagnostic> diagnostics = new ArrayList<>();
        DimensionDef dimension = aligned(0, 250, 128, diagnostics);
        assertEquals(0, dimension.minY());
        assertEquals(256, dimension.height());
        assertEquals(128, dimension.logicalHeight());
        assertEquals(1, diagnostics.size());
    }

    @Test
    public void heightIsClampedTo4064() {
        List<ExportDiagnostic> diagnostics = new ArrayList<>();
        DimensionDef dimension = aligned(-2032, 5000, 5000, diagnostics);
        assertEquals(-2032, dimension.minY());
        assertEquals(4064, dimension.height());
        assertEquals(4064, dimension.logicalHeight());
        assertEquals(1, diagnostics.size());
    }

    @Test
    public void topIsClampedTo2032() {
        List<ExportDiagnostic> diagnostics = new ArrayList<>();
        DimensionDef dimension = aligned(0, 3000, 3000, diagnostics);
        assertEquals(0, dimension.minY());
        assertEquals(2032, dimension.height());
        assertTrue(dimension.minY() + dimension.height() <= 2032);
        assertEquals(1, diagnostics.size());
    }

    @Test
    public void minYBelowLimitIsRaised() {
        List<ExportDiagnostic> diagnostics = new ArrayList<>();
        DimensionDef dimension = aligned(-2100, 512, 512, diagnostics);
        assertEquals(-2032, dimension.minY());
        assertEquals(448, dimension.height());
        assertEquals(1, diagnostics.size());
    }

    @Test
    public void logicalHeightIsCappedAtHeight() {
        List<ExportDiagnostic> diagnostics = new ArrayList<>();
        DimensionDef dimension = aligned(-64, 384, 1000, diagnostics);
        assertEquals(384, dimension.logicalHeight());
        assertEquals(1, diagnostics.size());
    }

    @Test
    public void canonicalConstructorRejectsUnrepresentableHeights() {
        assertThrows(IllegalArgumentException.class, () -> create(-60, 384, 384));
        assertThrows(IllegalArgumentException.class, () -> create(-64, 380, 380));
        assertThrows(IllegalArgumentException.class, () -> create(0, 4080, 4080));
        assertThrows(IllegalArgumentException.class, () -> create(1024, 1024, 1024));
        assertThrows(IllegalArgumentException.class, () -> create(-64, 384, 400));
    }

    @Test
    public void dimensionTypeIsCopied() {
        JsonObject type = new JsonObject();
        type.addProperty("has_skylight", true);
        DimensionDef dimension = new DimensionDef("overworld", -64, 384, 384, 63, "minecraft:stone", "minecraft:water", type);
        type.addProperty("has_ceiling", false);
        assertNotSame(type, dimension.dimensionType());
        assertEquals(1, dimension.dimensionType().size());
    }

    private static DimensionDef aligned(int minY, int height, int logicalHeight, List<ExportDiagnostic> diagnostics) {
        return DimensionDef.aligned("overworld", minY, height, logicalHeight, 63, "minecraft:stone", "minecraft:water", new JsonObject(), diagnostics);
    }

    private static DimensionDef create(int minY, int height, int logicalHeight) {
        return new DimensionDef("overworld", minY, height, logicalHeight, 63, "minecraft:stone", "minecraft:water", new JsonObject());
    }
}
