package art.arcane.iris.probe;

import art.arcane.iris.probe.fidelity.ColumnSurvey;
import art.arcane.iris.probe.fidelity.FidelityChunk;
import art.arcane.iris.probe.fidelity.FidelityColumn;
import art.arcane.iris.probe.fidelity.FidelityStats;
import art.arcane.iris.probe.fidelity.IrisEngineColumnSource;
import art.arcane.iris.probe.fidelity.IrisEngineFidelitySource;
import art.arcane.iris.probe.fidelity.SurveyWindow;
import art.arcane.iris.testsupport.IrisRuntimeState;
import org.junit.AfterClass;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public final class IrisEngineColumnSourceTest {
    @AfterClass
    public static void releaseRuntime() {
        IrisRuntimeState.reset();
    }

    @Test(timeout = 180_000L)
    public void columnsMatchTheGeneratedChunkSurface() throws Exception {
        URL resource = IrisEngineColumnSourceTest.class.getResource("/seed-cohesion");
        assertNotNull(resource);
        File pack = new File(resource.toURI());
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(pack, "cohesion", "[fidelity]");
             RealPackProbeSupport.EngineSession session = workspace.openEngine(1337L, false, "fidelity");
             IrisEngineColumnSource columns = new IrisEngineColumnSource(session.engine());
             IrisEngineFidelitySource chunks = new IrisEngineFidelitySource(session.engine())) {
            for (int chunkX = -1; chunkX <= 0; chunkX++) {
                for (int chunkZ = -1; chunkZ <= 0; chunkZ++) {
                    assertChunkMatches(columns, chunks, chunkX, chunkZ);
                }
            }
            FidelityStats stats = ColumnSurvey.scan(columns, new SurveyWindow(0, 0, 64, 8), 2);

            assertEquals(64L, stats.columns());
            assertEquals(64L, stats.heightColumns());
            assertEquals(0L, stats.fluidColumns());
            assertEquals(Map.of("terrain", 64L), stats.biomeColumns());
        }
    }

    private static void assertChunkMatches(IrisEngineColumnSource columns, IrisEngineFidelitySource chunks, int chunkX, int chunkZ) throws Exception {
        FidelityChunk chunk = chunks.sample(chunkX, chunkZ);
        for (int x = 0; x < 16; x += 3) {
            for (int z = 0; z < 16; z += 3) {
                FidelityColumn column = columns.column((chunkX << 4) + x, (chunkZ << 4) + z);
                int surface = chunk.surfaceY(x, z);
                assertEquals("column " + x + "," + z + " of chunk " + chunkX + "," + chunkZ, surface, column.terrainY());
                assertEquals(chunk.surfaceBiome(x, z), column.biome());
                assertFalse(column.wet());
            }
        }
    }
}
