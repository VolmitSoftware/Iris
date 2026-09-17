package art.arcane.iris.core.gui;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.image.IrisImageMap;
import org.junit.Test;
import org.mockito.InOrder;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ImageMapStudioGUITest {
    @Test
    public void invalidatesPresetKeysBeforeHotloadingTheActiveEngine() {
        Engine engine = mock(Engine.class);
        IrisData data = mock(IrisData.class);
        @SuppressWarnings("unchecked")
        ResourceLoader<IrisImageMap> loader = mock(ResourceLoader.class);
        when(engine.getData()).thenReturn(data);
        when(data.getImageMapLoader()).thenReturn(loader);

        ImageMapStudioGUI.reloadActiveEngine(engine);

        InOrder order = inOrder(loader, engine);
        order.verify(loader).clearCache();
        order.verify(engine).hotloadSilently();
    }
}
