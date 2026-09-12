package art.arcane.iris.pack.loading;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.MeteredCache;
import art.arcane.iris.generation.runtime.PreservationRegistry;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.generation.context.IrisContext;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisDataEngineRegistryTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private IrisData data;

    @Before
    public void registerPreservationService() {
        IrisServices.register(PreservationRegistry.class, new NoOpPreservationRegistry());
    }

    @After
    public void closeDataAndServices() {
        if (data != null) {
            data.close();
        }
        IrisServices.remove(PreservationRegistry.class);
    }

    @Test
    public void ambiguousRegistrationsRequireMatchingExecutionContext() throws Exception {
        data = IrisData.openRuntime(temporaryFolder.newFolder("pack"));
        AtomicBoolean firstClosed = new AtomicBoolean(false);
        Engine first = engine(firstClosed);
        Engine second = engine(new AtomicBoolean(false));

        data.registerEngine(first);
        data.registerEngine(first);
        data.registerEngine(second);

        assertEquals(2, data.getEngines().size());
        assertNull(data.getEngine());
        try (IrisContext.Scope ignored = IrisContext.open(first, 1L, null)) {
            assertSame(first, data.getEngine());
        }

        firstClosed.set(true);
        data.cleanupEngine();

        assertEquals(1, data.getEngines().size());
        assertSame(second, data.getEngine());
        data.unregisterEngine(second);
        assertNull(data.getEngine());
    }

    private Engine engine(AtomicBoolean closed) {
        Engine engine = mock(Engine.class);
        when(engine.getData()).thenReturn(data);
        when(engine.isClosed()).thenAnswer(ignored -> closed.get());
        return engine;
    }

    private static final class NoOpPreservationRegistry implements PreservationRegistry {
        @Override
        public void register(Thread thread) {
        }

        @Override
        public void register(ExecutorService service) {
        }

        @Override
        public void registerCache(MeteredCache cache) {
        }

        @Override
        public void dereference() {
        }
    }

    @Test
    public void soleEngineResolvesWithoutAContextAndDropsOnceClosed() throws Exception {
        data = IrisData.openRuntime(temporaryFolder.newFolder("pack"));
        AtomicBoolean closed = new AtomicBoolean(false);
        Engine engine = engine(closed);

        data.registerEngine(engine);

        assertSame(engine, data.getEngine());
        assertSame(engine, data.getEngine());

        closed.set(true);

        assertNull(data.getEngine());
        assertEquals(0, data.getEngines().size());
    }
}
