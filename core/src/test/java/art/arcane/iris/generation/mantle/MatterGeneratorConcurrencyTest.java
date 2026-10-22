package art.arcane.iris.generation.mantle;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.testsupport.Await;
import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.context.IrisContext;
import art.arcane.iris.spi.LogLevel;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.flag.ReservedFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import art.arcane.iris.generation.concurrent.MultiBurst;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MatterGeneratorConcurrencyTest {
    @ClassRule
    public static final PlatformBinding PLATFORM = PlatformBinding.mockPlatform();

    private static IrisSettings previousSettings;

    @BeforeClass
    public static void bindPlatform() {
        previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
    }

    @AfterClass
    public static void unbindPlatform() {
        IrisSettings.settings = previousSettings;
    }

    @Test
    public void externalWindowsBoundManagedBlockedComponentSubmissions() throws Exception {
        GeneratorFixture fixture = new GeneratorFixture(true);
        int pooled = 2 * MatterGenerator.COMPONENT_SUBMISSION_LIMIT;
        int maximum = pooled + 2;
        CountDownLatch admitted = new CountDownLatch(maximum);
        CountDownLatch excess = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger started = new AtomicInteger();
        AtomicInteger startedOnDispatcher = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        RecordingComponent component = new RecordingComponent(ReservedFlag.OBJECT, 0, 64) {
            @Override
            public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
                if (started.incrementAndGet() > maximum) {
                    excess.countDown();
                }
                if (MatterGenerator.DISPATCHER.ownsCurrentThread()) {
                    startedOnDispatcher.incrementAndGet();
                }
                admitted.countDown();
                awaitManaged(release);
                completed.incrementAndGet();
            }
        };
        TestMatterGenerator generator = fixture.generator(List.of(new MantlePass(List.of(component), 4, 0)));
        CompletableFuture<Void> first = CompletableFuture.runAsync(
                () -> generator.generateMatter(0, 0, true, fixture.context));
        CompletableFuture<Void> second = CompletableFuture.runAsync(
                () -> generator.generateMatter(32, 0, true, fixture.context));
        try {
            await(admitted);
            assertFalse(excess.await(300L, TimeUnit.MILLISECONDS));
            assertEquals(maximum, started.get());
            assertEquals(pooled, startedOnDispatcher.get());
            assertEquals(maximum, activeClaims(fixture, 0, 4) + activeClaims(fixture, 32, 4));
            verify(fixture.mantle.getChunk(-4, -4), never()).release();
        } finally {
            release.countDown();
            first.get(10L, TimeUnit.SECONDS);
            second.get(10L, TimeUnit.SECONDS);
        }
        assertEquals(162, completed.get());
        assertEquals(0, activeClaims(fixture, 0, 4) + activeClaims(fixture, 32, 4));
        verify(fixture.mantle.getChunk(-4, -4)).release();
    }

    @Test
    public void externalWindowDefersSharedClaimsUntilItsOwnSubmissionsComplete() throws Exception {
        GeneratorFixture fixture = new GeneratorFixture(true);
        List<MatterGenerator.MatterComponentTask> shared = new ArrayList<>();
        for (int z = -4; z < -4 + MatterGenerator.COMPONENT_SUBMISSION_LIMIT; z++) {
            MatterGenerator.MatterTaskKey key = new MatterGenerator.MatterTaskKey(
                    fixture.mantle, -4, z, ReservedFlag.OBJECT);
            MatterGenerator.MatterComponentTask task = new MatterGenerator.MatterComponentTask(key);
            assertTrue(task.start());
            assertNull(MatterGenerator.IN_FLIGHT_COMPONENTS.putIfAbsent(key, task));
            shared.add(task);
        }
        AtomicInteger completed = new AtomicInteger();
        RecordingComponent component = new RecordingComponent(ReservedFlag.OBJECT, 0, 64) {
            @Override
            public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
                completed.incrementAndGet();
            }
        };
        TestMatterGenerator generator = fixture.generator(List.of(new MantlePass(List.of(component), 4, 0)));
        CompletableFuture<Void> generation = CompletableFuture.runAsync(
                () -> generator.generateMatter(0, 0, true, fixture.context));
        try {
            Await.until("all privately owned component submissions", Duration.ofSeconds(10L),
                    () -> completed.get() == 81 - shared.size());
            assertFalse(generation.isDone());
        } finally {
            for (MatterGenerator.MatterComponentTask task : shared) {
                task.finish(null);
            }
            generation.get(10L, TimeUnit.SECONDS);
        }
    }

    @Test
    public void failedSubmissionDrainsItsWritersBeforeReturning() throws Exception {
        GeneratorFixture fixture = new GeneratorFixture(true);
        CountDownLatch failed = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(MatterGenerator.COMPONENT_SUBMISSION_LIMIT - 1);
        CountDownLatch release = new CountDownLatch(1);
        IllegalStateException failure = new IllegalStateException("Component rejected its input");
        RecordingComponent component = new RecordingComponent(ReservedFlag.OBJECT, 0, 64) {
            @Override
            public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
                if (x == -4 && z == -4) {
                    await(started);
                    failed.countDown();
                    throw failure;
                }
                started.countDown();
                awaitManaged(release);
            }
        };
        TestMatterGenerator generator = fixture.generator(List.of(new MantlePass(List.of(component), 4, 0)));
        CompletableFuture<Void> generation = CompletableFuture.runAsync(
                () -> generator.generateMatter(0, 0, true, fixture.context));
        try {
            await(failed);
            await(started);
            if (MatterGenerator.COMPONENT_SUBMISSION_LIMIT > 1) {
                assertFalse(generation.isDone());
                verify(fixture.mantle.getChunk(-4, -4), never()).release();
            }
        } finally {
            release.countDown();
        }
        ExecutionException observed = assertThrows(ExecutionException.class,
                () -> generation.get(10L, TimeUnit.SECONDS));
        assertSame(failure, observed.getCause());
        verify(fixture.mantle.getChunk(-4, -4)).release();
        assertEquals(0, activeClaims(fixture, 0, 4));
    }

    @Test
    public void externalWindowKeepsSubmittingPastAStragglingComponent() throws Exception {
        GeneratorFixture fixture = new GeneratorFixture(true);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger completed = new AtomicInteger();
        RecordingComponent component = new RecordingComponent(ReservedFlag.OBJECT, 0, 64) {
            @Override
            public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
                if (x == -4 && z == -4) {
                    awaitManaged(release);
                }
                completed.incrementAndGet();
            }
        };
        TestMatterGenerator generator = fixture.generator(List.of(new MantlePass(List.of(component), 4, 0)));
        CompletableFuture<Void> generation = CompletableFuture.runAsync(
                () -> generator.generateMatter(0, 0, true, fixture.context));
        try {
            Await.until("every chunk but the straggler", Duration.ofSeconds(10L), () -> completed.get() == 80);
            assertFalse(generation.isDone());
        } finally {
            release.countDown();
        }
        generation.get(10L, TimeUnit.SECONDS);
        assertEquals(81, completed.get());
        assertEquals(0, activeClaims(fixture, 0, 4));
    }

    @Test
    public void inlineWindowFinishesItsOwnChunksBeforeJoiningAClaimAnotherGenerationHolds() throws Exception {
        GeneratorFixture fixture = new GeneratorFixture(true);
        MantleChunk<Matter> held = fixture.mantle.getChunk(1, 0);
        CountDownLatch claimed = new CountDownLatch(1);
        CountDownLatch releaseClaim = new CountDownLatch(1);
        Set<Long> completed = ConcurrentHashMap.newKeySet();
        RecordingComponent component = new RecordingComponent(ReservedFlag.OBJECT, 0, 16) {
            @Override
            public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
                completed.add((((long) x) << 32) ^ (z & 0xffffffffL));
            }
        };
        TestMatterGenerator generator = fixture.generator(List.of(new MantlePass(List.of(component), 1, 0)));
        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> {
            assertTrue(held.tryClaim(ReservedFlag.OBJECT));
            claimed.countDown();
            await(releaseClaim);
            held.completeClaim(ReservedFlag.OBJECT);
        });
        await(claimed);
        CompletableFuture<Void> generation = CompletableFuture.runAsync(
                () -> generator.generateMatter(0, 0, false, fixture.context));
        try {
            Await.until("the eight unclaimed chunks", Duration.ofSeconds(10L), () -> completed.size() == 8);
            assertFalse(generation.isDone());
        } finally {
            releaseClaim.countDown();
        }
        holder.get(10L, TimeUnit.SECONDS);
        generation.get(10L, TimeUnit.SECONDS);
        assertEquals(8, completed.size());
        assertTrue(held.isFlagged(ReservedFlag.OBJECT));
    }

    @Test
    public void boundedAndReentrantGenerationMatchSerialComponentResults() throws Exception {
        Map<Long, Long> serial = componentResults(false, false);
        assertEquals(81, serial.size());
        assertEquals(serial, componentResults(true, false));
        assertEquals(serial, componentResults(true, true));
    }

    private static Map<Long, Long> componentResults(boolean multicore, boolean reentrant) throws Exception {
        GeneratorFixture fixture = new GeneratorFixture(true);
        Map<Long, Long> values = new ConcurrentHashMap<>();
        RecordingComponent first = new RecordingComponent(ReservedFlag.CARVED, 0, 32) {
            @Override
            public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
                long key = (((long) x) << 32) ^ (z & 0xffffffffL);
                values.put(key, ((long) x * 341873128712L) ^ ((long) z * 132897987541L));
            }
        };
        RecordingComponent second = new RecordingComponent(ReservedFlag.OBJECT, 1, 32) {
            @Override
            public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
                assertEquals(81, values.size());
                long key = (((long) x) << 32) ^ (z & 0xffffffffL);
                values.compute(key, (ignored, value) -> value * 31L + 17L);
            }
        };
        TestMatterGenerator generator = fixture.generator(List.of(
                new MantlePass(List.of(first), 4, 32),
                new MantlePass(List.of(second), 2, 0)));
        if (reentrant) {
            MultiBurst.burst.completeValueAsync(() -> {
                generator.generateMatter(0, 0, multicore, fixture.context);
                return null;
            }).get(10L, TimeUnit.SECONDS);
        } else {
            generator.generateMatter(0, 0, multicore, fixture.context);
        }
        return values;
    }

    private static int activeClaims(GeneratorFixture fixture, int centerX, int radius) {
        int active = 0;
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                MatterGenerator.MatterTaskKey key = new MatterGenerator.MatterTaskKey(
                        fixture.mantle, x, z, ReservedFlag.OBJECT);
                if (MatterGenerator.IN_FLIGHT_COMPONENTS.containsKey(key)) {
                    active++;
                }
            }
        }
        return active;
    }

    private static void awaitManaged(CountDownLatch latch) {
        try {
            ForkJoinPool.managedBlock(new ForkJoinPool.ManagedBlocker() {
                @Override
                public boolean block() {
                    await(latch);
                    return true;
                }

                @Override
                public boolean isReleasable() {
                    return latch.getCount() == 0L;
                }
            });
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interruption);
        }
    }

    @Test
    public void multicoreChunksOverlapAndCompleteBeforeTheNextPass() {
        GeneratorFixture fixture = new GeneratorFixture();
        CountDownLatch overlap = new CountDownLatch(2);
        AtomicInteger completed = new AtomicInteger();
        AtomicBoolean barrierObserved = new AtomicBoolean();
        RecordingComponent concurrent = new RecordingComponent(ReservedFlag.OBJECT, 0, 16) {
            @Override
            public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
                overlap.countDown();
                await(overlap);
                completed.incrementAndGet();
            }
        };
        RecordingComponent barrier = new RecordingComponent(ReservedFlag.JIGSAW, 1, 0) {
            @Override
            public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
                assertEquals(9, completed.get());
                barrierObserved.set(true);
            }
        };
        TestMatterGenerator generator = fixture.generator(List.of(
                new MantlePass(List.of(concurrent), 1, 0),
                new MantlePass(List.of(barrier), 0, 0)
        ));

        generator.generateMatter(0, 0, true, fixture.context);

        assertEquals(0, overlap.getCount());
        assertEquals(9, completed.get());
        assertTrue(barrierObserved.get());
    }

    @Test
    public void multicoreComponentsSpreadAcrossTheDispatcherWhileTheCallerHelps() {
        GeneratorFixture fixture = new GeneratorFixture();
        Thread caller = Thread.currentThread();
        AtomicInteger dispatcherRuns = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        CountDownLatch dispatched = new CountDownLatch(1);
        RecordingComponent ordinary = new RecordingComponent(ReservedFlag.OBJECT, 0, 16) {
            @Override
            public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
                if (Thread.currentThread() == caller) {
                    await(dispatched);
                } else {
                    assertTrue(MatterGenerator.DISPATCHER.ownsCurrentThread());
                    dispatcherRuns.incrementAndGet();
                    dispatched.countDown();
                }
                completed.incrementAndGet();
            }
        };
        TestMatterGenerator generator = fixture.generator(List.of(
                new MantlePass(List.of(ordinary), 1, 0)
        ));

        generator.generateMatter(0, 0, true, fixture.context);

        assertEquals(9, completed.get());
        assertTrue(dispatcherRuns.get() > 0);
    }

    @Test
    public void componentsReceiveTheCallerContextOnEveryThreadAndCallerScopeIsRestored() {
        GeneratorFixture fixture = new GeneratorFixture();
        Thread callerThread = Thread.currentThread();
        Set<IrisContext> observed = ConcurrentHashMap.newKeySet();
        AtomicInteger dispatcherRuns = new AtomicInteger();
        CountDownLatch dispatched = new CountDownLatch(1);
        RecordingComponent concurrent = new RecordingComponent(ReservedFlag.CARVED, 0, 16) {
            @Override
            public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
                observed.add(IrisContext.require());
                if (Thread.currentThread() == callerThread) {
                    await(dispatched);
                } else {
                    assertTrue(MatterGenerator.DISPATCHER.ownsCurrentThread());
                    dispatcherRuns.incrementAndGet();
                    dispatched.countDown();
                }
            }
        };
        TestMatterGenerator generator = fixture.generator(List.of(
                new MantlePass(List.of(concurrent), 1, 0)
        ));

        assertNull(IrisContext.get());
        try (IrisContext.Scope ignored = IrisContext.open(fixture.engine, 91L, fixture.context)) {
            IrisContext caller = IrisContext.require();
            generator.generateMatter(0, 0, true, fixture.context);

            assertTrue(dispatcherRuns.get() > 0);
            for (IrisContext context : observed) {
                assertSame(fixture.engine, context.getEngine());
                assertSame(fixture.context, context.getChunkContext());
                assertEquals(91L, context.getGenerationSessionId());
            }
            assertSame(caller, IrisContext.require());
        }
        assertNull(IrisContext.get());
    }

    @Test
    public void externalBarrierRunsItsQueuedComponentsWhileTheDispatcherIsBusy() throws Exception {
        GeneratorFixture fixture = new GeneratorFixture(true);
        CountDownLatch releaseDispatcher = new CountDownLatch(1);
        List<Future<?>> blockers = occupyDispatcher(releaseDispatcher);
        AtomicReference<Thread> generationThread = new AtomicReference<>();
        Set<Thread> threads = ConcurrentHashMap.newKeySet();
        AtomicInteger completed = new AtomicInteger();
        RecordingComponent component = new RecordingComponent(ReservedFlag.OBJECT, 0, 16) {
            @Override
            public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
                threads.add(Thread.currentThread());
                completed.incrementAndGet();
            }
        };
        TestMatterGenerator generator = fixture.generator(List.of(new MantlePass(List.of(component), 1, 0)));
        try {
            CompletableFuture<Void> generation = CompletableFuture.runAsync(() -> {
                generationThread.set(Thread.currentThread());
                generator.generateMatter(0, 0, true, fixture.context);
            });
            generation.get(5L, TimeUnit.SECONDS);
            assertEquals(9, completed.get());
            assertEquals(Set.of(generationThread.get()), threads);
            assertEquals(0, activeClaims(fixture, 0, 1));
        } finally {
            releaseDispatcher.countDown();
            for (Future<?> blocker : blockers) {
                blocker.get(10L, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    public void dispatcherComponentsRunInsideTheCallersGenerationRuntime() {
        GeneratorFixture fixture = new GeneratorFixture(true, true);
        Thread caller = Thread.currentThread();
        fixture.bindRuntime(caller);
        IrisEngine engine = (IrisEngine) fixture.engine;
        AtomicInteger dispatcherRuns = new AtomicInteger();
        AtomicInteger unscopedRuns = new AtomicInteger();
        AtomicInteger completed = new AtomicInteger();
        CountDownLatch dispatched = new CountDownLatch(1);
        RecordingComponent component = new RecordingComponent(ReservedFlag.OBJECT, 0, 64) {
            @Override
            public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
                if (!engine.hasGenerationRuntimeScope() || engine.captureGenerationRuntimeBinding() != fixture.runtime) {
                    unscopedRuns.incrementAndGet();
                }
                if (Thread.currentThread() == caller) {
                    await(dispatched);
                } else {
                    dispatcherRuns.incrementAndGet();
                    dispatched.countDown();
                }
                completed.incrementAndGet();
            }
        };
        TestMatterGenerator generator = fixture.generator(List.of(new MantlePass(List.of(component), 4, 0)));

        generator.generateMatter(0, 0, true, fixture.context);

        assertEquals(81, completed.get());
        assertEquals(0, unscopedRuns.get());
        assertTrue(dispatcherRuns.get() > 0);
        assertEquals(fixture.openedRuntimes.get(), fixture.closedRuntimes.get());
        assertTrue(fixture.openedRuntimes.get() >= dispatcherRuns.get());
    }

    @Test
    public void helpersOnlyRunQueuedComponentsBoundToTheirOwnRuntime() {
        GeneratorFixture fixture = new GeneratorFixture();
        IrisEngine.GenerationRuntimeBinding own = mock(IrisEngine.GenerationRuntimeBinding.class);
        IrisEngine.GenerationRuntimeBinding foreign = mock(IrisEngine.GenerationRuntimeBinding.class);
        AtomicInteger runs = new AtomicInteger();
        MatterGenerator.MatterComponentTask bound = new MatterGenerator.MatterComponentTask(
                new MatterGenerator.MatterTaskKey(fixture.mantle, 0, 0, ReservedFlag.OBJECT));
        bound.queue(runs::incrementAndGet, own);
        MatterGenerator.MatterComponentTask unbound = new MatterGenerator.MatterComponentTask(
                new MatterGenerator.MatterTaskKey(fixture.mantle, 1, 0, ReservedFlag.OBJECT));
        unbound.queue(runs::incrementAndGet, null);

        bound.runIfQueued(foreign);
        unbound.runIfQueued(foreign);
        assertEquals(0, runs.get());
        bound.runIfQueued(own);
        unbound.runIfQueued(null);
        bound.runIfQueued(null);
        assertEquals(3, runs.get());
    }

    private static List<Future<?>> occupyDispatcher(CountDownLatch release) {
        int parallelism = Math.max(1, MultiBurst.burst.parallelism());
        int blockers = parallelism * 4;
        CountDownLatch started = new CountDownLatch(parallelism);
        List<Future<?>> futures = new ArrayList<>(blockers);
        for (int index = 0; index < blockers; index++) {
            futures.add(MultiBurst.burst.submit(() -> {
                started.countDown();
                try {
                    assertTrue(release.await(30L, TimeUnit.SECONDS));
                } catch (InterruptedException interruption) {
                    Thread.currentThread().interrupt();
                }
            }));
        }
        try {
            assertTrue(started.await(30L, TimeUnit.SECONDS));
        } catch (InterruptedException interruption) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while occupying the burst dispatcher.", interruption);
        }
        return futures;
    }


    @Test
    public void terrainPhasePreparesContentReachWithoutPublishingPlanned() {
        GeneratorFixture fixture = new GeneratorFixture(true);
        AtomicInteger terrainRuns = new AtomicInteger();
        AtomicInteger contentRuns = new AtomicInteger();
        RecordingComponent terrain = new RecordingComponent(ReservedFlag.CARVED, 0, 0) {
            @Override
            public MatterGenerationPhase getGenerationPhase() {
                return MatterGenerationPhase.TERRAIN;
            }

            @Override
            public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
                terrainRuns.incrementAndGet();
            }
        };
        RecordingComponent content = new RecordingComponent(ReservedFlag.OBJECT, 1, 16) {
            @Override
            public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
                assertEquals(9, terrainRuns.get());
                contentRuns.incrementAndGet();
            }
        };
        TestMatterGenerator generator = fixture.generator(List.of(
                new MantlePass(List.of(terrain), 1, 0),
                new MantlePass(List.of(content), 1, 0)));

        generator.generateTerrainMatter(0, 0, true, fixture.context);
        assertEquals(9, terrainRuns.get());
        assertEquals(0, contentRuns.get());
        assertFalse(fixture.mantle.getChunk(0, 0).isFlagged(MantleFlag.PLANNED));
        assertFalse(fixture.mantle.getChunk(0, 0).isFlagged(ReservedFlag.OBJECT));

        generator.generateContentMatter(0, 0, true, fixture.context);
        generator.generateTerrainMatter(0, 0, true, fixture.context);
        generator.generateContentMatter(0, 0, true, fixture.context);
        assertEquals(9, terrainRuns.get());
        assertEquals(9, contentRuns.get());
        assertTrue(fixture.mantle.getChunk(0, 0).isFlagged(MantleFlag.PLANNED));
    }

    @Test
    public void contentPhaseGeneratesTerrainOfEveryChunkBeforeItIsReadOrWritten() {
        GeneratorFixture fixture = new GeneratorFixture(true);
        List<String> events = new ArrayList<>();
        RecordingComponent terrain = new RecordingComponent(ReservedFlag.CARVED, 0, 0) {
            @Override
            public MatterGenerationPhase getGenerationPhase() {
                return MatterGenerationPhase.TERRAIN;
            }

            @Override
            public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
                assertSame(fixture.mantle.getChunk(x, z), writer.acquireChunk(x, z));
                events.add("terrain " + x + "," + z);
            }
        };
        RecordingComponent content = new RecordingComponent(ReservedFlag.OBJECT, 1, 0) {
            @Override
            public MantleFlag[] getPrerequisiteFlags() {
                return new MantleFlag[]{ReservedFlag.CARVED};
            }

            @Override
            public int getInputRadius() {
                return 48;
            }

            @Override
            public int getEagerInputRadius(int inputRadius) {
                return 0;
            }

            @Override
            public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
                events.add("content " + x + "," + z);
                assertSame(fixture.mantle.getChunk(x + 3, z), writer.acquireChunk(x + 3, z));
                writer.acquireChunk(x + 3, z);
                events.add("read " + (x + 3) + "," + z);
            }
        };
        TestMatterGenerator generator = fixture.generator(List.of(
                new MantlePass(List.of(terrain), 0, 0),
                new MantlePass(List.of(content), 0, 0)));

        generator.generateTerrainMatter(0, 0, false, fixture.context);
        assertEquals(List.of("terrain 0,0"), events);
        generator.generateContentMatter(0, 0, false, fixture.context);

        assertEquals(List.of("terrain 0,0", "content 0,0", "terrain 3,0", "read 3,0"), events);
        assertTrue(fixture.mantle.getChunk(0, 0).isFlagged(MantleFlag.PLANNED));
        assertTrue(fixture.mantle.getChunk(3, 0).isFlagged(ReservedFlag.CARVED));
        assertFalse(fixture.mantle.getChunk(3, 0).isFlagged(ReservedFlag.OBJECT));
    }

    @Test
    public void contentPhaseGeneratesMissingTerrainOfItsOwnChunk() {
        GeneratorFixture fixture = new GeneratorFixture(true);
        AtomicInteger terrainRuns = new AtomicInteger();
        RecordingComponent terrain = new RecordingComponent(ReservedFlag.CARVED, 0, 0) {
            @Override
            public MatterGenerationPhase getGenerationPhase() {
                return MatterGenerationPhase.TERRAIN;
            }

            @Override
            public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
                terrainRuns.incrementAndGet();
            }
        };
        RecordingComponent content = new RecordingComponent(ReservedFlag.OBJECT, 1, 0) {
            @Override
            public MantleFlag[] getPrerequisiteFlags() {
                return new MantleFlag[]{ReservedFlag.CARVED};
            }

            @Override
            public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
                assertEquals(1, terrainRuns.get());
            }
        };
        TestMatterGenerator generator = fixture.generator(List.of(
                new MantlePass(List.of(terrain), 0, 0),
                new MantlePass(List.of(content), 0, 0)));

        generator.generateContentMatter(0, 0, false, fixture.context);

        assertEquals(1, terrainRuns.get());
        assertTrue(fixture.mantle.getChunk(0, 0).isFlagged(ReservedFlag.OBJECT));
        assertTrue(fixture.mantle.getChunk(0, 0).isFlagged(MantleFlag.PLANNED));
    }

    @Test
    public void dispatcherPropagatesAnAlreadyFailedSharedComponent() {
        IllegalStateException failure = new IllegalStateException("Shared component failed");
        ExecutionException observed = sharedComponentFailure(failure, ExecutionException.class);
        assertSame(failure, observed.getCause());
    }

    @Test
    public void dispatcherPropagatesAnAlreadyCancelledSharedComponent() {
        sharedComponentFailure(new CancellationException("Shared component cancelled"), CancellationException.class);
    }

    @Test
    public void slowRunningComponentRetainsWriterAndSharedClaimAndCompletesSuccessfully() throws Exception {
        GeneratorFixture fixture = new GeneratorFixture(true);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch writersAcquired = new CountDownLatch(2);
        CountDownLatch warned = new CountDownLatch(1);
        CountDownLatch polls = new CountDownLatch(6);
        AtomicInteger warnings = new AtomicInteger();
        AtomicReference<String> diagnostic = new AtomicReference<>();
        doAnswer(invocation -> {
            warnings.incrementAndGet();
            diagnostic.set(invocation.getArgument(1));
            warned.countDown();
            return null;
        }).when(PLATFORM.platform()).log(eq(LogLevel.WARN), startsWith("Mantle component OBJECT at 0,0 is still pending"));
        AtomicInteger runs = new AtomicInteger();
        MantleChunk<Matter> chunk = fixture.mantle.getChunk(0, 0);
        when(chunk.use()).thenAnswer(invocation -> {
            writersAcquired.countDown();
            return chunk;
        });
        RecordingComponent component = new RecordingComponent(ReservedFlag.OBJECT, 0, 0) {
            @Override
            public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
                runs.incrementAndGet();
                started.countDown();
                await(release);
                verify(chunk, never()).release();
            }
        };
        TestMatterGenerator generator = fixture.generator(List.of(new MantlePass(List.of(component), 0, 0)));
        CompletableFuture<Void> first = CompletableFuture.runAsync(
                () -> generator.generateMatter(0, 0, true, fixture.context));
        await(started);
        MatterGenerator.MatterTaskKey key = new MatterGenerator.MatterTaskKey(fixture.mantle, 0, 0, ReservedFlag.OBJECT);
        MatterGenerator.MatterComponentTask task = MatterGenerator.IN_FLIGHT_COMPONENTS.get(key);
        Future<?> submission = mock(Future.class);
        when(submission.isDone()).thenAnswer(invocation -> {
            polls.countDown();
            return false;
        });
        task.setSubmission(submission);
        CompletableFuture<Void> second = MultiBurst.burst.completeValueAsync(() -> {
            generator.generateMatter(0, 0, true, fixture.context);
            return null;
        });
        CompletableFuture<Void> timeout = CompletableFuture.runAsync(() -> task.await(0L));
        CompletableFuture<Void> otherTimeout = CompletableFuture.runAsync(() -> task.await(0L));
        try {
            await(writersAcquired);
            await(warned);
            await(polls);
            assertSame(task, MatterGenerator.IN_FLIGHT_COMPONENTS.get(key));
            assertFalse(first.isDone());
            assertFalse(second.isDone());
            assertFalse(timeout.isDone());
            assertFalse(otherTimeout.isDone());
            assertEquals(1, warnings.get());
            assertTrue(diagnostic.get().contains("Worker="));
            assertTrue(diagnostic.get().contains("runningForMs="));
            assertTrue(diagnostic.get().contains("\n\tat "));
            verify(submission, never()).cancel(anyBoolean());
            verify(chunk, never()).release();
            assertEquals(1, runs.get());
        } finally {
            release.countDown();
        }
        timeout.get(5L, TimeUnit.SECONDS);
        otherTimeout.get(5L, TimeUnit.SECONDS);
        first.get(5L, TimeUnit.SECONDS);
        second.get(5L, TimeUnit.SECONDS);
        verify(chunk, times(2)).release();
        assertFalse(MatterGenerator.IN_FLIGHT_COMPONENTS.containsKey(key));
    }

    @Test
    public void slowQueuedComponentCanStartAndCompleteSuccessfully() throws Exception {
        GeneratorFixture fixture = new GeneratorFixture();
        MatterGenerator.MatterTaskKey key = new MatterGenerator.MatterTaskKey(fixture.mantle, 1, 0, ReservedFlag.OBJECT);
        MatterGenerator.MatterComponentTask task = new MatterGenerator.MatterComponentTask(key);
        MatterGenerator.IN_FLIGHT_COMPONENTS.put(key, task);
        Future<?> submission = mock(Future.class);
        task.setSubmission(submission);
        CountDownLatch warned = new CountDownLatch(1);
        AtomicReference<String> diagnostic = new AtomicReference<>();
        doAnswer(invocation -> {
            diagnostic.set(invocation.getArgument(1));
            warned.countDown();
            return null;
        }).when(PLATFORM.platform()).log(eq(LogLevel.WARN), startsWith("Mantle component OBJECT at 1,0 is still pending"));
        CompletableFuture<Void> completion = CompletableFuture.runAsync(() -> task.await(0L));
        try {
            await(warned);
            assertTrue(diagnostic.get().contains("Task is queued"));
            assertFalse(completion.isDone());
            assertSame(task, MatterGenerator.IN_FLIGHT_COMPONENTS.get(key));
            verify(submission, never()).cancel(anyBoolean());
            assertTrue(task.start());
        } finally {
            task.finish(null);
        }
        completion.get(5L, TimeUnit.SECONDS);
        assertFalse(MatterGenerator.IN_FLIGHT_COMPONENTS.containsKey(key));
    }

    @Test
    public void slowComponentPreservesItsActualFailure() throws Exception {
        GeneratorFixture fixture = new GeneratorFixture();
        MatterGenerator.MatterTaskKey key = new MatterGenerator.MatterTaskKey(fixture.mantle, 2, 0, ReservedFlag.OBJECT);
        MatterGenerator.MatterComponentTask task = new MatterGenerator.MatterComponentTask(key);
        CountDownLatch warned = new CountDownLatch(1);
        doAnswer(invocation -> {
            warned.countDown();
            return null;
        }).when(PLATFORM.platform()).log(eq(LogLevel.WARN), startsWith("Mantle component OBJECT at 2,0 is still pending"));
        IllegalStateException failure = new IllegalStateException("Object generation failed");
        assertTrue(task.start());
        CompletableFuture<Void> completion = CompletableFuture.runAsync(() -> task.await(0L));
        try {
            await(warned);
            assertFalse(completion.isDone());
        } finally {
            task.finish(failure);
        }
        ExecutionException observed = assertThrows(ExecutionException.class,
                () -> completion.get(5L, TimeUnit.SECONDS));
        assertSame(failure, observed.getCause());
    }

    @Test
    public void droppedQueuedSubmissionCannotRunAfterTheBarrier() {
        GeneratorFixture fixture = new GeneratorFixture();
        MatterGenerator.MatterTaskKey key = new MatterGenerator.MatterTaskKey(fixture.mantle, 0, 0, ReservedFlag.OBJECT);
        MatterGenerator.MatterComponentTask task = new MatterGenerator.MatterComponentTask(key);
        MatterGenerator.IN_FLIGHT_COMPONENTS.put(key, task);
        Future<?> submission = mock(Future.class);
        when(submission.isDone()).thenReturn(true);
        task.setSubmission(submission);

        CompletionException failure = assertThrows(CompletionException.class,
                () -> task.await(MatterGenerator.COMPONENT_TASK_TIMEOUT_MS));

        assertTrue(failure.getCause().getMessage().contains("was dropped"));
        assertFalse(task.start());
        assertFalse(MatterGenerator.IN_FLIGHT_COMPONENTS.containsKey(key));
    }

    @Test
    public void componentStartedByAHelperIsNotDroppedWhenItsSubmissionFinishesFirst() throws Exception {
        GeneratorFixture fixture = new GeneratorFixture();
        MatterGenerator.MatterTaskKey key = new MatterGenerator.MatterTaskKey(fixture.mantle, 3, 0, ReservedFlag.OBJECT);
        MatterGenerator.MatterComponentTask task = new MatterGenerator.MatterComponentTask(key);
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch polls = new CountDownLatch(2);
        task.queue(() -> {
            if (task.start()) {
                running.countDown();
                await(release);
                task.finish(null);
            }
        }, null);
        Future<?> submission = mock(Future.class);
        when(submission.isDone()).thenAnswer(invocation -> {
            polls.countDown();
            return true;
        });
        task.setSubmission(submission);
        CompletableFuture<Void> helper = CompletableFuture.runAsync(() -> task.runIfQueued(null));
        await(running);
        CompletableFuture<Void> waiter = CompletableFuture.runAsync(
                () -> task.await(MatterGenerator.COMPONENT_TASK_TIMEOUT_MS));
        try {
            await(polls);
            assertFalse(waiter.isDone());
        } finally {
            release.countDown();
        }
        helper.get(5L, TimeUnit.SECONDS);
        waiter.get(5L, TimeUnit.SECONDS);
        verify(submission, never()).cancel(anyBoolean());
    }

    @Test
    public void interruptedWaiterDrainsRunningTaskAndRestoresInterrupt() throws Exception {
        GeneratorFixture fixture = new GeneratorFixture();
        MatterGenerator.MatterTaskKey key = new MatterGenerator.MatterTaskKey(fixture.mantle, 0, 0, ReservedFlag.OBJECT);
        MatterGenerator.MatterComponentTask task = new MatterGenerator.MatterComponentTask(key);
        assertTrue(task.start());
        CountDownLatch waiting = new CountDownLatch(1);
        AtomicBoolean restored = new AtomicBoolean();
        CompletableFuture<Void> completion = new CompletableFuture<>();
        Thread waiter = new Thread(() -> {
            try {
                Thread.currentThread().interrupt();
                waiting.countDown();
                task.await(MatterGenerator.COMPONENT_TASK_TIMEOUT_MS);
                restored.set(Thread.currentThread().isInterrupted());
                completion.complete(null);
            } catch (Throwable failure) {
                completion.completeExceptionally(failure);
            }
        });
        waiter.start();
        await(waiting);
        try {
            assertFalse(completion.isDone());
        } finally {
            task.finish(null);
        }
        completion.get(5L, TimeUnit.SECONDS);
        waiter.join(5_000L);
        assertTrue(restored.get());
    }

    private <T extends Throwable> T sharedComponentFailure(Throwable failure, Class<T> expectedFailure) {
        GeneratorFixture fixture = new GeneratorFixture(true);
        RecordingComponent component = new RecordingComponent(ReservedFlag.OBJECT, 0, 0);
        TestMatterGenerator generator = fixture.generator(List.of(new MantlePass(List.of(component), 0, 0)));
        MatterGenerator.MatterTaskKey key = new MatterGenerator.MatterTaskKey(fixture.mantle, 0, 0, ReservedFlag.OBJECT);
        MatterGenerator.MatterComponentTask task = new MatterGenerator.MatterComponentTask(key);
        task.finish(failure);
        MatterGenerator.IN_FLIGHT_COMPONENTS.put(key, task);
        try {
            CompletableFuture<Void> generation = MultiBurst.burst.completeValueAsync(() -> {
                generator.generateMatter(0, 0, true, fixture.context);
                return null;
            });
            return assertThrows(expectedFailure, () -> generation.get(5L, TimeUnit.SECONDS));
        } finally {
            MatterGenerator.IN_FLIGHT_COMPONENTS.remove(key, task);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static class RecordingComponent implements MantleComponent {
        private final MantleFlag flag;
        private final int priority;
        private final int radius;

        private RecordingComponent(MantleFlag flag, int priority, int radius) {
            this.flag = flag;
            this.priority = priority;
            this.radius = radius;
        }

        @Override
        public int getPriority() {
            return priority;
        }

        @Override
        public int getRadius() {
            return radius;
        }

        @Override
        public EngineMantle getEngineMantle() {
            return null;
        }

        @Override
        public MantleFlag getFlag() {
            return flag;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public void setEnabled(boolean enabled) {
        }

        @Override
        public void hotload() {
        }

        @Override
        public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
        }
    }

    private static final class GeneratorFixture {
        private final Engine engine;
        private final Mantle<Matter> mantle;
        private final ChunkContext context;
        private final IrisEngine.GenerationRuntimeBinding runtime = mock(IrisEngine.GenerationRuntimeBinding.class);
        private final ThreadLocal<Integer> runtimeDepth = ThreadLocal.withInitial(() -> 0);
        private final AtomicInteger openedRuntimes = new AtomicInteger();
        private final AtomicInteger closedRuntimes = new AtomicInteger();
        private volatile Thread boundThread;

        private GeneratorFixture() {
            this(false);
        }

        private GeneratorFixture(boolean lockingChunks) {
            this(lockingChunks, false);
        }

        /**
         * @param lockingChunks true to give every chunk a real per-flag lock and flag state, so a
         *                      generation that reaches a chunk another generation is working on
         *                      blocks exactly like production
         */
        @SuppressWarnings("unchecked")
        private GeneratorFixture(boolean lockingChunks, boolean runtimeBoundCaller) {
            IrisDimension dimension = mock(IrisDimension.class);
            when(dimension.isUseMantle()).thenReturn(true);
            EngineMantle engineMantle = mock(EngineMantle.class);
            if (runtimeBoundCaller) {
                IrisEngine irisEngine = mock(IrisEngine.class);
                when(irisEngine.hasGenerationRuntimeScope()).thenAnswer(invocation -> runtimeBound());
                when(irisEngine.captureGenerationRuntimeBinding()).thenAnswer(invocation -> runtimeBound() ? runtime : null);
                when(irisEngine.openGenerationRuntimeScope(runtime)).thenAnswer(invocation -> {
                    openedRuntimes.incrementAndGet();
                    runtimeDepth.set(runtimeDepth.get() + 1);
                    IrisEngine.GenerationRuntimeScope scope = mock(IrisEngine.GenerationRuntimeScope.class);
                    doAnswer(close -> {
                        runtimeDepth.set(runtimeDepth.get() - 1);
                        closedRuntimes.incrementAndGet();
                        return null;
                    }).when(scope).close();
                    return scope;
                });
                engine = irisEngine;
            } else {
                engine = mock(Engine.class);
            }
            when(engine.getDimension()).thenReturn(dimension);
            when(engine.getMantle()).thenReturn(engineMantle);
            mantle = mock(Mantle.class);
            ConcurrentHashMap<Long, MantleChunk<Matter>> chunks = new ConcurrentHashMap<>();
            when(mantle.getChunk(anyInt(), anyInt())).thenAnswer(invocation -> {
                int chunkX = invocation.getArgument(0);
                int chunkZ = invocation.getArgument(1);
                long key = (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
                return chunks.computeIfAbsent(key, ignored -> lockingChunks ? lockingChunk() : chunk());
            });
            when(mantle.useChunk(anyInt(), anyInt())).thenAnswer(invocation ->
                    mantle.getChunk(invocation.getArgument(0), invocation.getArgument(1)).use());
            context = mock(ChunkContext.class);
            when(context.getGenerationSessionId()).thenReturn(91L);
        }

        private void bindRuntime(Thread thread) {
            boundThread = thread;
        }

        private boolean runtimeBound() {
            return Thread.currentThread() == boundThread || runtimeDepth.get() > 0;
        }

        private TestMatterGenerator generator(List<MantlePass> passes) {
            return new TestMatterGenerator(engine, mantle, passes);
        }

        @SuppressWarnings("unchecked")
        private static MantleChunk<Matter> lockingChunk() {
            MantleChunk<Matter> chunk = mock(MantleChunk.class);
            java.util.concurrent.locks.ReentrantLock lock = new java.util.concurrent.locks.ReentrantLock();
            Set<Object> flagged = ConcurrentHashMap.newKeySet();
            when(chunk.use()).thenReturn(chunk);
            when(chunk.isFlagged(any())).thenAnswer(invocation -> flagged.contains(invocation.getArgument(0)));
            doAnswer(invocation -> {
                Object flag = invocation.getArgument(0);
                boolean enabled = invocation.getArgument(1);
                return enabled ? flagged.add(flag) : flagged.remove(flag);
            }).when(chunk).flag(any(), anyBoolean());
            doAnswer(invocation -> {
                Object flag = invocation.getArgument(0);
                Runnable task = invocation.getArgument(1);
                lock.lock();
                try {
                    if (!flagged.contains(flag)) {
                        task.run();
                        flagged.add(flag);
                    }
                } finally {
                    lock.unlock();
                }
                return null;
            }).when(chunk).raiseFlagSuspend(any(), any(Runnable.class));
            when(chunk.tryClaim(any())).thenAnswer(invocation -> {
                if (flagged.contains(invocation.getArgument(0)) || !lock.tryLock()) {
                    return false;
                }
                if (flagged.contains(invocation.getArgument(0))) {
                    lock.unlock();
                    return false;
                }
                return true;
            });
            doAnswer(invocation -> {
                flagged.add(invocation.getArgument(0));
                lock.unlock();
                return null;
            }).when(chunk).completeClaim(any());
            doAnswer(invocation -> {
                lock.unlock();
                return null;
            }).when(chunk).abandonClaim(any());
            return chunk;
        }

        @SuppressWarnings("unchecked")
        private static MantleChunk<Matter> chunk() {
            MantleChunk<Matter> chunk = mock(MantleChunk.class);
            when(chunk.use()).thenReturn(chunk);
            when(chunk.isFlagged(any())).thenReturn(false);
            doAnswer(invocation -> {
                Runnable task = invocation.getArgument(1);
                task.run();
                return null;
            }).when(chunk).raiseFlagSuspend(any(), any(Runnable.class));
            return chunk;
        }
    }

    private static final class TestMatterGenerator implements MatterGenerator {
        private final Engine engine;
        private final Mantle<Matter> mantle;
        private final List<MantlePass> passes;

        private TestMatterGenerator(Engine engine, Mantle<Matter> mantle, List<MantlePass> passes) {
            this.engine = engine;
            this.mantle = mantle;
            this.passes = passes;
        }

        @Override
        public Engine getEngine() {
            return engine;
        }

        @Override
        public Mantle<Matter> getMantle() {
            return mantle;
        }

        @Override
        public int getRadius() {
            return passes.getFirst().passChunkRadius();
        }

        @Override
        public int getRealRadius() {
            return passes.getLast().passChunkRadius();
        }

        @Override
        public List<MantlePass> getComponents() {
            return passes;
        }
    }

    @Test
    public void dispatcherThreadFinishesItsOwnChunksBeforeJoiningAComponentAnotherGenerationOwns() throws Exception {
        GeneratorFixture fixture = new GeneratorFixture(true);
        CountDownLatch sharedStarted = new CountDownLatch(1);
        CountDownLatch releaseShared = new CountDownLatch(1);
        Set<Long> secondCompleted = ConcurrentHashMap.newKeySet();
        AtomicReference<Thread> firstOwner = new AtomicReference<>();
        RecordingComponent component = new RecordingComponent(ReservedFlag.OBJECT, 0, 16) {
            @Override
            public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
                if (x == 1 && z == 0 && firstOwner.compareAndSet(null, Thread.currentThread())) {
                    sharedStarted.countDown();
                    await(releaseShared);
                    return;
                }
                if (firstOwner.get() != null && Thread.currentThread() != firstOwner.get()) {
                    secondCompleted.add((((long) x) << 32) ^ (z & 0xffffffffL));
                }
            }
        };
        TestMatterGenerator generator = fixture.generator(List.of(new MantlePass(List.of(component), 1, 0)));

        java.util.concurrent.CompletableFuture<Void> first = MultiBurst.burst.completeValueAsync(() -> {
            generator.generateMatter(0, 0, true, fixture.context);
            return null;
        });
        await(sharedStarted);
        java.util.concurrent.CompletableFuture<Void> second = MultiBurst.burst.completeValueAsync(() -> {
            generator.generateMatter(2, 0, true, fixture.context);
            return null;
        });

        Await.reached("the second generation to finish its own seven chunks", Duration.ofSeconds(10L),
                () -> secondCompleted.size() >= 7);
        // The second generation's window is x 1..3, z -1..1. The first generation finished (1,-1)
        // before parking on (1,0), so the second one owns the remaining seven chunks and must
        // complete all of them while (1,0) is still held instead of blocking on it first.
        assertEquals(7, secondCompleted.size());
        assertFalse(second.isDone());

        releaseShared.countDown();
        first.get(10L, TimeUnit.SECONDS);
        second.get(10L, TimeUnit.SECONDS);
    }
}
