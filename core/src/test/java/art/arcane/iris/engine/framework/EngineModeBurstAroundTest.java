package art.arcane.iris.engine.framework;

import art.arcane.iris.testsupport.PlatformBinding;
import art.arcane.iris.util.common.parallel.MultiBurst;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.core.IrisSettings;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EngineModeBurstAroundTest {
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

    private static EngineMode mode() {
        Engine engine = mock(Engine.class);
        when(engine.burst()).thenReturn(MultiBurst.burst);
        EngineMode mode = mock(EngineMode.class, CALLS_REAL_METHODS);
        when(mode.getEngine()).thenReturn(engine);
        return mode;
    }

    @Test
    public void multicoreRunsTheInlineStageHereAndTheOthersOnThePoolBeforeReturning() {
        EngineMode mode = mode();
        ChunkContext context = mock(ChunkContext.class);
        Thread caller = Thread.currentThread();
        Set<Thread> parallelThreads = ConcurrentHashMap.newKeySet();
        List<String> completed = new CopyOnWriteArrayList<>();
        EngineStage inline = (x, z, blocks, biomes, multicore, ctx) -> {
            assertTrue(multicore);
            assertEquals(caller, Thread.currentThread());
            completed.add("inline");
        };
        EngineStage first = (x, z, blocks, biomes, multicore, ctx) -> {
            parallelThreads.add(Thread.currentThread());
            completed.add("first");
        };
        EngineStage second = (x, z, blocks, biomes, multicore, ctx) -> {
            parallelThreads.add(Thread.currentThread());
            completed.add("second");
        };

        mode.burstAround(inline, first, second).generate(0, 0, null, null, true, context);

        assertEquals(3, completed.size());
        assertTrue(completed.containsAll(List.of("inline", "first", "second")));
        assertFalse(parallelThreads.contains(caller));
    }

    @Test
    public void withoutMulticoreEveryStageRunsOnTheCallerParallelOnesFirst() {
        EngineMode mode = mode();
        ChunkContext context = mock(ChunkContext.class);
        Thread caller = Thread.currentThread();
        List<String> order = new CopyOnWriteArrayList<>();
        EngineStage inline = (x, z, blocks, biomes, multicore, ctx) -> {
            assertEquals(caller, Thread.currentThread());
            order.add("inline");
        };
        EngineStage first = (x, z, blocks, biomes, multicore, ctx) -> {
            assertEquals(caller, Thread.currentThread());
            order.add("first");
        };

        mode.burstAround(inline, first).generate(0, 0, null, null, false, context);

        assertEquals(List.of("first", "inline"), order);
    }

    @Test
    public void aParallelFailureSurfacesAfterTheInlineStageFinished() {
        EngineMode mode = mode();
        ChunkContext context = mock(ChunkContext.class);
        List<String> completed = new CopyOnWriteArrayList<>();
        EngineStage inline = (x, z, blocks, biomes, multicore, ctx) -> completed.add("inline");
        EngineStage failing = (x, z, blocks, biomes, multicore, ctx) -> {
            throw new IllegalStateException("boom");
        };

        IllegalStateException failure = org.junit.Assert.assertThrows(IllegalStateException.class,
                () -> mode.burstAround(inline, failing).generate(0, 0, null, null, true, context));

        assertEquals("boom", failure.getMessage());
        assertEquals(List.of("inline"), completed);
    }
}
