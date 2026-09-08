package art.arcane.iris.engine.hydrology;

import org.junit.Assume;
import org.junit.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class HydrologyColumnSampleAccessorTest {
    private static final int WARMUP = 20_000;
    private static final int ROUNDS = 20_000;
    private static final int PASSES = 5;

    @Test
    public void theNullableSurfaceAccessorsAgreeWithTheOptionalOnes() {
        HydrologyColumnSample sample = sample();

        assertSame(sample.primarySurfaceLayer().orElse(null), sample.primarySurfaceLayerOrNull());
        assertSame(sample.primarySurfaceFluidLayer().orElse(null), sample.primarySurfaceFluidLayerOrNull());
        assertNotNull(sample.primarySurfaceLayerOrNull());
        assertNotNull(sample.primarySurfaceFluidLayerOrNull());
    }

    @Test
    public void aColumnWithoutSurfaceLayersReportsNothingOnBothAccessors() {
        HydrologyColumnSample empty = new HydrologyColumnSample(0, 0, 70, 62, false, "overworld/plains", List.of());

        assertNull(empty.primarySurfaceLayerOrNull());
        assertNull(empty.primarySurfaceFluidLayerOrNull());
        assertTrue(empty.primarySurfaceLayer().isEmpty());
        assertTrue(empty.primarySurfaceFluidLayer().isEmpty());
        assertEquals(70, empty.terrainHeight());
    }

    @Test
    public void terrainHeightStillFollowsThePrimarySurfaceLayer() {
        HydrologyColumnSample sample = sample();

        assertEquals(sample.primarySurfaceLayerOrNull().bedY(), sample.terrainHeight());
    }

    @Test
    public void theNullableAccessorNeverAllocatesMoreThanTheOptionalOne() {
        HydrologyColumnSample sample = sample();

        Measurement nullable = best(() -> sample.primarySurfaceLayerOrNull());
        Measurement optional = best(() -> sample.primarySurfaceLayer().orElse(null));

        System.out.println("[bench] primarySurfaceLayer nullable=" + nullable + " optional=" + optional);

        Assume.assumeTrue("per-thread allocation counters are unavailable on this JVM", nullable.bytes() >= 0L);
        assertTrue("the nullable accessor allocated " + nullable.bytes()
                        + " bytes against " + optional.bytes() + " for the Optional accessor",
                nullable.bytes() <= optional.bytes());
    }

    private static HydrologyColumnSample sample() {
        List<HydrologyColumnLayer> layers = new ArrayList<>();
        layers.add(layer(1L, HydrologyFeatureType.RIFFLE, 66, 68, true, false, true, true));
        layers.add(layer(2L, HydrologyFeatureType.SURFACE_POOL, 67, 68, false, true, false, false));
        layers.add(layer(3L, HydrologyFeatureType.MOUTH, 65, 67, true, false, true, true));
        return new HydrologyColumnSample(4, 9, 74, 62, false, "overworld/river", layers);
    }

    private static HydrologyColumnLayer layer(
            long id,
            HydrologyFeatureType type,
            int bedY,
            int fluidHeadY,
            boolean channel,
            boolean shore,
            boolean connectedFluid,
            boolean fluidOwned
    ) {
        return new HydrologyColumnLayer(
                new HydrologyFeatureRef(id, type, id, id, 4, fluidHeadY, 9, 1, 0, false),
                bedY,
                fluidHeadY,
                fluidHeadY,
                channel,
                shore,
                false,
                connectedFluid,
                false,
                false,
                true,
                fluidOwned,
                false,
                "overworld/river",
                "overworld/river-surface",
                "overworld/river-mouth",
                "overworld/river-shore",
                "overworld/river-bank",
                "overworld/river-cave");
    }

    private static Measurement best(Supplier<HydrologyColumnLayer> work) {
        for (int round = 0; round < WARMUP; round++) {
            work.get();
        }

        long bytes = Long.MAX_VALUE;
        long nanos = Long.MAX_VALUE;
        for (int pass = 0; pass < PASSES; pass++) {
            Measurement measured = measure(work);
            if (measured.bytes() < 0L) {
                return measured;
            }
            bytes = Math.min(bytes, measured.bytes());
            nanos = Math.min(nanos, measured.nanos());
        }
        return new Measurement(bytes, nanos);
    }

    private static Measurement measure(Supplier<HydrologyColumnLayer> work) {
        HydrologyColumnLayer sink = null;
        long startBytes = allocatedBytes();
        long startNanos = System.nanoTime();
        for (int round = 0; round < ROUNDS; round++) {
            sink = work.get();
        }
        long elapsedNanos = System.nanoTime() - startNanos;
        long endBytes = allocatedBytes();
        assertNotNull(sink);
        long bytes = startBytes < 0L || endBytes < 0L ? -1L : (endBytes - startBytes) / ROUNDS;

        return new Measurement(bytes, elapsedNanos / ROUNDS);
    }

    private static long allocatedBytes() {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        if (!(threads instanceof com.sun.management.ThreadMXBean hotspot) || !hotspot.isThreadAllocatedMemoryEnabled()) {
            return -1L;
        }
        return hotspot.getThreadAllocatedBytes(Thread.currentThread().threadId());
    }

    private record Measurement(long bytes, long nanos) {
        @Override
        public String toString() {
            return "{bytes/op=" + bytes + ", ns/op=" + nanos + "}";
        }
    }
}
