package art.arcane.iris.engine.mantle;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
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

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
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
        public MantleFlag getFlag() {
            return flag;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
        }
    }

    private static final class GeneratorFixture {
        private final Engine engine;
        private final Mantle<Matter> mantle;
        private final ChunkContext context;

        @SuppressWarnings("unchecked")
        private GeneratorFixture() {
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
                return chunks.computeIfAbsent(key, ignored -> chunk());
            });
            context = mock(ChunkContext.class);
            when(context.getGenerationSessionId()).thenReturn(91L);
        }

        private TestMatterGenerator generator(List<MantlePass> passes) {
            return new TestMatterGenerator(engine, mantle, passes);
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
}
