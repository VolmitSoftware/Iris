package art.arcane.iris.engine.mantle;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.context.IrisContext;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.flag.ReservedFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import art.arcane.iris.util.common.parallel.MultiBurst;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MatterGeneratorConcurrencyTest {
    private static IrisSettings previousSettings;

    @BeforeClass
    public static void bindPlatform() {
        previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
        IrisPlatforms.unbind();
        PlatformBlockState defaultBlock = mock(PlatformBlockState.class);
        PlatformRegistries registries = mock(PlatformRegistries.class);
        IrisPlatform platform = mock(IrisPlatform.class);
        when(registries.block(anyString())).thenReturn(defaultBlock);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
    }

    @AfterClass
    public static void unbindPlatform() {
        IrisPlatforms.unbind();
        IrisSettings.settings = previousSettings;
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
    public void multicoreComponentRunsOnTheDispatcher() {
        GeneratorFixture fixture = new GeneratorFixture();
        Thread caller = Thread.currentThread();
        Set<Thread> threads = ConcurrentHashMap.newKeySet();
        RecordingComponent ordinary = new RecordingComponent(ReservedFlag.OBJECT, 0, 16) {
            @Override
            public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
                threads.add(Thread.currentThread());
            }
        };
        TestMatterGenerator generator = fixture.generator(List.of(
                new MantlePass(List.of(ordinary), 1, 0)
        ));

        generator.generateMatter(0, 0, true, fixture.context);

        assertFalse(threads.isEmpty());
        assertFalse(threads.contains(caller));
    }

    @Test
    public void asyncComponentReceivesCallerContextAndCallerScopeIsRestored() {
        GeneratorFixture fixture = new GeneratorFixture();
        AtomicReference<IrisContext> observed = new AtomicReference<>();
        AtomicReference<Thread> observedThread = new AtomicReference<>();
        RecordingComponent concurrent = new RecordingComponent(ReservedFlag.CARVED, 0, 0) {
            @Override
            public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
                observed.set(IrisContext.require());
                observedThread.set(Thread.currentThread());
            }
        };
        TestMatterGenerator generator = fixture.generator(List.of(
                new MantlePass(List.of(concurrent), 0, 0)
        ));

        assertNull(IrisContext.get());
        try (IrisContext.Scope ignored = IrisContext.open(fixture.engine, 91L, fixture.context)) {
            IrisContext caller = IrisContext.require();
            Thread callerThread = Thread.currentThread();
            generator.generateMatter(0, 0, true, fixture.context);

            assertNotSame(callerThread, observedThread.get());
            assertSame(fixture.engine, observed.get().getEngine());
            assertSame(fixture.context, observed.get().getChunkContext());
            assertEquals(91L, observed.get().getGenerationSessionId());
            assertSame(caller, IrisContext.require());
        }
        assertNull(IrisContext.get());
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
    public void contentPhaseRequiresTerrainEvenWhenItsExplicitPrerequisiteIsMissing() {
        GeneratorFixture fixture = new GeneratorFixture(true);
        RecordingComponent terrain = new RecordingComponent(ReservedFlag.CARVED, 0, 0) {
            @Override
            public MatterGenerationPhase getGenerationPhase() {
                return MatterGenerationPhase.TERRAIN;
            }
        };
        RecordingComponent content = new RecordingComponent(ReservedFlag.OBJECT, 1, 0) {
            @Override
            public MantleFlag[] getPrerequisiteFlags() {
                return new MantleFlag[]{ReservedFlag.CARVED};
            }
        };
        TestMatterGenerator generator = fixture.generator(List.of(
                new MantlePass(List.of(terrain), 0, 0),
                new MantlePass(List.of(content), 0, 0)));

        assertThrows(IllegalStateException.class,
                () -> generator.generateContentMatter(0, 0, false, fixture.context));
        assertFalse(fixture.mantle.getChunk(0, 0).isFlagged(MantleFlag.PLANNED));
    }

    @Test
    public void dispatcherPropagatesAnAlreadyFailedSharedComponent() {
        IllegalStateException failure = new IllegalStateException("Shared component failed");
        ExecutionException observed = sharedComponentFailure(CompletableFuture.failedFuture(failure), ExecutionException.class);
        assertSame(failure, observed.getCause());
    }

    @Test
    public void dispatcherPropagatesAnAlreadyCancelledSharedComponent() {
        CompletableFuture<Void> cancelled = new CompletableFuture<>();
        cancelled.cancel(false);
        sharedComponentFailure(cancelled, CancellationException.class);
    }

    private <T extends Throwable> T sharedComponentFailure(CompletableFuture<Void> completion, Class<T> expectedFailure) {
        GeneratorFixture fixture = new GeneratorFixture(true);
        RecordingComponent component = new RecordingComponent(ReservedFlag.OBJECT, 0, 0);
        TestMatterGenerator generator = fixture.generator(List.of(new MantlePass(List.of(component), 0, 0)));
        MatterGenerator.MatterTaskKey key = new MatterGenerator.MatterTaskKey(fixture.mantle, 0, 0, ReservedFlag.OBJECT);
        MatterGenerator.IN_FLIGHT_COMPONENTS.put(key, completion);
        try {
            CompletableFuture<Void> generation = MultiBurst.burst.completeValueAsync(() -> {
                generator.generateMatter(0, 0, true, fixture.context);
                return null;
            });
            return assertThrows(expectedFailure, () -> generation.get(5L, TimeUnit.SECONDS));
        } finally {
            MatterGenerator.IN_FLIGHT_COMPONENTS.remove(key, completion);
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

        private GeneratorFixture() {
            this(false);
        }

        /**
         * @param lockingChunks true to give every chunk a real per-flag lock and flag state, so a
         *                      generation that reaches a chunk another generation is working on
         *                      blocks exactly like production
         */
        @SuppressWarnings("unchecked")
        private GeneratorFixture(boolean lockingChunks) {
            IrisDimension dimension = mock(IrisDimension.class);
            when(dimension.isUseMantle()).thenReturn(true);
            EngineMantle engineMantle = mock(EngineMantle.class);
            engine = mock(Engine.class);
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
            context = mock(ChunkContext.class);
            when(context.getGenerationSessionId()).thenReturn(91L);
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

        long deadline = System.currentTimeMillis() + 10_000L;
        while (secondCompleted.size() < 7 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10L);
        }
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
