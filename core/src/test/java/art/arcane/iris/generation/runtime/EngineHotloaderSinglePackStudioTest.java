package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.structure.placement.IrisStructureLocator;
import art.arcane.iris.structure.placement.StructureReachability;
import art.arcane.iris.world.IrisWorld;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class EngineHotloaderSinglePackStudioTest {
    private static final String DIMENSION_KEY = "overworld";

    @Test
    public void studioHotloadWithoutHistoryReloadsTheAuthoringPackInPlace() throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        setField(engine, "lifecycleLock", new Object());
        EngineRuntimeBuilder runtimeBuilder = mock(EngineRuntimeBuilder.class);
        setField(engine, "runtimeBuilder", runtimeBuilder);
        EngineRuntime previousRuntime = mock(EngineRuntime.class);
        EngineRuntime nextRuntime = mock(EngineRuntime.class);
        engine.runtime = previousRuntime;
        engine.lifecycleState = IrisEngine.LifecycleState.RUNNING;
        when(engine.isStudio()).thenReturn(true);
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.empty());
        File authoring = new File("authoring-pack");
        IrisData previousData = mock(IrisData.class);
        when(previousData.getDataFolder()).thenReturn(authoring);
        when(engine.getData()).thenReturn(previousData);
        IrisDimension previousDimension = mock(IrisDimension.class);
        when(previousDimension.getLoadKey()).thenReturn(DIMENSION_KEY);
        when(engine.getDimension()).thenReturn(previousDimension);
        IrisWorld world = mock(IrisWorld.class);
        when(engine.getWorld()).thenReturn(world);
        when(engine.getPlatformHooks()).thenReturn(mock(EnginePlatformHooks.class));
        when(engine.getEngineData()).thenReturn(mock(IrisEngineData.class, RETURNS_DEEP_STUBS));
        when(engine.getPrefetchSaveStarted()).thenReturn(new AtomicBoolean(true));
        IrisData replacementData = mock(IrisData.class, RETURNS_DEEP_STUBS);
        IrisDimension replacementDimension = mock(IrisDimension.class);
        when(replacementData.getDimensionLoader().load(DIMENSION_KEY)).thenReturn(replacementDimension);
        when(runtimeBuilder.buildRuntime(any(EngineTarget.class))).thenReturn(nextRuntime);

        try (MockedStatic<IrisData> data = mockStatic(IrisData.class);
             MockedStatic<StudioGenerationUpdate> updates = mockStatic(StudioGenerationUpdate.class);
             MockedStatic<IrisStructureLocator> structures = mockStatic(IrisStructureLocator.class);
             MockedStatic<StructureReachability> reachability = mockStatic(StructureReachability.class)) {
            data.when(() -> IrisData.openRuntime(authoring)).thenReturn(replacementData);

            new EngineHotloader(engine).hotloadSilently();

            updates.verifyNoInteractions();
        }

        ArgumentCaptor<EngineTarget> target = ArgumentCaptor.forClass(EngineTarget.class);
        verify(runtimeBuilder).buildRuntime(target.capture());
        assertSame(replacementDimension, target.getValue().getDimension());
        assertSame(replacementData, target.getValue().getData());
        verify(runtimeBuilder).publishRuntime(nextRuntime, previousRuntime);
        verify(replacementData).registerEngine(engine);
        verify(previousData).unregisterEngine(engine);
    }

    private static void setField(IrisEngine engine, String name, Object value) throws ReflectiveOperationException {
        Field field = IrisEngine.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(engine, value);
    }
}
