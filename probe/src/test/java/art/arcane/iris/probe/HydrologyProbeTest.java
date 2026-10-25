package art.arcane.iris.probe;

import art.arcane.iris.generation.hydrology.plan.WindowKey;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HydrologyProbeTest {
    private static final HydrologyWindows.WindowRange LAND = new HydrologyWindows.WindowRange(4, 4, 4, 4);
    private static final List<String> METRICS = List.of("coldFirstChunkMs", "windowCpuMs", "regionCpuMs", "maxDependencyDepth",
            "riversPerTile", "streamBlocksPerTile", "pondsPerTile", "pondLevelsDistinct", "pondsPerched8", "containmentLeaks",
            "risingWaterSegments", "seamMismatches", "tickConversionCells", "missingSurface", "bermOverCap", "inlets", "inletFailures",
            "cavePools", "cavePoolFailures");

    @Test
    public void reportsAllMetricFields() throws Exception {
        try (SyntheticHydrology fixture = new SyntheticHydrology()) {
            List<String> lines = HydrologyProbe.measure(fixture.inputs(), LAND).lines();
            Map<String, String> window = fields(line(lines, "IRIS_HYDROLOGY_WINDOW"));
            Map<String, String> total = fields(line(lines, "IRIS_HYDROLOGY_TOTAL"));
            assertEquals("4", window.get("wx"));
            assertEquals("4", window.get("wz"));
            for (String metric : METRICS) {
                assertNumber(metric, window.get(metric));
                assertNumber(metric, total.get(metric));
            }
            assertEquals("1", total.get("windows"));
            assertEquals("0", total.get("maxDependencyDepth"));
            assertEquals("0", total.get("inlets"));
            assertEquals("0", total.get("cavePools"));
            assertTrue("land " + total.get("landTiles"), Double.parseDouble(total.get("landTiles")) > 0D);
            assertTrue("streams " + total.get("streamBlocksPerTile"), Double.parseDouble(total.get("streamBlocksPerTile")) > 0D);
            assertTrue("ponds " + total.get("pondsPerTile"), Double.parseDouble(total.get("pondsPerTile")) > 0D);
            assertTrue("cold " + total.get("coldFirstChunkMs"), Double.parseDouble(total.get("coldFirstChunkMs")) > 0D);
            assertTrue("generated " + total.get("generatedChunks"), Long.parseLong(total.get("generatedChunks")) > 0L);
        }
    }

    @Test
    public void failsOnContainmentLeak() throws Exception {
        try (SyntheticHydrology fixture = new SyntheticHydrology()) {
            HydrologyReport.Result clean = HydrologyProbe.measure(fixture.inputs(), LAND);
            assertEquals(clean.lines().toString(), 0L, clean.sum(HydrologyReport.WindowMetrics::containmentLeaks));
            int[] hole = bankBesideWater(fixture);
            assertNotNull("no bank column beside water in window " + LAND, hole);
            HydrologyReport.Result leaky = HydrologyProbe.measure(fixture.inputs(blocks -> {
                if (blocks.chunkX() == hole[0] >> 4 && blocks.chunkZ() == hole[1] >> 4) {
                    blocks.set(hole[0] & 15, hole[2], hole[1] & 15, HydrologyVoxels.OPEN);
                }
                return blocks;
            }), LAND);
            Map<String, String> total = fields(line(leaky.lines(), "IRIS_HYDROLOGY_TOTAL"));
            assertTrue(leaky.lines().toString(), Long.parseLong(total.get("containmentLeaks")) > 0L);
            assertEquals("FAIL", total.get("status"));
            assertTrue(total.get("missedTargets"), total.get("missedTargets").contains("containmentLeaks"));
            assertTrue(leaky.lines().toString(), leaky.lines().stream().anyMatch(line -> line.startsWith("IRIS_HYDROLOGY_EXAMPLE leak")
                    && line.endsWith(" at " + (hole[0] - hole[3]) + "," + hole[2] + "," + (hole[1] - hole[4]))));
        }
    }

    @Test
    public void failsOnMissingPlannedSurface() throws Exception {
        try (SyntheticHydrology fixture = new SyntheticHydrology()) {
            HydrologyReport.Result clean = HydrologyProbe.measure(fixture.inputs(), LAND);
            assertEquals(clean.lines().toString(), 0L, clean.sum(HydrologyReport.WindowMetrics::missingSurface));
            int[] wet = bankBesideWater(fixture);
            assertNotNull("no wet column in window " + LAND, wet);
            int x = wet[0] - wet[3];
            int z = wet[1] - wet[4];
            HydrologyReport.Result covered = HydrologyProbe.measure(fixture.inputs(blocks -> {
                if (blocks.chunkX() == x >> 4 && blocks.chunkZ() == z >> 4) {
                    blocks.set(x & 15, wet[2], z & 15, HydrologyVoxels.SOLID);
                }
                return blocks;
            }), LAND);
            Map<String, String> total = fields(line(covered.lines(), "IRIS_HYDROLOGY_TOTAL"));
            assertEquals(covered.lines().toString(), "1", total.get("missingSurface"));
            assertEquals("FAIL", total.get("status"));
            assertTrue(total.get("missedTargets"), total.get("missedTargets").contains("missingSurface"));
            assertTrue(covered.lines().toString(), covered.lines().contains("IRIS_HYDROLOGY_EXAMPLE missing planned surface at " + x + ","
                    + wet[2] + "," + z));
        }
    }

    @Test
    public void seaFillOverNaturalSeaFloorIsNotAHydrologyLeak() throws Exception {
        HydrologyWindows.WindowRange coast = new HydrologyWindows.WindowRange(3, 3, 0, 0);
        try (SyntheticHydrology fixture = new SyntheticHydrology()) {
            int[] sea = dryColumnInTheSea(fixture, coast);
            assertNotNull("no dry hydrology column over natural sea floor in window " + coast, sea);
            HydrologyReport.Result result = HydrologyProbe.measure(fixture.inputs(blocks -> {
                if (blocks.chunkX() == sea[2] >> 4 && blocks.chunkZ() == sea[3] >> 4) {
                    blocks.set(sea[2] & 15, SyntheticHydrology.SEA_LEVEL, sea[3] & 15, HydrologyVoxels.OPEN);
                }
                return blocks;
            }), coast);
            String leak = "IRIS_HYDROLOGY_EXAMPLE leak source beside open ground at " + sea[0] + "," + SyntheticHydrology.SEA_LEVEL + "," + sea[1];
            assertTrue(result.lines().toString(), result.lines().stream().noneMatch(leak::equals));
        }
    }

    @Test
    public void aMouthOneBelowTheSeaBesideASeaFilledShoreLeaks() throws Exception {
        HydrologyWindows.WindowRange coast = new HydrologyWindows.WindowRange(3, 3, 0, 0);
        try (SyntheticHydrology fixture = new SyntheticHydrology()) {
            int[] shore = seaShoreBesideWater(fixture, coast);
            assertNotNull("no sea-filled column beside hydrology water in window " + coast, shore);
            HydrologyReport.Result result = HydrologyProbe.measure(fixture.inputs(blocks -> {
                if (blocks.chunkX() == shore[2] >> 4 && blocks.chunkZ() == shore[3] >> 4) {
                    blocks.set(shore[2] & 15, SyntheticHydrology.SEA_LEVEL, shore[3] & 15, HydrologyVoxels.OPEN);
                }
                return blocks;
            }), coast);
            String leak = "IRIS_HYDROLOGY_EXAMPLE leak source beside open ground at " + shore[0] + "," + SyntheticHydrology.SEA_LEVEL + "," + shore[1];
            assertTrue(result.lines().toString(), result.lines().contains(leak));
        }
    }

    private static int[] seaShoreBesideWater(SyntheticHydrology fixture, HydrologyWindows.WindowRange range) {
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int z = range.minZ() * WindowKey.SIZE + 1; z < (range.maxZ() + 1) * WindowKey.SIZE - 1; z++) {
            for (int x = range.minX() * WindowKey.SIZE + 1; x < (range.maxX() + 1) * WindowKey.SIZE - 1; x++) {
                ChunkWaterRaster raster = fixture.raster(x >> 4, z >> 4);
                if (raster.empty() || raster.role(x & 15, z & 15).wet() || fixture.naturalHeight(x, z) >= SyntheticHydrology.SEA_LEVEL
                        || raster.terrainHeight(x & 15, z & 15) >= SyntheticHydrology.SEA_LEVEL) {
                    continue;
                }
                for (int[] offset : offsets) {
                    int nx = x + offset[0];
                    int nz = z + offset[1];
                    ChunkWaterRaster neighbour = fixture.raster(nx >> 4, nz >> 4);
                    if (neighbour.role(nx & 15, nz & 15).wet() && neighbour.waterHead(nx & 15, nz & 15) == SyntheticHydrology.SEA_LEVEL
                            && fixture.terrainHeight(nx, nz) < SyntheticHydrology.SEA_LEVEL) {
                        return new int[]{x, z, nx, nz};
                    }
                }
            }
        }
        return null;
    }

    private static int[] dryColumnInTheSea(SyntheticHydrology fixture, HydrologyWindows.WindowRange range) {
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int z = range.minZ() * WindowKey.SIZE + 1; z < (range.maxZ() + 1) * WindowKey.SIZE - 1; z++) {
            for (int x = range.minX() * WindowKey.SIZE + 1; x < (range.maxX() + 1) * WindowKey.SIZE - 1; x++) {
                ChunkWaterRaster raster = fixture.raster(x >> 4, z >> 4);
                WaterRole role = raster.role(x & 15, z & 15);
                if (raster.empty() || role == WaterRole.NONE || role.wet() || fixture.naturalHeight(x, z) >= SyntheticHydrology.SEA_LEVEL
                        || raster.terrainHeight(x & 15, z & 15) >= SyntheticHydrology.SEA_LEVEL) {
                    continue;
                }
                for (int[] offset : offsets) {
                    int nx = x + offset[0];
                    int nz = z + offset[1];
                    if (fixture.raster(nx >> 4, nz >> 4).role(nx & 15, nz & 15) == WaterRole.NONE
                            && fixture.terrainHeight(nx, nz) < SyntheticHydrology.SEA_LEVEL) {
                        return new int[]{x, z, nx, nz};
                    }
                }
            }
        }
        return null;
    }

    @Test
    public void inletAndCavePoolChecksFeedTheWindowAndFailTheProbe() throws Exception {
        try (SyntheticHydrology fixture = new SyntheticHydrology()) {
            HydrologyReport.Result result = HydrologyProbe.measure(fixture.inputs(UnaryOperator.identity(),
                    (key, plan) -> new HydrologyUnderground.Counts(3, 2L, 5, 1L, List.of("inlet leak at 1,2,3", "pool air at 4,5,6"))), LAND);
            Map<String, String> total = fields(line(result.lines(), "IRIS_HYDROLOGY_TOTAL"));
            assertEquals("3", total.get("inlets"));
            assertEquals("2", total.get("inletFailures"));
            assertEquals("5", total.get("cavePools"));
            assertEquals("1", total.get("cavePoolFailures"));
            assertEquals("FAIL", total.get("status"));
            assertTrue(total.get("missedTargets"), total.get("missedTargets").contains("inletFailures")
                    && total.get("missedTargets").contains("cavePoolFailures"));
            assertTrue(result.lines().toString(), result.lines().contains("IRIS_HYDROLOGY_EXAMPLE inlet leak at 1,2,3"));
        }
    }

    private static int[] bankBesideWater(SyntheticHydrology fixture) {
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int min = LAND.minX() * HydrologyWindows.CHUNKS;
        for (int chunkZ = min; chunkZ < min + HydrologyWindows.CHUNKS; chunkZ++) {
            for (int chunkX = min; chunkX < min + HydrologyWindows.CHUNKS; chunkX++) {
                ChunkWaterRaster raster = fixture.raster(chunkX, chunkZ);
                for (int lz = 1; lz < 15; lz++) {
                    for (int lx = 1; lx < 15; lx++) {
                        int head = raster.waterHead(lx, lz);
                        if (!raster.role(lx, lz).wet() || head <= SyntheticHydrology.SEA_LEVEL) {
                            continue;
                        }
                        for (int[] offset : offsets) {
                            int nx = lx + offset[0];
                            int nz = lz + offset[1];
                            if (raster.waterHead(nx, nz) == Integer.MIN_VALUE && raster.terrainHeight(nx, nz) >= head) {
                                return new int[]{(chunkX << 4) + nx, (chunkZ << 4) + nz, head, offset[0], offset[1]};
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private static String line(List<String> lines, String prefix) {
        for (String line : lines) {
            if (line.startsWith(prefix + " ")) {
                return line;
            }
        }
        throw new AssertionError("No " + prefix + " line in " + lines);
    }

    private static Map<String, String> fields(String line) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String token : line.trim().split(" ")) {
            int equals = token.indexOf('=');
            if (equals > 0) {
                fields.put(token.substring(0, equals), token.substring(equals + 1));
            }
        }
        return fields;
    }

    private static void assertNumber(String metric, String value) {
        assertNotNull(metric + " missing", value);
        Double.parseDouble(value);
    }
}
