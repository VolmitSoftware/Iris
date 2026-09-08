package art.arcane.iris.modded;

import org.junit.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ModdedStructureTemplateCacheTest {
    @Test
    public void concurrentCacheOwnsDistinctAndSharedMappingsExactlyOnce() throws Exception {
        ConcurrentHashMap<String, Integer> cache = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger mappingCalls = new AtomicInteger();
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int task = 0; task < 256; task++) {
                int value = task;
                String key = task % 2 == 0 ? "shared" : "distinct-" + task;
                futures.add(executor.submit(() -> {
                    start.await();
                    return cache.computeIfAbsent(key, ignored -> {
                        mappingCalls.incrementAndGet();
                        Thread.yield();
                        return value;
                    });
                }));
            }
            start.countDown();
            for (Future<Integer> future : futures) {
                assertNotNull(future.get(10, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }

        assertEquals(129, cache.size());
        assertEquals(129, mappingCalls.get());
    }

    @Test
    public void requiredCommonMixinConfigRegistersPaletteConcurrencyFix() throws Exception {
        InputStream resource = ModdedStructureTemplateCacheTest.class.getClassLoader()
                .getResourceAsStream("irisworldgen.entity.mixins.json");
        assertNotNull(resource);
        String config;
        try (InputStream input = resource) {
            config = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(config.contains("\"StructureTemplatePaletteConcurrencyMixin\""));
        assertNotNull(Class.forName(
                "art.arcane.iris.modded.mixin.StructureTemplatePaletteConcurrencyMixin",
                false,
                ModdedStructureTemplateCacheTest.class.getClassLoader()));
    }
}
