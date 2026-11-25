package art.arcane.iris.studio.bridge;

import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.studio.scene.SceneEntry;
import art.arcane.iris.studio.scene.SceneRequest;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import org.junit.Test;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PackObjectsSceneViewTest {
    private final ResourceLoader<IrisObject> objects = mockObjectLoader();
    private final Engine engine = engineWith(objects);
    private final PackObjectsSceneView view = new PackObjectsSceneView();

    @Test
    public void objectFilesAreListedByFolderWithoutTheBakedProceduralObjects() {
        when(objects.getPossibleKeys()).thenReturn(new String[]{"trees/oak", "procedural/rocks/boulder", "well"});

        List<SceneEntry> entries = view.entries(engine);

        assertEquals(List.of(new SceneEntry("trees/oak", "oak", "trees", 1), new SceneEntry("well", "well", "", 1)), entries);
    }

    @Test
    public void anObjectIsDrawnFromItsBlocks() {
        IrisObject well = new IrisObject(2, 1, 1);
        well.getBlocks().put(new IrisBlockVector(0, 0, 0), state("minecraft:cobblestone"));
        well.getBlocks().put(new IrisBlockVector(1, 0, 0), state("minecraft:cobblestone"));
        when(objects.load("well")).thenReturn(well);

        assertEquals(2, view.scene(engine, request("well")).count());
    }

    @Test
    public void anObjectThatDoesNotLoadIsReportedAsMissing() {
        assertThrows(NoSuchElementException.class, () -> view.scene(engine, request("gone")));
    }

    @Test
    public void aProceduralObjectIsNotBakedAsAnObjectFile() {
        assertThrows(NoSuchElementException.class, () -> view.scene(engine, request("procedural/rocks/boulder")));
        verify(objects, never()).load("procedural/rocks/boulder");
    }

    private static SceneRequest request(String key) {
        return new SceneRequest(key, 0, 0, Map.of());
    }

    @SuppressWarnings("unchecked")
    private static ResourceLoader<IrisObject> mockObjectLoader() {
        return mock(ResourceLoader.class);
    }

    private static Engine engineWith(ResourceLoader<IrisObject> objects) {
        IrisData data = mock(IrisData.class);
        when(data.getObjectLoader()).thenReturn(objects);
        Engine engine = mock(Engine.class);
        when(engine.getData()).thenReturn(data);
        return engine;
    }

    private static NativeBlockState state(String key) {
        NativeBlockState state = mock(NativeBlockState.class);
        when(state.key()).thenReturn(key);
        return state;
    }
}
