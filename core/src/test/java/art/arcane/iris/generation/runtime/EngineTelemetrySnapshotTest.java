package art.arcane.iris.generation.runtime;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class EngineTelemetrySnapshotTest {
    @Test
    public void aggregatesMultipleWorldsWithExplicitSumMaxAndMeanRules() {
        EngineTelemetrySnapshot first = snapshot(
                "minecraft:overworld",
                "world",
                true,
                false,
                12L,
                3L,
                0.25D,
                4L,
                100L,
                2D,
                5L,
                2,
                1,
                1L,
                4L,
                1L,
                10D,
                Map.of("total", 5D, "terrain", 2D)
        );
        EngineTelemetrySnapshot second = snapshot(
                "minecraft:the_nether",
                "world_nether",
                false,
                true,
                8L,
                5L,
                0.75D,
                6L,
                200L,
                3D,
                7L,
                4,
                2,
                2L,
                6L,
                3L,
                30D,
                Map.of("total", 8D, "terrain", 1D)
        );

        EngineTelemetrySnapshot.Aggregate aggregate = EngineTelemetrySnapshot.aggregate(List.of(first, second));

        assertEquals(2, aggregate.worldCount());
        assertEquals(1, aggregate.active());
        assertEquals(1, aggregate.studio());
        assertEquals(1, aggregate.failed());
        assertEquals(20L, aggregate.loadedChunks());
        assertEquals(8L, aggregate.loadedEntities());
        assertEquals(0.75D, aggregate.entitySaturationMax(), 0D);
        assertEquals(10L, aggregate.generatedSession());
        assertEquals(300L, aggregate.generatedTotal());
        assertEquals(5D, aggregate.chunksPerSecond(), 0D);
        assertEquals(12L, aggregate.blockUpdatesPerSecond());
        assertEquals(6, aggregate.parallelism());
        assertEquals(3, aggregate.activeGenerationLeases());
        assertEquals(3L, aggregate.hotloadsTotal());
        assertEquals(10L, aggregate.mantleResidentPlates());
        assertEquals(4L, aggregate.mantleQueuedPlates());
        assertEquals(20D, aggregate.mantleIdleAverageMs(), 0D);
        assertEquals(30D, aggregate.mantleIdleMaxMs(), 0D);
        assertEquals(10D, aggregate.mantleIdleMinMs(), 0D);
        assertEquals(8D, aggregate.generationTimingMaximaMs().get("total"), 0D);
        assertEquals(2D, aggregate.generationTimingMaximaMs().get("terrain"), 0D);
    }

    @Test
    public void normalizesInvalidRuntimeCountersAtSnapshotBoundary() {
        EngineTelemetrySnapshot snapshot = new EngineTelemetrySnapshot(
                1L,
                "minecraft:overworld",
                "world",
                "overworld",
                true,
                false,
                false,
                false,
                -1L,
                -1L,
                Double.NaN,
                -1L,
                -1L,
                Double.POSITIVE_INFINITY,
                -1L,
                -1,
                -1,
                -1L,
                -1L,
                -1L,
                Double.NaN,
                Map.of("bad", Double.NaN, "negative", -1D, "valid", 2D)
        );

        assertEquals(0L, snapshot.loadedChunks());
        assertEquals(0D, snapshot.entitySaturation(), 0D);
        assertEquals(0D, snapshot.chunksPerSecond(), 0D);
        assertEquals(Map.of("valid", 2D), snapshot.generationTimingsMs());
    }

    @Test
    public void aggregateWorldCountIgnoresMissingSnapshots() {
        List<EngineTelemetrySnapshot> snapshots = new ArrayList<>();
        snapshots.add(snapshot(
                "minecraft:overworld",
                "world",
                true,
                false,
                0L,
                0L,
                0D,
                0L,
                0L,
                0D,
                0L,
                0,
                0,
                0L,
                0L,
                0L,
                0D,
                Map.of()
        ));
        snapshots.add(null);

        assertEquals(1, EngineTelemetrySnapshot.aggregate(snapshots).worldCount());
    }

    private static EngineTelemetrySnapshot snapshot(
            String identity,
            String name,
            boolean active,
            boolean studio,
            long chunks,
            long entities,
            double saturation,
            long generatedSession,
            long generatedTotal,
            double chunksPerSecond,
            long blockUpdates,
            int parallelism,
            int leases,
            long hotloads,
            long resident,
            long queued,
            double idleMs,
            Map<String, Double> timings
    ) {
        return new EngineTelemetrySnapshot(
                1_000L,
                identity,
                name,
                "dimension",
                active,
                studio,
                false,
                !active,
                chunks,
                entities,
                saturation,
                generatedSession,
                generatedTotal,
                chunksPerSecond,
                blockUpdates,
                parallelism,
                leases,
                hotloads,
                resident,
                queued,
                idleMs,
                timings
        );
    }
}
