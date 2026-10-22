package art.arcane.iris.structure.nativegen;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.volmlib.nativelib.terrain.structure.NativeStructureVolume;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NativeStructureVolumeIndexCacheTest {
    @Test
    public void concurrentQueriesBuildEachOriginOnceAndAnswerIdentically() throws Exception {
        Map<Long, AtomicInteger> builds = new ConcurrentHashMap<>();
        NativeStructureVolumeIndex index = NativeStructureVolumeIndex.forTesting((engine, chunkX, chunkZ) -> {
            builds.computeIfAbsent(((long) chunkX << 32) ^ (chunkZ & 0xffffffffL), key -> new AtomicInteger()).incrementAndGet();
            return volumesAt(chunkX, chunkZ);
        });
        Engine engine = mock(Engine.class);
        when(engine.getCacheID()).thenReturn(7);
        KList<NativeStructureVolume> expected = NativeStructureVolumeIndex.forTesting(
                (ignored, chunkX, chunkZ) -> volumesAt(chunkX, chunkZ)).resolve(engine, 0, 0, 47, 47);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<KList<NativeStructureVolume>>> results = new ArrayList<>();
            for (int task = 0; task < 32; task++) {
                results.add(pool.submit(() -> index.resolve(engine, 0, 0, 47, 47)));
            }
            for (Future<KList<NativeStructureVolume>> result : results) {
                assertEquals(expected, result.get(30, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        }
        for (AtomicInteger count : builds.values()) {
            assertEquals(1, count.get());
        }
        int distinct = builds.size();
        index.evictRuntime(7);
        assertEquals(expected, index.resolve(engine, 0, 0, 47, 47));
        assertEquals(distinct, builds.size());
        for (AtomicInteger count : builds.values()) {
            assertEquals(2, count.get());
        }
    }

    private static KList<NativeStructureVolume> volumesAt(int chunkX, int chunkZ) {
        if (Math.floorMod(chunkX * 31 + chunkZ * 17, 5) != 0) {
            return NativeStructureVolume.NONE;
        }
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        return new KList<>(new NativeStructureVolume("test:" + chunkX + "_" + chunkZ,
                minX - 20, -10, minZ - 20, minX + 36, 40, minZ + 36, null));
    }
}
