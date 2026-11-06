package art.arcane.iris.core.modules;

import art.arcane.iris.builtin.structure.StructureHost;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import com.volmit.world.PackWorld;
import org.junit.Test;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Threads asking an Iris world for its running world or structure host at once all get the same instance. */
public class IrisPackWorldConcurrencyTest {
    private static final int THREADS = 16;
    private static final int ROUNDS = 200;

    @Test
    public void concurrentFirstCallersShareOneRunningWorld() throws Exception {
        assertEveryCallerGetsOneInstance(world -> world.running().orElseThrow());
    }

    @Test
    public void concurrentFirstCallersShareOneStructureHost() throws Exception {
        assertEveryCallerGetsOneInstance(world -> world.extension(StructureHost.class).orElseThrow());
    }

    private static void assertEveryCallerGetsOneInstance(Function<PackWorld, Object> ask) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                PackWorld world = new IrisPackWorld(engine());
                assertEquals(1, distinctAnswers(pool, world, ask));
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static int distinctAnswers(ExecutorService pool, PackWorld world, Function<PackWorld, Object> ask) throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> answers = new ArrayList<>();
        for (int caller = 0; caller < THREADS; caller++) {
            Callable<Object> firstCall = () -> {
                go.await();
                return ask.apply(world);
            };
            answers.add(pool.submit(firstCall));
        }
        go.countDown();
        Set<Object> distinct = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Future<Object> answer : answers) {
            distinct.add(answer.get());
        }
        return distinct.size();
    }

    private static Engine engine() {
        Engine engine = mock(Engine.class);
        when(engine.getData()).thenReturn(mock(IrisData.class));
        return engine;
    }
}
