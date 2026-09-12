package art.arcane.iris.integration;

import art.arcane.iris.generation.runtime.IrisTelemetrySnapshot;

import art.arcane.iris.generation.runtime.EngineTelemetrySnapshot;
import art.arcane.volmlib.integration.IntegrationMetricGroup;
import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisIntegrationServiceTest {
    @Test
    public void unavailableTelemetryDoesNotPublishFalseZeroes() {
        IrisIntegrationService service = new IrisIntegrationService(() -> IrisTelemetrySnapshot.EMPTY);
        Map<String, IntegrationMetricSample> samples = service.sampleMetrics(Set.of(
                IntegrationMetricSchema.IRIS_WORLD_COUNT,
                IntegrationMetricSchema.IRIS_PREGEN_QUEUE,
                IntegrationMetricSchema.IRIS_GENERATION_TOTAL_MS));

        for (IntegrationMetricSample sample : samples.values()) {
            assertFalse(sample.available());
            assertEquals("telemetry-not-ready", sample.message());
        }
        assertTrue(service.metricGroups().isEmpty());
    }

    @Test
    public void publishesAggregateAndPerWorldMetricsFromOneImmutableSnapshot() {
        long now = System.currentTimeMillis();
        EngineTelemetrySnapshot first = world("minecraft:overworld", "world", 12L, 20L, 4D, now);
        EngineTelemetrySnapshot second = world("minecraft:the_nether", "world_nether", 8L, 30L, 2D, now);
        IrisTelemetrySnapshot snapshot = telemetry(
                List.of(first, second),
                new IrisTelemetrySnapshot.PregenSnapshot(
                        true,
                        first.worldIdentity(),
                        first.worldName(),
                        false,
                        50D,
                        100L,
                        200L,
                        100L,
                        8D,
                        2_000L,
                        5_000L,
                        1L
                ),
                now
        );
        IrisIntegrationService service = new IrisIntegrationService(() -> snapshot);

        Map<String, IntegrationMetricSample> samples = service.sampleMetrics(Set.of(
                IntegrationMetricSchema.IRIS_WORLD_COUNT,
                IntegrationMetricSchema.IRIS_ENGINE_PENDING_REGISTRATIONS,
                IntegrationMetricSchema.IRIS_LOADED_CHUNKS,
                IntegrationMetricSchema.IRIS_CHUNKS_GENERATED_TOTAL,
                IntegrationMetricSchema.IRIS_PREGEN_THROUGHPUT,
                IntegrationMetricSchema.IRIS_GENERATION_TOTAL_MS,
                "iris.unsupported"
        ));

        assertEquals(2D, value(samples, IntegrationMetricSchema.IRIS_WORLD_COUNT), 0D);
        assertEquals(3D, value(samples, IntegrationMetricSchema.IRIS_ENGINE_PENDING_REGISTRATIONS), 0D);
        assertEquals(20D, value(samples, IntegrationMetricSchema.IRIS_LOADED_CHUNKS), 0D);
        assertEquals(50D, value(samples, IntegrationMetricSchema.IRIS_CHUNKS_GENERATED_TOTAL), 0D);
        assertEquals(8D, value(samples, IntegrationMetricSchema.IRIS_PREGEN_THROUGHPUT), 0D);
        assertEquals(4D, value(samples, IntegrationMetricSchema.IRIS_GENERATION_TOTAL_MS), 0D);
        assertFalse(samples.get("iris.unsupported").available());
        assertEquals("unsupported-key", samples.get("iris.unsupported").message());

        List<IntegrationMetricGroup> groups = service.metricGroups();
        assertEquals(2, groups.size());
        IntegrationMetricGroup overworld = groups.get(0);
        IntegrationMetricGroup nether = groups.get(1);
        assertEquals("minecraft:overworld", overworld.scopeId());
        assertEquals(1D, value(overworld.samples(), IntegrationMetricSchema.IRIS_PREGEN_ACTIVE), 0D);
        assertEquals(8D, value(overworld.samples(), IntegrationMetricSchema.IRIS_PREGEN_THROUGHPUT), 0D);
        assertEquals("minecraft:the_nether", nether.scopeId());
        assertEquals(0D, value(nether.samples(), IntegrationMetricSchema.IRIS_PREGEN_ACTIVE), 0D);
        assertFalse(nether.samples().get(IntegrationMetricSchema.IRIS_PREGEN_QUEUE).available());
    }

    @Test
    public void publishesEveryManagedWorldWithoutAWorldCountCap() {
        long now = System.currentTimeMillis();
        List<EngineTelemetrySnapshot> worlds = new ArrayList<>();
        for (int index = 0; index < 48; index++) {
            worlds.add(world("iris:world_" + index, "world_" + index, index, index, index, now));
        }
        IrisIntegrationService service = new IrisIntegrationService(
                () -> telemetry(worlds, IrisTelemetrySnapshot.PregenSnapshot.INACTIVE, now)
        );

        List<IntegrationMetricGroup> groups = service.metricGroups();

        assertEquals(48, groups.size());
        assertEquals(48, groups.stream().map(IntegrationMetricGroup::scopeId).distinct().count());
    }

    private static IrisTelemetrySnapshot telemetry(
            List<EngineTelemetrySnapshot> worlds,
            IrisTelemetrySnapshot.PregenSnapshot pregenerator,
            long now
    ) {
        IrisTelemetrySnapshot.CacheBucket resource = new IrisTelemetrySnapshot.CacheBucket(2, 10L, 100L);
        IrisTelemetrySnapshot.CacheSnapshot caches = new IrisTelemetrySnapshot.CacheSnapshot(
                resource,
                resource,
                IrisTelemetrySnapshot.CacheBucket.EMPTY,
                IrisTelemetrySnapshot.CacheBucket.EMPTY,
                IrisTelemetrySnapshot.CacheBucket.EMPTY
        );
        return new IrisTelemetrySnapshot(
                now,
                worlds,
                EngineTelemetrySnapshot.aggregate(worlds),
                1,
                4,
                0,
                3,
                0.5D,
                0.1D,
                caches,
                pregenerator
        );
    }

    private static EngineTelemetrySnapshot world(
            String identity,
            String name,
            long loadedChunks,
            long generatedTotal,
            double generationMs,
            long now
    ) {
        return new EngineTelemetrySnapshot(
                now,
                identity,
                name,
                "dimension",
                true,
                false,
                false,
                false,
                loadedChunks,
                3L,
                0.25D,
                generatedTotal,
                generatedTotal,
                2D,
                4L,
                2,
                1,
                1L,
                4L,
                1L,
                10D,
                Map.of("total", generationMs)
        );
    }

    private static double value(Map<String, IntegrationMetricSample> samples, String key) {
        IntegrationMetricSample sample = samples.get(key);
        assertTrue(sample.available());
        return sample.valueOr(-1D);
    }
}
