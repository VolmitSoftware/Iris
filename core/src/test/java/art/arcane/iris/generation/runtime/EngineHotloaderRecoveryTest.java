package art.arcane.iris.generation.runtime;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.platform.bukkit.nms.BukkitGenerationPolicy;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.world.history.GenerationActivation;
import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.world.history.GenerationManifest;
import art.arcane.iris.world.history.IrisBoundarySignatureSampler;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EngineHotloaderRecoveryTest {
    @Test
    public void failedBoundaryCaptureKeepsNativeGenerationAvailableWithPendingUpdate() throws Exception {
        RecoveryFixture fixture = new RecoveryFixture(1L);
        long previousSession = fixture.sessions.currentSessionId();

        fixture.failPromotion();

        assertEquals(IrisEngine.LifecycleState.RUNNING, fixture.engine.lifecycleState);
        assertSame(fixture.previousRuntime, fixture.engine.runtime);
        assertFalse(fixture.closing.get());
        try (GenerationSessionLease lease = new BukkitGenerationPolicy(fixture.engine, null)
                .acquireLease("bukkit_nms_build_surface")) {
            assertNotEquals(previousSession, lease.sessionId());
            assertEquals(1, fixture.sessions.activeLeases());
        }
        verify(fixture.background).openBackgroundTaskAdmission();
    }

    @Test
    public void failureAfterDurableActivationDoesNotReopenPreviousGeneration() throws Exception {
        RecoveryFixture fixture = new RecoveryFixture(2L);

        fixture.failPromotion();

        assertEquals(IrisEngine.LifecycleState.FAILED, fixture.engine.lifecycleState);
        assertTrue(fixture.closing.get());
        assertThrows(IllegalStateException.class, () -> new BukkitGenerationPolicy(fixture.engine, null)
                .acquireLease("bukkit_nms_build_surface"));
    }

    @Test
    public void unreadableHistoryPreservesFailureAndKeepsGenerationSealed() throws Exception {
        RecoveryFixture fixture = new RecoveryFixture(1L);
        IllegalStateException historyFailure = new IllegalStateException("Manifest unavailable");
        when(fixture.history.manifest()).thenReturn(fixture.previousManifest).thenThrow(historyFailure);

        fixture.failPromotion();

        assertEquals(IrisEngine.LifecycleState.FAILED, fixture.engine.lifecycleState);
        assertTrue(fixture.closing.get());
        assertSame(historyFailure, fixture.failure.getSuppressed()[0]);
    }

    private static void setField(IrisEngine engine, String name, Object value) throws ReflectiveOperationException {
        Field field = IrisEngine.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(engine, value);
    }

    private static final class RecoveryFixture {
        private final IrisEngine engine = mock(IrisEngine.class);
        private final EngineRuntime previousRuntime = mock(EngineRuntime.class);
        private final GenerationSessionManager sessions = new GenerationSessionManager(true);
        private final AtomicBoolean closing = new AtomicBoolean();
        private final EngineBackgroundTasks background = mock(EngineBackgroundTasks.class);
        private final GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        private final GenerationHistory history = mock(GenerationHistory.class);
        private final GenerationManifest previousManifest = mock(GenerationManifest.class);
        private final GenerationHistoryRuntimeRouter.StudioCutover cutover = mock(
                GenerationHistoryRuntimeRouter.StudioCutover.class);
        private final StudioGenerationUpdate update = mock(StudioGenerationUpdate.class);
        private final IOException failure = new IOException("Unable to capture terrain boundary");

        private RecoveryFixture(long currentActivationId) throws Exception {
            setField(engine, "lifecycleLock", new Object());
            setField(engine, "backgroundTasks", background);
            setField(engine, "runtimeBuilder", new EngineRuntimeBuilder(engine));
            engine.runtime = previousRuntime;
            engine.lifecycleState = IrisEngine.LifecycleState.RUNNING;
            when(engine.isStudio()).thenReturn(true);
            when(engine.isClosing()).thenAnswer(invocation -> closing.get());
            when(engine.getClosing()).thenReturn(closing);
            when(engine.getGenerationSessions()).thenReturn(sessions);
            when(engine.acquireGenerationLease(anyString())).thenAnswer(invocation ->
                    sessions.acquireForEngine(engine, invocation.getArgument(0, String.class)));
            when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
            when(router.history()).thenReturn(history);
            when(router.beginStudioCutover(anyLong())).thenReturn(cutover);
            when(background.drainBackgroundTasks(anyString())).thenReturn(
                    new EngineBackgroundTasks.BackgroundTaskDrain(null, true));
            IrisWorld world = mock(IrisWorld.class);
            when(world.name()).thenReturn("studio-recovery");
            when(engine.getWorld()).thenReturn(world);
            GenerationActivation previousActivation = mock(GenerationActivation.class);
            when(previousActivation.activationId()).thenReturn(1L);
            when(previousManifest.activeActivation()).thenReturn(previousActivation);
            GenerationManifest stagedManifest = mock(GenerationManifest.class);
            GenerationActivation currentActivation = mock(GenerationActivation.class);
            when(currentActivation.activationId()).thenReturn(currentActivationId);
            when(stagedManifest.activeActivation()).thenReturn(currentActivation);
            when(history.manifest()).thenReturn(previousManifest, stagedManifest);
            doAnswer(invocation -> {
                closing.set(true);
                sessions.sealAndAwait(invocation.getArgument(0, String.class), 1000L);
                return null;
            }).when(engine).sealForTransition(anyString(), anyBoolean());
            doThrow(failure).when(cutover).promotePending();
        }

        private void failPromotion() throws Exception {
            IrisSettings settings = mock(IrisSettings.class, RETURNS_DEEP_STUBS);
            try (MockedStatic<StudioGenerationUpdate> updates = mockStatic(StudioGenerationUpdate.class);
                 MockedStatic<IrisBoundarySignatureSampler> signatures = mockStatic(IrisBoundarySignatureSampler.class);
                 MockedStatic<IrisSettings> configuration = mockStatic(IrisSettings.class)) {
                updates.when(() -> StudioGenerationUpdate.prepare(engine, history)).thenReturn(update);
                configuration.when(IrisSettings::get).thenReturn(settings);

                IllegalStateException thrown = assertThrows(IllegalStateException.class,
                        () -> new EngineHotloader(engine).hotloadSilently());
                assertSame(failure, thrown.getCause());
            }
            verify(cutover).close();
            verify(update).close();
        }
    }
}
