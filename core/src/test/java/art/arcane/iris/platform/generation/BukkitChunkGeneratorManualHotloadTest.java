package art.arcane.iris.platform.generation;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.platform.generation.BukkitChunkGenerator.GenerationStageGate;
import art.arcane.iris.world.task.J;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class BukkitChunkGeneratorManualHotloadTest {
    @Test
    public void manualHotloadCompletesAfterExclusiveEngineRefresh() throws Exception {
        Fixture fixture = new Fixture();
        try (MockedStatic<J> scheduling = fixture.captureScheduling()) {
            CompletableFuture<Void> result = fixture.generator.hotloadAsync();

            assertFalse(result.isDone());
            verify(fixture.engine, never()).hotload();
            fixture.queued.get().run();
            result.join();
            verify(fixture.engine).hotload();
        }
    }

    @Test
    public void manualHotloadRejectsUnavailableStudios() throws Exception {
        for (String state : new String[]{"initialEntryPending", "studioUpdatesPaused", "closing", "jigsawStudioActive"}) {
            Fixture fixture = new Fixture();
            setField(fixture.generator, state, true);
            assertTrue(state, fixture.generator.hotloadAsync().isCompletedExceptionally());
            verify(fixture.engine, never()).hotload();
        }
        Fixture fixture = new Fixture();
        setField(fixture.generator, "studio", false);
        assertTrue(fixture.generator.hotloadAsync().isCompletedExceptionally());
        verify(fixture.engine, never()).hotload();
    }

    @Test
    public void queuedHotloadFailsWhenStudioClosesBeforeExecution() throws Exception {
        Fixture fixture = new Fixture();
        try (MockedStatic<J> scheduling = fixture.captureScheduling()) {
            CompletableFuture<Void> result = fixture.generator.hotloadAsync();
            setField(fixture.generator, "closing", true);
            fixture.queued.get().run();

            assertTrue(result.isCompletedExceptionally());
            verify(fixture.engine, never()).hotload();
        }
    }

    @Test
    public void failedHotloadReleasesControlForTheNextRequest() throws Exception {
        Fixture fixture = new Fixture();
        doThrow(new IllegalStateException("Invalid pack")).doNothing().when(fixture.engine).hotload();
        try (MockedStatic<J> scheduling = fixture.captureScheduling()) {
            CompletableFuture<Void> failed = fixture.generator.hotloadAsync();
            fixture.queued.get().run();
            assertTrue(failed.isCompletedExceptionally());

            CompletableFuture<Void> retried = fixture.generator.hotloadAsync();
            fixture.queued.get().run();
            retried.join();
        }
    }

    private static void setField(BukkitChunkGenerator generator, String name, Object value) throws Exception {
        Field field = BukkitChunkGenerator.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(generator, value);
    }

    private static final class Fixture {
        private final Engine engine = mock(Engine.class);
        private final BukkitChunkGenerator generator = mock(BukkitChunkGenerator.class, CALLS_REAL_METHODS);
        private final AtomicReference<Runnable> queued = new AtomicReference<>();

        private Fixture() throws Exception {
            generator.setEngine(engine);
            setField(generator, "studio", true);
            setField(generator, "loadLock", new GenerationStageGate(1, () -> false));
        }

        private MockedStatic<J> captureScheduling() {
            MockedStatic<J> scheduling = mockStatic(J.class);
            scheduling.when(() -> J.a(any(Runnable.class))).thenAnswer(invocation -> {
                queued.set(invocation.getArgument(0));
                return null;
            });
            return scheduling;
        }
    }
}
