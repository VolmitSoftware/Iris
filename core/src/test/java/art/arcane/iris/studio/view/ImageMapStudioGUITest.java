package art.arcane.iris.studio.view;

import art.arcane.iris.generation.runtime.Engine;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class ImageMapStudioGUITest {
    @Test
    public void exportRequestsHotloadWithoutMutatingTheActiveGenerationData() {
        Engine engine = mock(Engine.class);

        ImageMapStudioGUI.reloadActiveEngine(engine);

        verify(engine).hotloadSilently();
        verifyNoMoreInteractions(engine);
    }
}
