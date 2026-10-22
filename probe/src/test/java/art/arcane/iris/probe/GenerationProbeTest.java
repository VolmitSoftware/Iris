package art.arcane.iris.probe;

import art.arcane.iris.generation.hydrology.HydrologyTileCache;
import art.arcane.iris.generation.hydrology.HydrologyTileKey;
import art.arcane.iris.generation.runtime.EngineMetrics;
import org.junit.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class GenerationProbeTest {
    @Test
    public void parsesExplicitBenchmarkInputs() {
        GenerationProbe.ProbeConfiguration configuration = GenerationProbe.ProbeConfiguration.parse(new String[]{
                "/tmp/pack",
                "underworld",
                "256",
                "1024",
                "2048",
                "-2048",
                "true",
                "true",
                "-1"
        });

        assertEquals(new File("/tmp/pack"), configuration.packSource());
        assertEquals("underworld", configuration.dimensionKey());
        assertEquals(256, configuration.warmupChunks());
        assertEquals(1024, configuration.measuredChunks());
        assertEquals(2048, configuration.centerChunkX());
        assertEquals(-2048, configuration.centerChunkZ());
        assertTrue(configuration.multicore());
        assertTrue(configuration.studio());
        assertEquals(-1L, configuration.seed());
    }

    @Test
    public void rejectsImplicitOrInvalidBenchmarkInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> GenerationProbe.ProbeConfiguration.parse(new String[]{"/tmp/pack"}));
        assertThrows(IllegalArgumentException.class,
                () -> new GenerationProbe.ProbeConfiguration(new File("/tmp/pack"), " ", 1, 1, 0, 0, false, false, 1337L));
        assertThrows(IllegalArgumentException.class,
                () -> new GenerationProbe.ProbeConfiguration(new File("/tmp/pack"), "overworld", 0, 1, 0, 0, false, false, 1337L));
        assertThrows(IllegalArgumentException.class,
                () -> new GenerationProbe.ProbeConfiguration(new File("/tmp/pack"), "overworld", 1, 0, 0, 0, false, false, 1337L));
    }

    @Test
    public void parsesSignedBorderCoordinatesAndFullWidthSeeds() {
        GenerationProbe.ProbeConfiguration configuration = GenerationProbe.ProbeConfiguration.parse(new String[]{
                "/tmp/pack", "overworld", "32", "96", "1874000", "-1874000", "false", "false",
                Long.toString(Long.MIN_VALUE)
        });
        assertEquals(Long.MIN_VALUE, configuration.seed());
        assertEquals(-1874000, configuration.centerChunkZ());
        assertThrows(NumberFormatException.class,
                () -> GenerationProbe.ProbeConfiguration.parse(new String[]{
                        "/tmp/pack", "overworld", "32", "96", "0", "0", "false", "false", "invalid"
                }));
    }

    @Test
    public void schedulesTheExactNumberOfUniqueChunksAroundTheCenter() {
        List<GenerationProbe.ChunkCoordinate> coordinates = GenerationProbe.scheduleCoordinates(1280, 2048, -2048);
        Set<GenerationProbe.ChunkCoordinate> distinct = new HashSet<>(coordinates);

        assertEquals(1280, coordinates.size());
        assertEquals(1280, distinct.size());
        assertTrue(coordinates.stream().allMatch(coordinate -> Math.abs(coordinate.x() - 2048) <= 18));
        assertTrue(coordinates.stream().allMatch(coordinate -> Math.abs(coordinate.z() + 2048) <= 18));
    }

    @Test
    public void ordersMeasuredChunksOutwardFromTheCenterWithoutTouchingWarmup() {
        List<GenerationProbe.ChunkCoordinate> coordinates = GenerationProbe.scheduleCoordinates(320, 2048, 2048);
        List<Integer> order = GenerationProbe.spiralOrder(coordinates, 64, 2048, 2048);

        assertEquals(256, order.size());
        assertEquals(256, new HashSet<>(order).size());
        assertTrue(order.stream().allMatch(index -> index >= 64));
        int previousRing = -1;
        for (int index : order) {
            GenerationProbe.ChunkCoordinate coordinate = coordinates.get(index);
            int ring = Math.max(Math.abs(coordinate.x() - 2048), Math.abs(coordinate.z() - 2048));
            assertTrue(ring >= previousRing);
            previousRing = ring;
        }
    }

    @Test
    public void pregenerationAreaCoversEveryScheduledChunk() {
        GenerationProbe.ProbeConfiguration configuration = new GenerationProbe.ProbeConfiguration(
                new File("/tmp/pack"), "overworld", 64, 256, 2048, 2048, true, false, 1337L);
        HydrologyTileCache.PregenerationArea area = GenerationProbe.pregenerationArea(configuration);

        for (GenerationProbe.ChunkCoordinate coordinate : GenerationProbe.scheduleCoordinates(320, 2048, 2048)) {
            assertTrue(((long) coordinate.x() << 4) >= area.minimumBlockX());
            assertTrue((((long) coordinate.x() << 4) + 15L) <= area.maximumBlockX());
            assertTrue(((long) coordinate.z() << 4) >= area.minimumBlockZ());
            assertTrue((((long) coordinate.z() << 4) + 15L) <= area.maximumBlockZ());
        }
    }

    @Test
    public void readsThroughputAndDumpOptionsFromSystemProperties() {
        System.setProperty("iris.probe.threads", "16");
        System.setProperty("iris.probe.dumpFile", "/tmp/iris-dump.bin");
        System.setProperty("iris.probe.pregenProfile", "true");
        try {
            GenerationProbe.ProbeOptions options = GenerationProbe.ProbeOptions.fromSystemProperties();
            assertEquals(16, options.threads());
            assertEquals(Path.of("/tmp/iris-dump.bin"), options.dumpFile());
            assertTrue(options.pregenProfile());
            assertTrue(options.parallel());
        } finally {
            System.clearProperty("iris.probe.threads");
            System.clearProperty("iris.probe.dumpFile");
            System.clearProperty("iris.probe.pregenProfile");
        }
        GenerationProbe.ProbeOptions defaults = GenerationProbe.ProbeOptions.fromSystemProperties();
        assertEquals(1, defaults.threads());
        assertNull(defaults.dumpFile());
        assertThrows(IllegalArgumentException.class,
                () -> new GenerationProbe.ProbeOptions(0, null, false, false, false, false, false, false));
        assertFalse(defaults.hydrologyPreplan());
        assertFalse(defaults.historyRouter());
        assertFalse(defaults.productionBuffers());
        assertFalse(defaults.counters());
    }

    @Test
    public void readsHarnessFidelityOptionsFromSystemProperties() {
        System.setProperty("iris.probe.hydrologyPreplan", "true");
        System.setProperty("iris.probe.historyRouter", "true");
        System.setProperty("iris.probe.productionBuffers", "true");
        System.setProperty("iris.generation.counters", "true");
        try {
            GenerationProbe.ProbeOptions options = GenerationProbe.ProbeOptions.fromSystemProperties();
            assertTrue(options.hydrologyPreplan());
            assertTrue(options.historyRouter());
            assertTrue(options.productionBuffers());
            assertTrue(options.counters());
            assertFalse(options.parallel());
        } finally {
            System.clearProperty("iris.probe.hydrologyPreplan");
            System.clearProperty("iris.probe.historyRouter");
            System.clearProperty("iris.probe.productionBuffers");
            System.clearProperty("iris.generation.counters");
        }
    }

    @Test
    public void stageLineReportsOnlyTheMeasuredWindow() {
        Map<String, EngineMetrics.StageTotal> before = new LinkedHashMap<>();
        before.put("total", new EngineMetrics.StageTotal(100D, 10L));
        before.put("context.prefill.height", new EngineMetrics.StageTotal(0D, 0L));
        Map<String, EngineMetrics.StageTotal> after = new LinkedHashMap<>();
        after.put("total", new EngineMetrics.StageTotal(350.25D, 20L));
        after.put("context.prefill.height", new EngineMetrics.StageTotal(12.5D, 10L));

        String line = ProbeWindowMetrics.stagesLine(new ProbeWindowMetrics(before, Map.of()),
                new ProbeWindowMetrics(after, Map.of()), 10, 1);

        assertEquals("IRIS_GENPROBE_STAGES version=1 chunks=10 threads=1 total=250.250 context.prefill.height=12.500", line);
    }

    @Test
    public void counterLineReportsWindowTotalsAndPerChunkRates() {
        Map<String, Long> before = new LinkedHashMap<>();
        before.put("generator_evaluations", 1_000L);
        before.put("corner_starcasts", 0L);
        Map<String, Long> after = new LinkedHashMap<>();
        after.put("generator_evaluations", 5_000L);
        after.put("corner_starcasts", 330L);

        String line = ProbeWindowMetrics.countersLine(new ProbeWindowMetrics(Map.of(), before),
                new ProbeWindowMetrics(Map.of(), after), 2);

        assertEquals("IRIS_GENPROBE_COUNTERS version=1 chunks=2 generator_evaluations=4000"
                + " generator_evaluations_per_chunk=2000.0 corner_starcasts=330 corner_starcasts_per_chunk=165.0", line);
    }

    @Test
    public void hydrologyPreplanCoversEveryTileAScheduledChunkComposesFromPlusTheNeighbourRing() {
        GenerationProbe.ProbeConfiguration configuration = new GenerationProbe.ProbeConfiguration(
                new File("/tmp/pack"), "overworld", 256, 1024, 2048, 2048, false, false, 1337L);
        int tileSize = 512;
        int publicationRadius = 96;
        int reachChunks = 13;
        List<HydrologyTileKey> keys = ProbeHydrologyPreplan.tiles(configuration, tileSize, publicationRadius, reachChunks);
        Set<HydrologyTileKey> planned = new HashSet<>(keys);

        assertEquals(keys.size(), planned.size());
        for (GenerationProbe.ChunkCoordinate chunk : GenerationProbe.scheduleCoordinates(1280, 2048, 2048)) {
            for (int offsetX = -reachChunks; offsetX <= reachChunks; offsetX++) {
                for (int offsetZ = -reachChunks; offsetZ <= reachChunks; offsetZ++) {
                    long minimumX = (long) (chunk.x() + offsetX) * 16L;
                    long minimumZ = (long) (chunk.z() + offsetZ) * 16L;
                    int firstX = Math.floorDiv(Math.toIntExact(minimumX - publicationRadius), tileSize) - 1;
                    int lastX = Math.floorDiv(Math.toIntExact(minimumX + 15L + publicationRadius), tileSize) + 1;
                    int firstZ = Math.floorDiv(Math.toIntExact(minimumZ - publicationRadius), tileSize) - 1;
                    int lastZ = Math.floorDiv(Math.toIntExact(minimumZ + 15L + publicationRadius), tileSize) + 1;
                    for (int tileX = firstX; tileX <= lastX; tileX++) {
                        for (int tileZ = firstZ; tileZ <= lastZ; tileZ++) {
                            assertTrue(planned.contains(new HydrologyTileKey(tileX, tileZ)));
                        }
                    }
                }
            }
        }
        HydrologyTileKey first = keys.getFirst();
        assertEquals(new HydrologyTileKey(Math.floorDiv(2048 * 16 + 8, tileSize), Math.floorDiv(2048 * 16 + 8, tileSize)), first);
    }

    @Test
    public void reportsMedianNearestRankP95MaximumAndTotal() {
        GenerationProbe.TimingSummary odd = GenerationProbe.TimingSummary.from(List.of(50L, 10L, 30L, 20L, 40L));
        GenerationProbe.TimingSummary even = GenerationProbe.TimingSummary.from(List.of(40L, 10L, 30L, 20L));

        assertEquals(30L, odd.medianNanos());
        assertEquals(50L, odd.p95Nanos());
        assertEquals(50L, odd.maxNanos());
        assertEquals(150L, odd.totalNanos());
        assertEquals(25L, even.medianNanos());
    }

    @Test
    public void emitsAStableMachineReadableResultLine() {
        GenerationProbe.TimingSummary timings = new GenerationProbe.TimingSummary(
                10_000_000L, 20_000_000L, 30_000_000L, 40_000_000L);
        GenerationProbe.ProbeResult result = new GenerationProbe.ProbeResult(
                "PASS", "underworld", 2, 4, true, true, 6, 0,
                5_000_000L, 6_000_000L, timings, "0123456789abcdef");

        assertEquals(
                "IRIS_GENPROBE_RESULT version=1 status=PASS dimension=underworld warmup_chunks=2 measured_chunks=4 multicore=true studio=true successful_chunks=6 failed_chunks=0 engine_ready_ms=5.000 first_chunk_ms=6.000 measured_median_ms=10.000 measured_p95_ms=20.000 measured_max_ms=30.000 measured_total_ms=40.000 measured_cps=100.000 signature=0123456789abcdef",
                result.machineLine());
    }

    @Test
    public void offlineProbeResolvesVanillaEntitiesWithoutInventingExternalKeys() {
        StubPlatform platform = new StubPlatform(new File("/tmp/iris-probe-test"));

        assertNotNull(platform.registries().entity("minecraft:slime"));
        assertNull(platform.registries().entity("external:missing"));
    }
}
