package art.arcane.iris.probe;

import art.arcane.iris.probe.fidelity.FidelityChunk;
import art.arcane.iris.probe.fidelity.FidelityComparison;
import art.arcane.iris.probe.fidelity.FidelityReport;
import art.arcane.iris.probe.fidelity.FidelitySampling;
import art.arcane.iris.probe.fidelity.FidelitySamples;
import art.arcane.iris.probe.fidelity.FidelityScanner;
import art.arcane.iris.probe.fidelity.FidelityStats;
import art.arcane.iris.probe.fidelity.FidelityThresholds;
import art.arcane.iris.probe.fidelity.IrisEngineFidelitySource;
import art.arcane.iris.testsupport.IrisRuntimeState;
import org.junit.AfterClass;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class IrisEngineFidelitySourceTest {
    @AfterClass
    public static void releaseRuntime() {
        IrisRuntimeState.reset();
    }

    @Test(timeout = 180_000L)
    public void realPackChunksScanIntoComparableStats() throws Exception {
        URL resource = IrisEngineFidelitySourceTest.class.getResource("/seed-cohesion");
        assertNotNull(resource);
        File pack = new File(resource.toURI());
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(pack, "cohesion", "[fidelity]");
             RealPackProbeSupport.EngineSession session = workspace.openEngine(1337L, false, "fidelity");
             IrisEngineFidelitySource source = new IrisEngineFidelitySource(session.engine())) {
            FidelityStats stats = FidelityScanner.scan(source, 0, 0, 1, 4, 2);

            assertEquals(0, source.minY());
            assertEquals(64, source.maxY());
            assertEquals(144L, stats.columns());
            assertEquals(144L, stats.heightColumns());
            assertEquals(Map.of("terrain", 144L), stats.biomeColumns());
            assertEquals(144L, stats.blockColumns());
            assertTrue(stats.wallSolidVoxels() > 0L);
            assertTrue(stats.wallVoxels() <= stats.wallSolidVoxels());
            double median = stats.heightQuantile(0.5D);
            assertTrue("median " + median, median >= 20.0D && median < 40.0D);
            FidelitySamples samples = new FidelitySamples(stats, stats, new FidelitySampling("chunks", 48, 4, List.of(1337L), stats.columns()));
            FidelityReport report = FidelityComparison.compare(samples, samples, FidelityThresholds.defaults());
            assertTrue(report.toJson(), report.passed());
        }
    }

    @Test(timeout = 180_000L)
    public void volumetricOverhangsAreTerrainVoidsAndRockIsTerrainMass() throws Exception {
        URL resource = IrisEngineFidelitySourceTest.class.getResource("/terrain3d-pack");
        assertNotNull(resource);
        File pack = new File(resource.toURI());
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(pack, "terrain3d", "[fidelity]");
             RealPackProbeSupport.EngineSession session = workspace.openEngine(1337L, false, "fidelity-voids");
             IrisEngineFidelitySource source = new IrisEngineFidelitySource(session.engine())) {
            FidelityChunk chunk = source.sample(0, 0);
            long voids = 0L;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = source.minY(); y < chunk.surfaceY(x, z); y++) {
                        boolean air = chunk.block(x, y, z).endsWith("air");
                        assertEquals(x + "," + y + "," + z, !air, chunk.terrainMass(x, y, z));
                        if (air) {
                            voids++;
                        }
                    }
                }
            }
            assertTrue(voids > 0L);
        }
    }
}
