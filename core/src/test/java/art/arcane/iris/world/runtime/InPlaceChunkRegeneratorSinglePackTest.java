package art.arcane.iris.world.runtime;

import art.arcane.iris.generation.runtime.GenerationSource;
import art.arcane.iris.generation.runtime.IrisEngine;
import org.junit.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyList;

public class InPlaceChunkRegeneratorSinglePackTest {
    @Test
    public void regenOnASinglePackStudioWorldLeavesGenerationOwnershipAlone() throws Exception {
        IrisEngine engine = mock(IrisEngine.class);
        GenerationSource source = mock(GenerationSource.class);
        when(engine.isStudio()).thenReturn(true);
        when(engine.getGenerationSource()).thenReturn(source);
        when(source.keepsPackVersions()).thenReturn(false);

        InPlaceChunkRegenerator.releaseForRegeneration(engine, List.of(new int[]{-72, -15}, new int[]{0, 0}));

        verify(source, never()).releaseForRegeneration(anyList());
    }
}
