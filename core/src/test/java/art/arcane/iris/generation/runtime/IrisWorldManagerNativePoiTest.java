package art.arcane.iris.generation.runtime;

import art.arcane.iris.platform.bukkit.nms.INMS;
import art.arcane.iris.platform.bukkit.nms.INMSBinding;
import org.bukkit.Chunk;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;

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
    public void chunkLoadPropagatesPoiFailuresToTheRuntimeEventReporter() throws Exception {
        IrisWorldManager manager = mock(IrisWorldManager.class, CALLS_REAL_METHODS);
        Field maintenance = IrisWorldManager.class.getDeclaredField("chunkMaintenance");
        maintenance.setAccessible(true);
        maintenance.set(manager, new WorldChunkMaintenance(manager));
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
