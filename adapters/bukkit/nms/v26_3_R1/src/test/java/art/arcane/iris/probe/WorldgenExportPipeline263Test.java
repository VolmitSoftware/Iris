package art.arcane.iris.probe;

import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.WorldgenDatapackExporter;
import art.arcane.iris.pack.datapack.export.WorldgenExportMode;
import art.arcane.iris.pack.datapack.export.WorldgenExportProgress;
import art.arcane.iris.pack.datapack.export.WorldgenExportRequest;
import art.arcane.iris.pack.datapack.export.WorldgenExportResult;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class WorldgenExportPipeline263Test {
    @ClassRule
    public static final PaperRegistryIsolation registryIsolation = new PaperRegistryIsolation();

    private static final long SEED = 1337L;
    private static final String DIMENSION_ID = WorldgenExportRequest.DEFAULT_NAMESPACE + ":" + ExportTestPacks.DIMENSION;
    private static final int[] SAMPLE_Y = {-64, 0, 64, 200};
    private static final int LATTICE = 8;
    private static final int LATTICE_STEP = 61;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExportTestPacks.BoundPlatform platform = new ExportTestPacks.BoundPlatform();

    @Test
    public void exportedPackLoadsAndSamples() throws IOException {
        File pack = ExportTestPacks.basic(temporaryFolder.newFolder("pack"));
        Path root = temporaryFolder.newFolder("exports").toPath();
        WorldgenExportRequest request = WorldgenExportRequest.builder()
                .pack(pack.toPath())
                .dimension(ExportTestPacks.DIMENSION)
                .exportRoot(root)
                .format("directory")
                .build();

        WorldgenExportResult result = WorldgenDatapackExporter.create().export(request, WorldgenExportProgress.NONE);

        assertTrue(result.message() + " " + result.diagnostics(), result.isSuccess());
        try (WorldgenExportSampler sampler = WorldgenExportSampler.open(result.output(), WorldgenExportMode.OVERWORLD, DIMENSION_ID, SEED)) {
            assertFalse(sampler.densityFunctionIds().isEmpty());
            for (String id : sampler.densityFunctionIds()) {
                for (int y : SAMPLE_Y) {
                    for (int xIndex = 0; xIndex < LATTICE; xIndex++) {
                        for (int zIndex = 0; zIndex < LATTICE; zIndex++) {
                            int x = (xIndex - LATTICE / 2) * LATTICE_STEP;
                            int z = (zIndex - LATTICE / 2) * LATTICE_STEP;
                            float value = sampler.density(id, x, y, z);
                            assertTrue(id + " at " + x + "," + y + "," + z + " = " + value, Float.isFinite(value));
                        }
                    }
                }
            }
            for (int xIndex = 0; xIndex < LATTICE; xIndex++) {
                for (int zIndex = 0; zIndex < LATTICE; zIndex++) {
                    int x = (xIndex - LATTICE / 2) * LATTICE_STEP;
                    int z = (zIndex - LATTICE / 2) * LATTICE_STEP;
                    int height = sampler.baseHeight(x, z);
                    assertTrue("height " + height + " at " + x + "," + z, height >= sampler.minY() && height <= sampler.minY() + sampler.height());
                }
            }
        }
    }
}
