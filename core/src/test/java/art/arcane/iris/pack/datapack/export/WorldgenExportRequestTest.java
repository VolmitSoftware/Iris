package art.arcane.iris.pack.datapack.export;

import org.junit.Test;

import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class WorldgenExportRequestTest {
    private static final Path PACK = Path.of("packs", "overworld");
    private static final Path EXPORT_ROOT = Path.of("exports", "datapacks");

    @Test
    public void builderDefaults() {
        WorldgenExportRequest request = WorldgenExportRequest.builder()
                .pack(PACK)
                .dimension("overworld")
                .exportRoot(EXPORT_ROOT)
                .build();
        assertEquals(PACK, request.pack());
        assertEquals("overworld", request.dimension());
        assertEquals("", request.version());
        assertEquals("overworld", request.mode());
        assertEquals("iris_export", request.namespace());
        assertEquals("", request.output());
        assertEquals("zip", request.format());
        assertTrue(request.objects());
        assertFalse(request.replace());
        assertEquals(EXPORT_ROOT, request.exportRoot());
    }

    @Test
    public void builderOverridesEveryField() {
        WorldgenExportRequest request = WorldgenExportRequest.builder()
                .pack(PACK)
                .dimension(" overworld ")
                .version("26.3")
                .mode("dimension")
                .namespace("my_pack")
                .output("custom")
                .format("directory")
                .objects(false)
                .replace(true)
                .exportRoot(EXPORT_ROOT)
                .build();
        assertEquals("overworld", request.dimension());
        assertEquals("26.3", request.version());
        assertEquals("dimension", request.mode());
        assertEquals("my_pack", request.namespace());
        assertEquals("custom", request.output());
        assertEquals("directory", request.format());
        assertFalse(request.objects());
        assertTrue(request.replace());
    }

    @Test
    public void missingPackIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> WorldgenExportRequest.builder().dimension("overworld").exportRoot(EXPORT_ROOT).build());
        assertEquals("Worldgen export request requires a pack directory", error.getMessage());
    }

    @Test
    public void blankDimensionIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> WorldgenExportRequest.builder().pack(PACK).dimension("  ").exportRoot(EXPORT_ROOT).build());
        assertEquals("Worldgen export request requires a dimension", error.getMessage());
    }

    @Test
    public void missingExportRootIsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> WorldgenExportRequest.builder().pack(PACK).dimension("overworld").build());
        assertEquals("Worldgen export request requires an export root", error.getMessage());
    }
}
