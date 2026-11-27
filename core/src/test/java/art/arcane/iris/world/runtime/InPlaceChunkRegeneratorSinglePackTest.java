package art.arcane.iris.world.runtime;

import art.arcane.iris.generation.runtime.IrisEngine;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class InPlaceChunkRegeneratorSinglePackTest {
    @Test
    public void regenOnASinglePackStudioWorldLeavesGenerationOwnershipAlone() throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        when(engine.isStudio()).thenReturn(true);
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.empty());

        InPlaceChunkRegenerator.reclaimForActivePack(engine, List.of(new int[]{-72, -15}, new int[]{0, 0}));

        verify(engine).getGenerationHistoryRuntimeRouter();
        verifyNoMoreInteractions(engine);
    }
}
