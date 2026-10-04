package art.arcane.iris.probe;

import org.junit.Test;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
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
