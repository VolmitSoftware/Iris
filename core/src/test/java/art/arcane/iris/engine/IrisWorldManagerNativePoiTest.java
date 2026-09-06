package art.arcane.iris.engine;

import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.core.nms.INMSBinding;
import art.arcane.iris.engine.framework.Engine;
import org.bukkit.Chunk;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisWorldManagerNativePoiTest {
    @Test
    public void chunkLoadReconcilesPoisWithoutAnOptionalCleanupExecutor() {
        IrisWorldManager manager = mock(IrisWorldManager.class, CALLS_REAL_METHODS);
        Engine engine = mock(Engine.class);
        Chunk chunk = mock(Chunk.class);
        INMSBinding binding = mock(INMSBinding.class);
        when(manager.getEngine()).thenReturn(engine);

        try (MockedStatic<INMS> nms = mockStatic(INMS.class)) {
            nms.when(INMS::get).thenReturn(binding);
            manager.onChunkLoad(chunk, false);
        }

        verify(binding).reconcileNativeStructurePois(chunk);
    }

    @Test
    public void chunkLoadPropagatesPoiFailuresToTheRuntimeEventReporter() {
        IrisWorldManager manager = mock(IrisWorldManager.class, CALLS_REAL_METHODS);
        Engine engine = mock(Engine.class);
        Chunk chunk = mock(Chunk.class);
        INMSBinding binding = mock(INMSBinding.class);
        IllegalStateException failure = new IllegalStateException("Native POI storage failure");
        when(manager.getEngine()).thenReturn(engine);
        doThrow(failure).when(binding).reconcileNativeStructurePois(chunk);

        try (MockedStatic<INMS> nms = mockStatic(INMS.class)) {
            nms.when(INMS::get).thenReturn(binding);
            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> manager.onChunkLoad(chunk, true)));
        }
    }
}
