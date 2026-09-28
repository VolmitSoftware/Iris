package art.arcane.iris.generation.stream;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.PreservationRegistry;
import art.arcane.iris.spi.IrisServices;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

public class ProvisionalSamplingTest {
    @Test
    public void provisionalValuesReachTheCallerWithoutBeingMemoized() {
        try (MockedStatic<IrisServices> services = mockStatic(IrisServices.class)) {
            services.when(() -> IrisServices.get(PreservationRegistry.class)).thenReturn(mock(PreservationRegistry.class));
            AtomicBoolean planned = new AtomicBoolean();
            AtomicInteger samples = new AtomicInteger();
            ProceduralStream<Double> source = ProceduralStream.ofDouble((x, z) -> {
                samples.incrementAndGet();
                if (!planned.get()) {
                    ProvisionalSampling.mark();
                    return -1D;
                }
                return x + z;
            });
            CachedDoubleStream2D cached = new CachedDoubleStream2D("provisional", mock(Engine.class), source, 4);

            assertEquals(-1D, cached.getDouble(3, 4), 0D);
            assertEquals(-1D, cached.get(3D, 4D), 0D);
            assertEquals(2, samples.get());
            planned.set(true);
            assertEquals(7D, cached.getDouble(3, 4), 0D);
            assertEquals(7D, cached.getDouble(3, 4), 0D);
            assertEquals(3, samples.get());
        }
    }

    @Test
    public void derivedCachesInheritTheMarkAndFillChunksWithoutStoringIt() {
        try (MockedStatic<IrisServices> services = mockStatic(IrisServices.class)) {
            services.when(() -> IrisServices.get(PreservationRegistry.class)).thenReturn(mock(PreservationRegistry.class));
            AtomicBoolean planned = new AtomicBoolean();
            AtomicInteger samples = new AtomicInteger();
            ProceduralStream<Double> source = ProceduralStream.ofDouble((x, z) -> {
                samples.incrementAndGet();
                if (!planned.get() && x == 5 && z == 0) {
                    ProvisionalSampling.mark();
                    return -1D;
                }
                return 1D;
            });
            CachedDoubleStream2D base = new CachedDoubleStream2D("base", mock(Engine.class), source, 4);
            CachedStream2D<String> derived = new CachedStream2D<>("derived", mock(Engine.class),
                    base.convert((Double value) -> value < 0D ? "natural" : "planned"), 4);

            Object[] row = new Object[256];
            derived.fillChunkRaw(0, 0, row);
            assertEquals("natural", row[5]);
            assertEquals("planned", row[6]);
            assertEquals("natural", derived.get(5D, 0D));
            planned.set(true);
            int before = samples.get();
            assertEquals("planned", derived.get(5D, 0D));
            assertEquals(before + 1, samples.get());
            assertEquals("planned", derived.get(6D, 0D));
            assertEquals(before + 1, samples.get());
        }
    }
}
