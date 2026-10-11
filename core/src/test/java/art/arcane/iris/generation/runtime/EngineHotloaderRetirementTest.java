package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.structure.placement.IrisStructureLocator;
import art.arcane.iris.structure.placement.StructureReachability;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.iris.world.history.IrisBoundarySignatureSampler;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EngineHotloaderRetirementTest {
    @Test
    public void studioPromotionDrainsHydrologyWorkersOutsideLifecycleLock() throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        Object lifecycleLock = new Object();
        setField(engine, "lifecycleLock", lifecycleLock);
        EngineBackgroundTasks background = mock(EngineBackgroundTasks.class);
        when(background.drainBackgroundTasks(anyString())).thenReturn(
                new EngineBackgroundTasks.BackgroundTaskDrain(null, true));
        setField(engine, "backgroundTasks", background);
        EngineRuntimeBuilder runtimeBuilder = mock(EngineRuntimeBuilder.class);
        setField(engine, "runtimeBuilder", runtimeBuilder);
        engine.runtime = mock(EngineRuntime.class);
        engine.lifecycleState = IrisEngine.LifecycleState.RUNNING;
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        GenerationHistory history = mock(GenerationHistory.class);
        when(router.history()).thenReturn(history);
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        when(engine.isStudio()).thenReturn(true);
        IrisWorld world = mock(IrisWorld.class);
        when(world.name()).thenReturn("studio");
        when(engine.getWorld()).thenReturn(world);
        when(engine.getDimension()).thenReturn(mock(IrisDimension.class));
        when(engine.getEngineData()).thenReturn(mock(IrisEngineData.class, RETURNS_DEEP_STUBS));
        when(engine.getPlatformHooks()).thenReturn(mock(EnginePlatformHooks.class));
        when(engine.getPrefetchSaveStarted()).thenReturn(new AtomicBoolean());
        GenerationHistoryRuntimeRouter.StudioCutover cutover = mock(
                GenerationHistoryRuntimeRouter.StudioCutover.class);
        when(router.beginStudioCutover(anyLong())).thenReturn(cutover);
        AtomicBoolean drained = new AtomicBoolean();
        doAnswer(invocation -> {
            assertFalse(Thread.holdsLock(lifecycleLock));
            CompletableFuture<Void> planning = CompletableFuture.runAsync(() -> {
                synchronized (lifecycleLock) {
                    drained.set(true);
                }
            });
            planning.get(5L, TimeUnit.SECONDS);
            return null;
        }).when(cutover).promotePending();
        StudioGenerationUpdate update = mock(StudioGenerationUpdate.class);
        IrisSettings settings = mock(IrisSettings.class, RETURNS_DEEP_STUBS);

        try (MockedStatic<StudioGenerationUpdate> updates = mockStatic(StudioGenerationUpdate.class);
             MockedStatic<IrisBoundarySignatureSampler> signatures = mockStatic(IrisBoundarySignatureSampler.class);
             MockedStatic<IrisStructureLocator> structures = mockStatic(IrisStructureLocator.class);
             MockedStatic<StructureReachability> reachability = mockStatic(StructureReachability.class);
             MockedStatic<IrisSettings> configuration = mockStatic(IrisSettings.class)) {
            updates.when(() -> StudioGenerationUpdate.prepare(engine, history)).thenReturn(update);
            configuration.when(IrisSettings::get).thenReturn(settings);

            new EngineHotloader(engine).hotloadSilently();
        }

        assertTrue(drained.get());
        verify(runtimeBuilder).refreshStudioRuntimeServices();
        verify(cutover).close();
        verify(update).close();
    }

    private static void setField(IrisEngine engine, String name, Object value) throws ReflectiveOperationException {
        Field field = IrisEngine.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(engine, value);
    }
}
