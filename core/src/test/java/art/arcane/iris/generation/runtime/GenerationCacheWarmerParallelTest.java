package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.decoration.IrisProceduralPlacement;
import art.arcane.iris.generation.runtime.GenerationCacheWarmer.ProceduralWarmTask;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GenerationCacheWarmerParallelTest {
    @Test
    public void repeatedIdentitiesKeepTheirSeedOrderWithoutConcurrentCalls() {
        IrisData data = mock(IrisData.class);
        IrisProceduralPlacement first = mock(IrisProceduralPlacement.class);
        IrisProceduralPlacement second = mock(IrisProceduralPlacement.class);
        List<Long> firstSeeds = new ArrayList<>();
        List<Long> secondSeeds = new ArrayList<>();
        AtomicInteger activeFirst = new AtomicInteger();
        when(first.getVariantObject(same(data), any(RNG.class), isNull())).thenAnswer(invocation -> {
            assertEquals(1, activeFirst.incrementAndGet());
            firstSeeds.add(((RNG) invocation.getArgument(1)).getSeed());
            activeFirst.decrementAndGet();
            return null;
        });
        when(second.getVariantObject(same(data), any(RNG.class), isNull())).thenAnswer(invocation -> {
            secondSeeds.add(((RNG) invocation.getArgument(1)).getSeed());
            return null;
        });
        GenerationCacheWarmer.warmProcedural(List.of(
                new ProceduralWarmTask(first, new RNG(11)),
                new ProceduralWarmTask(second, new RNG(12)),
                new ProceduralWarmTask(first, new RNG(13)),
                new ProceduralWarmTask(second, new RNG(14))), data);
        assertEquals(List.of(11L, 13L), firstSeeds);
        assertEquals(List.of(12L, 14L), secondSeeds);
    }

    @Test(timeout = 10000)
    public void workerLimitAndReadinessCoverAllBanks() throws Exception {
        assumeTrue(Runtime.getRuntime().availableProcessors() > 1);
        int workers = Math.min(4, Runtime.getRuntime().availableProcessors());
        CountDownLatch started = new CountDownLatch(workers);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        List<ProceduralWarmTask> tasks = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            IrisProceduralPlacement placement = mock(IrisProceduralPlacement.class);
            when(placement.getVariantObject(isNull(), any(RNG.class), isNull())).thenAnswer(invocation -> {
                maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
                started.countDown();
                try {
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                    completed.incrementAndGet();
                    return null;
                } finally {
                    active.decrementAndGet();
                }
            });
            tasks.add(new ProceduralWarmTask(placement, new RNG(index)));
        }
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> warming = executor.submit(() -> GenerationCacheWarmer.warmProcedural(tasks, null));
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> warming.get(50, TimeUnit.MILLISECONDS));
            } finally {
                release.countDown();
            }
            warming.get(5, TimeUnit.SECONDS);
        }
        assertEquals(workers, maximum.get());
        assertEquals(12, completed.get());
    }

    @Test(timeout = 10000)
    public void failureWaitsForOtherBanksAndKeepsOriginalCause() throws Exception {
        assumeTrue(Runtime.getRuntime().availableProcessors() > 1);
        IrisProceduralPlacement failing = mock(IrisProceduralPlacement.class);
        IrisProceduralPlacement waiting = mock(IrisProceduralPlacement.class);
        IllegalStateException failure = new IllegalStateException("variant bank failed");
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(failing.getVariantObject(isNull(), any(RNG.class), isNull())).thenThrow(failure);
        when(waiting.getVariantObject(isNull(), any(RNG.class), isNull())).thenAnswer(invocation -> {
            started.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return null;
        });
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> warming = executor.submit(() -> GenerationCacheWarmer.warmProcedural(List.of(
                    new ProceduralWarmTask(failing, new RNG(1)),
                    new ProceduralWarmTask(waiting, new RNG(2))), null));
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> warming.get(50, TimeUnit.MILLISECONDS));
            } finally {
                release.countDown();
            }
            assertSame(failure, assertThrows(ExecutionException.class,
                    () -> warming.get(5, TimeUnit.SECONDS)).getCause());
        }
    }
}
