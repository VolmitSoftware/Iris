package art.arcane.iris.world.pregen;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PregenCacheConcurrencyTest {
    private static final int THREADS = 8;

    private File directory;
    private ExecutorService pool;

    @Before
    public void setUp() throws Exception {
        directory = Files.createTempDirectory("iris-pregen-cache-concurrency").toFile();
        pool = Executors.newFixedThreadPool(THREADS);
    }

    @After
    public void tearDown() throws Exception {
        pool.shutdownNow();
        pool.awaitTermination(30, TimeUnit.SECONDS);
        deleteRecursively(directory);
    }

    @Test
    public void everyConcurrentlyCachedChunkIsReadBackAsCached() throws Exception {
        PregenCache cache = PregenCache.create(directory);
        int chunksPerThread = 512;
        runAll(thread -> () -> {
            for (int index = 0; index < chunksPerThread; index++) {
                int x = (thread * chunksPerThread) + index;
                cache.cacheChunk(x, thread);
            }
            return null;
        });

        for (int thread = 0; thread < THREADS; thread++) {
            for (int index = 0; index < chunksPerThread; index++) {
                int x = (thread * chunksPerThread) + index;
                assertTrue("chunk " + x + "," + thread, cache.isChunkCached(x, thread));
            }
        }
    }

    @Test
    public void concurrentWritersToOneRegionNeverLoseABit() throws Exception {
        PregenCache cache = PregenCache.create(directory);
        runAll(thread -> () -> {
            for (int index = thread; index < 1024; index += THREADS) {
                cache.cacheChunk(index & 31, index >> 5);
            }
            return null;
        });

        for (int index = 0; index < 1024; index++) {
            assertTrue("chunk " + index, cache.isChunkCached(index & 31, index >> 5));
        }
        assertTrue(cache.isRegionCached(0, 0));
    }

    @Test
    public void aRegionIsOnlyReportedCachedOnceEveryChunkInItLanded() throws Exception {
        PregenCache cache = PregenCache.create(directory);
        AtomicInteger seenComplete = new AtomicInteger();
        runAll(thread -> () -> {
            for (int index = thread; index < 1023; index += THREADS) {
                cache.cacheChunk(index & 31, index >> 5);
                if (cache.isRegionCached(0, 0)) {
                    seenComplete.incrementAndGet();
                }
            }
            return null;
        });

        assertEquals(0, seenComplete.get());
        assertFalse(cache.isRegionCached(0, 0));
        cache.cacheChunk(1023 & 31, 1023 >> 5);
        assertTrue(cache.isRegionCached(0, 0));
    }

    @Test
    public void concurrentReadsAndWritesNeverInventACachedChunk() throws Exception {
        PregenCache cache = PregenCache.create(directory);
        AtomicInteger falsePositives = new AtomicInteger();
        runAll(thread -> () -> {
            if (thread % 2 == 0) {
                for (int index = 0; index < 2048; index++) {
                    cache.cacheChunk(index, 0);
                }
                return null;
            }

            for (int index = 0; index < 2048; index++) {
                if (cache.isChunkCached(index, 7000)) {
                    falsePositives.incrementAndGet();
                }
            }
            return null;
        });

        assertEquals(0, falsePositives.get());
    }

    @Test
    public void aConcurrentWriteAndFlushRoundTripsThroughDiskWithoutLosingChunks() throws Exception {
        PregenCache cache = PregenCache.create(directory);
        runAll(thread -> () -> {
            for (int index = 0; index < 256; index++) {
                cache.cacheChunk((thread * 256) + index, 4);
                if (index % 64 == 0) {
                    cache.write();
                }
            }
            return null;
        });
        cache.write();

        PregenCache reopened = PregenCache.create(directory);
        for (int thread = 0; thread < THREADS; thread++) {
            for (int index = 0; index < 256; index++) {
                int x = (thread * 256) + index;
                assertTrue("chunk " + x + " lost across a flush", reopened.isChunkCached(x, 4));
            }
        }
    }

    @Test
    public void plateEvictionUnderConcurrencyStillPersistsEveryChunk() throws Exception {
        PregenCacheImpl cache = new PregenCacheImpl(directory, 1);
        int platesPerThread = 6;
        runAll(thread -> () -> {
            for (int plate = 0; plate < platesPerThread; plate++) {
                cache.cacheChunk(plate * 1024, thread);
            }
            return null;
        });
        cache.write();

        PregenCache reopened = PregenCache.create(directory);
        for (int thread = 0; thread < THREADS; thread++) {
            for (int plate = 0; plate < platesPerThread; plate++) {
                assertTrue("chunk " + (plate * 1024) + "," + thread + " lost to eviction",
                        reopened.isChunkCached(plate * 1024, thread));
            }
        }
    }

    private void runAll(ThreadTask task) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(THREADS);
        List<Future<Void>> futures = new ArrayList<>(THREADS);
        for (int thread = 0; thread < THREADS; thread++) {
            Callable<Void> body = task.forThread(thread);
            futures.add(pool.submit(() -> {
                barrier.await(30, TimeUnit.SECONDS);
                return body.call();
            }));
        }

        for (Future<Void> future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    private interface ThreadTask {
        Callable<Void> forThread(int thread);
    }
}
