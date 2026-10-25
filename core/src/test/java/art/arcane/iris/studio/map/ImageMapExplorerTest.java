package art.arcane.iris.studio.map;

import art.arcane.iris.generation.runtime.Engine;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class ImageMapExplorerTest {
    @Test
    public void exportRequestsHotloadWithoutMutatingTheActiveGenerationData() {
        Engine engine = mock(Engine.class);

        ImageMapExplorer.reloadActiveEngine(engine);

        verify(engine).hotloadSilently();
        verifyNoMoreInteractions(engine);
    }
}
