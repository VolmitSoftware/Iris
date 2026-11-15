package art.arcane.iris.studio.bridge;

import art.arcane.iris.studio.scene.SceneBlocks;
import art.arcane.iris.studio.scene.SceneView;
import art.arcane.iris.studio.scene.SceneViews;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StudioScenesTest {
    @Test
    public void partsAreSentAsLittleEndianIntegersInBlockOrder() {
        SceneBlocks scene = new SceneBlocks();
        scene.add(0, 0, 0, "minecraft:stone", 3);
        scene.add(1, 0, 0, "minecraft:stone");
        scene.add(2, 0, 0, "minecraft:dirt", 0);

        ByteBuffer wire = ByteBuffer.wrap(StudioScenes.encodeParts(scene)).order(ByteOrder.LITTLE_ENDIAN);
        int[] parts = new int[wire.remaining() / Integer.BYTES];
        wire.asIntBuffer().get(parts);

        assertArrayEquals(new int[]{3, SceneBlocks.NO_PART, 0}, parts);
    }

    @Test
    public void theStudioListsEveryFeaturesViewInOrder() {
        List<String> listed = new StudioScenes().infos().stream().map(StudioScenes.SceneViewInfo::id).toList();

        assertEquals(SceneViews.all().stream().map(SceneView::id).toList(), listed);
    }

    @Test
    public void aViewIsListedWithWhatItCanDo() {
        StudioScenes.SceneViewInfo caves = new StudioScenes().infos().stream().filter(info -> info.id().equals("caves")).findFirst().orElseThrow();

        assertTrue(caves.parts());
        assertFalse(caves.editable());
        assertFalse(caves.creatable());
        assertFalse(caves.form());
        assertEquals(128, caves.sizes().opening());
    }
}
