package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
import org.junit.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IrisComplexInferredBiomeStreamCacheTest {
    @Test
    public void compilesOncePerRegionIdentityAndPreservesSampling() {
        IrisRegion first = new IrisRegion();
        IrisRegion second = new IrisRegion();
        AtomicInteger compilations = new AtomicInteger();
        IdentityHashMap<IrisRegion, EnumMap<InferredType, IrisBiome>> expected = new IdentityHashMap<>();
        List<InferredType> compilationOrder = new ArrayList<>();

        Map<IrisRegion, Map<InferredType, ProceduralStream<IrisBiome>>> streams =
                IrisComplex.compileInferredBiomeStreams(
                        List.of(first, first, second),
                        (region, inferredType) -> {
                            compilations.incrementAndGet();
                            compilationOrder.add(inferredType);
                            IrisBiome biome = new IrisBiome().setInferredType(inferredType);
                            expected.computeIfAbsent(region, ignored -> new EnumMap<>(InferredType.class))
                                    .put(inferredType, biome);
                            return constant(biome);
                        }
                );

        assertEquals(8, compilations.get());
        assertEquals(List.of(
                InferredType.LAND,
                InferredType.CAVE,
                InferredType.SEA,
                InferredType.SHORE,
                InferredType.LAND,
                InferredType.CAVE,
                InferredType.SEA,
                InferredType.SHORE
        ), compilationOrder);
        assertEquals(2, streams.size());
        assertNotSame(streams.get(first), streams.get(second));
        for (InferredType inferredType : InferredType.values()) {
            ProceduralStream<IrisBiome> firstStream = IrisComplex.preparedInferredBiomeStream(
                    streams, first, inferredType);
            assertSame(firstStream, IrisComplex.preparedInferredBiomeStream(streams, first, inferredType));
            assertSame(expected.get(first).get(inferredType), firstStream.get(13D, 17D));
            assertSame(
                    expected.get(second).get(inferredType),
                    IrisComplex.preparedInferredBiomeStream(streams, second, inferredType).get(13D, 17D)
            );
        }
        assertThrows(UnsupportedOperationException.class, streams::clear);
        assertThrows(
                UnsupportedOperationException.class,
                () -> streams.get(first).put(InferredType.LAND, constant(new IrisBiome()))
        );
    }

    @Test
    public void rejectsOnlyUnpreparedRegionIdentities() {
        IrisRegion prepared = new IrisRegion();
        IrisRegion unprepared = new IrisRegion();
        unprepared.setLoadKey("missing");
        Map<IrisRegion, Map<InferredType, ProceduralStream<IrisBiome>>> streams =
                IrisComplex.compileInferredBiomeStreams(
                        List.of(prepared),
                        (region, inferredType) -> constant(new IrisBiome().setInferredType(inferredType))
                );

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> IrisComplex.preparedInferredBiomeStream(streams, unprepared, InferredType.LAND)
        );

        assertTrue(error.getMessage().contains("missing"));
    }

    @Test
    public void immutableCacheSupportsConcurrentReads() throws Exception {
        IrisRegion region = new IrisRegion();
        IrisBiome biome = new IrisBiome().setInferredType(InferredType.LAND);
        Map<IrisRegion, Map<InferredType, ProceduralStream<IrisBiome>>> streams =
                IrisComplex.compileInferredBiomeStreams(
                        List.of(region),
                        (preparedRegion, inferredType) -> constant(
                                inferredType == InferredType.LAND
                                        ? biome
                                        : new IrisBiome().setInferredType(inferredType)
                        )
                );
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<IrisBiome>> reads = new ArrayList<>();
            for (int task = 0; task < 32; task++) {
                reads.add(executor.submit(() -> {
                    IrisBiome sampled = null;
                    for (int read = 0; read < 1_000; read++) {
                        sampled = IrisComplex.preparedInferredBiomeStream(
                                streams, region, InferredType.LAND).get(read, -read);
                    }
                    return sampled;
                }));
            }
            for (Future<IrisBiome> read : reads) {
                assertSame(biome, read.get(5, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static ProceduralStream<IrisBiome> constant(IrisBiome biome) {
        return ProceduralStream.of(
                (x, z) -> biome,
                Interpolated.of(value -> 0D, value -> biome)
        );
    }
}
