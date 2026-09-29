package art.arcane.iris.world.pregen;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.world.IrisToolbelt;
import org.bukkit.World;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AsyncPregenHistorySyncTest {
    @Test
    public void closeForcesGenerationHistoryOnceOutstandingChunksDrain() throws Exception {
        Engine engine = mock(Engine.class);
        PregenAdmissionGate admission = mock(PregenAdmissionGate.class);
        AsyncPregenMethod method = closable(engine, admission);
        try (MockedStatic<IrisToolbelt> toolbelt = mockStatic(IrisToolbelt.class)) {
            toolbelt.when(IrisToolbelt::isServerStopping).thenReturn(true);
            method.close();
        }
        InOrder order = inOrder(admission, engine);
        order.verify(admission).awaitDrain(anyLong(), any(), anyLong(), any(), any());
        order.verify(engine).syncGenerationHistory();
    }

    @Test
    public void closeReportsAHistorySyncFailureAndStillFinishes() throws Exception {
        Engine engine = mock(Engine.class);
        IllegalStateException failure = new IllegalStateException("history force failed");
        doThrow(failure).when(engine).syncGenerationHistory();
        AsyncPregenMethod method = closable(engine, mock(PregenAdmissionGate.class));
        try (MockedStatic<IrisToolbelt> toolbelt = mockStatic(IrisToolbelt.class);
             MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            toolbelt.when(IrisToolbelt::isServerStopping).thenReturn(true);
            method.close();
            logging.verify(() -> IrisLogging.reportError(anyString(), same(failure)));
        }
        verify(engine).syncGenerationHistory();
    }

    private static AsyncPregenMethod closable(Engine engine, PregenAdmissionGate admission) throws Exception {
        AsyncPregenMethod method = mock(AsyncPregenMethod.class, CALLS_REAL_METHODS);
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        set(method, "world", world);
        set(method, "closing", new AtomicBoolean());
        set(method, "holdsWorkerBoost", new AtomicBoolean());
        when(admission.awaitDrain(anyLong(), any(), anyLong(), any(), any()))
                .thenReturn(new PregenAdmissionGate.Drain(true, false, 0));
        set(method, "admission", admission);
        set(method, "mantleCleanup", new PregenSerialWorker("Test Mantle Cleanup", "world"));
        set(method, "chunkFlush", new PregenSerialWorker("Test Chunk Flush", "world"));
        set(method, "regionChunks", new ConcurrentHashMap<>());
        set(method, "pendingEvictions", new ConcurrentLinkedQueue<>());
        set(method, "metricsEngine", engine);
        Class<?> ticketExecutor = Class.forName(AsyncPregenMethod.class.getName() + "$TicketExecutor");
        Constructor<?> constructor = ticketExecutor.getDeclaredConstructor(AsyncPregenMethod.class);
        constructor.setAccessible(true);
        set(method, "executor", constructor.newInstance(method));
        return method;
    }

    private static void set(AsyncPregenMethod method, String name, Object value) throws Exception {
        Field field = AsyncPregenMethod.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(method, value);
    }
}
