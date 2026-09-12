package art.arcane.iris.studio.view;

import art.arcane.volmlib.util.function.NoiseProvider;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class NoiseRenderCoordinatorTest {
    @Test
    public void adaptiveStepNeverExceedsHardSampleBudget() {
        int sampleStep = NoiseRenderCoordinator.sampleStepForBudget(3840, 2160, 400_000L);

        assertTrue(sampleStep > 1);
        assertTrue(NoiseRenderCoordinator.sampleCount(3840, 2160, sampleStep) <= 400_000L);
        assertEquals(1, NoiseRenderCoordinator.sampleStepForBudget(320, 200, 64_000L));
    }

    @Test
    public void timedBudgetStaysSmallForSlowSourcesAndCapsFastSources() {
        assertEquals(24_000L, NoiseRenderCoordinator.timeBoundSampleBudget(
                18_000L,
                2_000D,
                500D,
                24_000L,
                120_000L
        ));
        assertEquals(120_000L, NoiseRenderCoordinator.timeBoundSampleBudget(
                18_000L,
                10D,
                500D,
                24_000L,
                120_000L
        ));
    }

    @Test
    public void eachRefinementPassMakesVisibleProgressEvenForSlowSources() {
        assertEquals(7, NoiseRenderCoordinator.nextRefinementStep(1440, 770, 7, 24_000L));
        assertEquals(2, NoiseRenderCoordinator.nextRefinementStep(1440, 770, 2, 24_000L));
        assertEquals(1, NoiseRenderCoordinator.nextRefinementStep(320, 200, 3, 400_000L));
        int boundedStep = NoiseRenderCoordinator.nextRefinementStep(3840, 2160, 64, 48_000L);
        assertTrue(NoiseRenderCoordinator.sampleCount(3840, 2160, boundedStep) <= 48_000L);
    }

    @Test
    public void sameRevisionRefinementSupersedesAnInFlightPreview() throws Exception {
        CountDownLatch previewStarted = new CountDownLatch(1);
        CountDownLatch releasePreview = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        List<Integer> completedSteps = new ArrayList<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        NoiseRenderCoordinator coordinator = new NoiseRenderCoordinator(2, new NoiseRenderCoordinator.Listener() {
            @Override
            public void onRenderStarted(NoiseRenderCoordinator.Request request) {
                if (request.sampleStep() == 4) {
                    previewStarted.countDown();
                }
            }

            @Override
            public void onRenderCompleted(NoiseRenderCoordinator.Result result) {
                completedSteps.add(result.request().sampleStep());
                completed.countDown();
            }

            @Override
            public void onRenderFailed(NoiseRenderCoordinator.Request request, Throwable error) {
                failure.set(error);
                completed.countDown();
            }
        });
        try {
            AtomicBoolean firstSample = new AtomicBoolean(true);
            NoiseProvider previewSampler = (x, z) -> {
                if (firstSample.compareAndSet(true, false)) {
                    try {
                        releasePreview.await(3L, TimeUnit.SECONDS);
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                    }
                }
                return 0.25D;
            };
            NoiseRenderCoordinator.Request preview = new NoiseRenderCoordinator.Request(
                    7L,
                    previewSampler,
                    new NoiseViewport(0D, 0D, 1D),
                    NoisePalette.TERRAIN,
                    64,
                    32,
                    4
            );
            NoiseRenderCoordinator.Request refinement = new NoiseRenderCoordinator.Request(
                    7L,
                    (x, z) -> 0.75D,
                    new NoiseViewport(0D, 0D, 1D),
                    NoisePalette.TERRAIN,
                    64,
                    32,
                    2
            );

            coordinator.request(preview);
            assertTrue(previewStarted.await(3L, TimeUnit.SECONDS));
            coordinator.request(refinement);
            releasePreview.countDown();

            assertTrue(completed.await(3L, TimeUnit.SECONDS));
            assertEquals(null, failure.get());
            assertEquals(List.of(2), completedSteps);
        } finally {
            releasePreview.countDown();
            coordinator.close();
        }
    }

    @Test
    public void samplingRunsOffEdtAndStopsWhenRenderCompletes() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<NoiseRenderCoordinator.Result> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<String> samplingThread = new AtomicReference<>();
        AtomicBoolean sampledOnEdt = new AtomicBoolean();
        AtomicBoolean renderFinished = new AtomicBoolean();
        CountDownLatch sampledAfterCompletion = new CountDownLatch(1);
        AtomicInteger samples = new AtomicInteger();
        NoiseRenderCoordinator coordinator = new NoiseRenderCoordinator(2, new NoiseRenderCoordinator.Listener() {
            @Override
            public void onRenderStarted(NoiseRenderCoordinator.Request request) {
            }

            @Override
            public void onRenderCompleted(NoiseRenderCoordinator.Result rendered) {
                result.set(rendered);
                completed.countDown();
            }

            @Override
            public void onRenderFailed(NoiseRenderCoordinator.Request request, Throwable error) {
                failure.set(error);
                completed.countDown();
            }
        });
        try {
            NoiseProvider sampler = (x, z) -> {
                samples.incrementAndGet();
                if (renderFinished.get()) {
                    sampledAfterCompletion.countDown();
                }
                samplingThread.compareAndSet(null, Thread.currentThread().getName());
                sampledOnEdt.compareAndSet(false, SwingUtilities.isEventDispatchThread());
                return x + z;
            };
            NoiseRenderCoordinator.Request request = new NoiseRenderCoordinator.Request(
                    1L,
                    sampler,
                    new NoiseViewport(0D, 0D, 1D),
                    NoisePalette.SIGNED,
                    17,
                    11,
                    1
            );

            SwingUtilities.invokeAndWait(() -> coordinator.request(request));

            assertTrue(completed.await(3L, TimeUnit.SECONDS));
            assertEquals(null, failure.get());
            assertNotNull(result.get());
            assertEquals(187, samples.get());
            assertFalse(sampledOnEdt.get());
            assertTrue(samplingThread.get().startsWith("Iris Noise Renderer"));
            int completedSamples = samples.get();
            renderFinished.set(true);
            assertFalse(sampledAfterCompletion.await(150L, TimeUnit.MILLISECONDS));
            assertEquals(completedSamples, samples.get());
        } finally {
            coordinator.close();
        }
    }

    @Test
    public void samplingTraversesEachBandInRowMajorOrder() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        List<String> coordinates = new ArrayList<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        NoiseRenderCoordinator coordinator = new NoiseRenderCoordinator(1, new NoiseRenderCoordinator.Listener() {
            @Override
            public void onRenderStarted(NoiseRenderCoordinator.Request request) {
            }

            @Override
            public void onRenderCompleted(NoiseRenderCoordinator.Result result) {
                completed.countDown();
            }

            @Override
            public void onRenderFailed(NoiseRenderCoordinator.Request request, Throwable error) {
                failure.set(error);
                completed.countDown();
            }
        });
        try {
            NoiseProvider sampler = (x, z) -> {
                coordinates.add(x + "," + z);
                return 0.5D;
            };
            coordinator.request(new NoiseRenderCoordinator.Request(
                    1L,
                    sampler,
                    new NoiseViewport(0D, 0D, 1D),
                    NoisePalette.TERRAIN,
                    6,
                    4,
                    2
            ));

            assertTrue(completed.await(3L, TimeUnit.SECONDS));
            assertEquals(null, failure.get());
            assertEquals(List.of("-2.0,-1.0", "0.0,-1.0", "2.0,-1.0", "-2.0,1.0", "0.0,1.0", "2.0,1.0"), coordinates);
        } finally {
            coordinator.close();
        }
    }

    @Test
    public void newerRevisionDiscardsStaleResult() throws Exception {
        CountDownLatch firstSample = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch latestCompleted = new CountDownLatch(1);
        List<Long> completedRevisions = new ArrayList<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicInteger staleSamples = new AtomicInteger();
        NoiseRenderCoordinator coordinator = new NoiseRenderCoordinator(1, new NoiseRenderCoordinator.Listener() {
            @Override
            public void onRenderStarted(NoiseRenderCoordinator.Request request) {
            }

            @Override
            public void onRenderCompleted(NoiseRenderCoordinator.Result result) {
                completedRevisions.add(result.request().revision());
                if (result.request().revision() == 2L) {
                    latestCompleted.countDown();
                }
            }

            @Override
            public void onRenderFailed(NoiseRenderCoordinator.Request request, Throwable error) {
                failure.set(error);
                latestCompleted.countDown();
            }
        });
        try {
            AtomicBoolean block = new AtomicBoolean(true);
            NoiseProvider slowSampler = (x, z) -> {
                staleSamples.incrementAndGet();
                if (block.compareAndSet(true, false)) {
                    firstSample.countDown();
                    while (releaseFirst.getCount() > 0L) {
                        Thread.onSpinWait();
                    }
                }
                return 0.25D;
            };
            NoiseRenderCoordinator.Request stale = new NoiseRenderCoordinator.Request(
                    1L,
                    slowSampler,
                    new NoiseViewport(0D, 0D, 1D),
                    NoisePalette.TERRAIN,
                    512,
                    1,
                    1
            );
            NoiseRenderCoordinator.Request latest = new NoiseRenderCoordinator.Request(
                    2L,
                    (x, z) -> 0.75D,
                    new NoiseViewport(0D, 0D, 1D),
                    NoisePalette.TERRAIN,
                    512,
                    1,
                    1
            );

            coordinator.request(stale);
            assertTrue(firstSample.await(3L, TimeUnit.SECONDS));
            coordinator.request(latest);

            assertTrue(latestCompleted.await(3L, TimeUnit.SECONDS));
            assertEquals(null, failure.get());
            assertEquals(List.of(2L), completedRevisions);
            assertTrue(staleSamples.get() <= 32);
        } finally {
            releaseFirst.countDown();
            coordinator.close();
        }
    }

    @Test
    public void closeReturnsImmediatelyWhenSamplerIgnoresInterruption() throws Exception {
        CountDownLatch sampleStarted = new CountDownLatch(1);
        AtomicBoolean releaseSampler = new AtomicBoolean();
        AtomicBoolean daemonWorker = new AtomicBoolean();
        NoiseRenderCoordinator coordinator = new NoiseRenderCoordinator(1, new NoiseRenderCoordinator.Listener() {
            @Override
            public void onRenderStarted(NoiseRenderCoordinator.Request request) {
            }

            @Override
            public void onRenderCompleted(NoiseRenderCoordinator.Result result) {
            }

            @Override
            public void onRenderFailed(NoiseRenderCoordinator.Request request, Throwable error) {
            }
        });
        try {
            coordinator.request(new NoiseRenderCoordinator.Request(
                    1L,
                    (x, z) -> {
                        daemonWorker.set(Thread.currentThread().isDaemon());
                        sampleStarted.countDown();
                        while (!releaseSampler.get()) {
                            Thread.onSpinWait();
                        }
                        return 0.5D;
                    },
                    new NoiseViewport(0D, 0D, 1D),
                    NoisePalette.TERRAIN,
                    32,
                    32,
                    1
            ));
            assertTrue(sampleStarted.await(1L, TimeUnit.SECONDS));

            long started = System.nanoTime();
            coordinator.close();
            long closeMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

            assertTrue(closeMillis < 200L);
            assertTrue(daemonWorker.get());
        } finally {
            releaseSampler.set(true);
            coordinator.close();
        }
    }

    @Test
    public void latestMultiBandViewCompletesAfterThreeInterruptIgnoringGenerations() throws Exception {
        CountDownLatch latestCompleted = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean releaseStale = new AtomicBoolean();
        NoiseRenderCoordinator coordinator = new NoiseRenderCoordinator(2, new NoiseRenderCoordinator.Listener() {
            @Override
            public void onRenderStarted(NoiseRenderCoordinator.Request request) {
            }

            @Override
            public void onRenderCompleted(NoiseRenderCoordinator.Result result) {
                if (result.request().revision() == 4L) {
                    latestCompleted.countDown();
                }
            }

            @Override
            public void onRenderFailed(NoiseRenderCoordinator.Request request, Throwable error) {
                failure.set(error);
                latestCompleted.countDown();
            }
        });
        try {
            for (long revision = 1L; revision <= 3L; revision++) {
                CountDownLatch staleStarted = new CountDownLatch(2);
                coordinator.request(new NoiseRenderCoordinator.Request(
                        revision,
                        (x, z) -> {
                            staleStarted.countDown();
                            while (!releaseStale.get()) {
                                Thread.onSpinWait();
                            }
                            return 0.25D;
                        },
                        new NoiseViewport(0D, 0D, 1D),
                        NoisePalette.TERRAIN,
                        8,
                        4,
                        1
                ));
                assertTrue(staleStarted.await(1L, TimeUnit.SECONDS));
            }

            coordinator.request(new NoiseRenderCoordinator.Request(
                    4L,
                    (x, z) -> 0.75D,
                    new NoiseViewport(0D, 0D, 1D),
                    NoisePalette.TERRAIN,
                    8,
                    4,
                    1
            ));

            assertTrue(latestCompleted.await(3L, TimeUnit.SECONDS));
            assertEquals(null, failure.get());
        } finally {
            releaseStale.set(true);
            coordinator.close();
        }
    }
}
