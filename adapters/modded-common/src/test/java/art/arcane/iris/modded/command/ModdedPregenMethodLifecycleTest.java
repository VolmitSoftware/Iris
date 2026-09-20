package art.arcane.iris.modded.command;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.modded.ModdedGenPool;
import art.arcane.volmlib.nativelib.terrain.NativePregenRuntime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModdedPregenMethodLifecycleTest {
    private MockedStatic<IrisSettings> settings;
    private NativePregenRuntime runtime;
    private ModdedPregenMethod method;

    @Before
    public void prepare() {
        settings = mockStatic(IrisSettings.class);
        IrisSettings configured = new IrisSettings();
        settings.when(IrisSettings::get).thenReturn(configured);
        runtime = mock(NativePregenRuntime.class);
        when(runtime.worldIdentity()).thenReturn("example:terrain");
        Engine engine = mock(Engine.class);
        when(engine.getHeight()).thenReturn(384);
        method = new ModdedPregenMethod(new ModdedPregenMethod.Configuration(runtime, engine, true));
    }

    @After
    public void cleanup() {
        settings.close();
    }

    @Test
    public void periodicSaveRunsOnlyAfterTheServerExecutesIt() {
        method.save();
        verify(runtime, never()).save();
        ArgumentCaptor<Runnable> queued = ArgumentCaptor.forClass(Runnable.class);
        verify(runtime).execute(queued.capture());
        when(runtime.isServerThread()).thenReturn(true);
        queued.getValue().run();
        verify(runtime).save();
    }

    @Test
    public void shutdownCanClaimTheFinalSaveWithoutQueuingWorkToItsOwnThread() {
        when(runtime.isServerThread()).thenReturn(true);
        assertTrue(method.deferFinalSaveToServerThread());
        method.close();
        assertTrue(method.hasPendingFinalSave());
        verify(runtime, never()).save();
        method.completeDeferredFinalSave();
        method.completeDeferredFinalSave();
        assertFalse(method.hasPendingFinalSave());
        verify(runtime, times(1)).save();
        verify(runtime, never()).execute(any());
    }

    @Test
    public void asynchronousCallerCannotClaimTheShutdownSave() {
        assertFalse(method.deferFinalSaveToServerThread());
        assertThrows(IllegalStateException.class, method::completeDeferredFinalSave);
        verify(runtime, never()).save();
    }

    @Test
    public void failedFinalSaveStillRestoresTheEmptyServerPauseSetting() {
        AtomicInteger pause = new AtomicInteger(60);
        when(runtime.supportsPauseWhenEmpty()).thenReturn(true);
        when(runtime.pauseWhenEmptySeconds()).thenAnswer(call -> pause.get());
        doAnswer(call -> {
            pause.set(call.getArgument(0, Integer.class));
            return null;
        }).when(runtime).pauseWhenEmptySeconds(anyInt());
        when(runtime.isServerThread()).thenReturn(true);
        IllegalStateException failure = new IllegalStateException("save failed");
        doThrow(failure).when(runtime).save();
        try (MockedStatic<ModdedGenPool> pool = mockStatic(ModdedGenPool.class)) {
            method.init();
            assertEquals(0, pause.get());
            assertSame(failure, assertThrows(IllegalStateException.class, method::close));
        }
        assertEquals(60, pause.get());
    }
}
