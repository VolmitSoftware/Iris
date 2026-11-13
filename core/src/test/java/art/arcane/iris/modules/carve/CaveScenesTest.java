package art.arcane.iris.modules.carve;

import art.arcane.iris.core.modules.CarveTestWorld;
import art.arcane.iris.engine.object.TestSettings;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.GeneratedObject;
import com.volmit.object.ObjectBlock;
import com.volmit.tooling.EditableSceneView;
import com.volmit.tooling.SceneEntry;
import com.volmit.tooling.SceneRequest;
import com.volmit.tooling.SceneView;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class CaveScenesTest {
    private static final String MIXED = """
            {"tunnel": {"width": {"min": 4, "max": 6}, "spacing": {"horizontal": 48, "vertical": 34}},
             "cavern": {"size": 40, "height": 18, "coverage": 0.2, "pillars": {"spacing": 14, "thickness": 4}}}
            """;
    private static final String TUNNELS_ONLY = "{\"tunnel\": {\"width\": {\"min\": 4, \"max\": 6}, \"spacing\": {\"horizontal\": 48, \"vertical\": 34}}}";
    private static final String TUNNEL_BLOCK = "minecraft:light_blue_concrete";
    private static final String CAVERN_BLOCK = "minecraft:orange_concrete";
    private static final int[][] NEIGHBOURS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private final CaveScenes view = new CaveScenes();
    private final CarveTestWorld world = new CarveTestWorld()
            .cave("deep/mixed", TestSettings.of(Cave.class, MIXED))
            .cave("tunnels", TestSettings.of(Cave.class, TUNNELS_ONLY));

    @Test
    public void everyCaveFileIsAnEntryUnderItsFolder() {
        List<SceneEntry> entries = view.entries(world);

        assertEquals(List.of("deep/mixed", "tunnels"), entries.stream().map(SceneEntry::key).toList());
        assertEquals(Optional.of("deep"), entries.getFirst().group());
        assertEquals("mixed", entries.getFirst().name());
        assertEquals(Optional.empty(), entries.get(1).group());
    }

    @Test
    public void tunnelsAndCavernsShowInTheirOwnColours() {
        GeneratedObject scene = scene("deep/mixed", 0, 128);

        Set<String> colours = new HashSet<>();
        scene.blocks().forEach(block -> colours.add(block.state().key()));
        assertEquals(Set.of(TUNNEL_BLOCK, CAVERN_BLOCK), colours);
    }

    // The inside of the carved space could not be seen, so it is left out: every block kept touches rock or the side.
    @Test
    public void onlyTheOutsideOfTheCarvedSpaceIsKept() {
        GeneratedObject scene = scene("deep/mixed", 0, 64);

        Set<BlockPosition> kept = new HashSet<>();
        scene.blocks().forEach(block -> kept.add(block.position()));
        for (ObjectBlock block : scene.blocks()) {
            assertTrue(block.position() + " is inside the carved space", touchesSomethingNotKept(block.position(), kept, scene));
        }
    }

    @Test
    public void theSceneIsAsWideAsTheSizeAskedFor() {
        assertEquals(64, scene("tunnels", 0, 64).bounds().maxX());
        assertEquals(256, scene("tunnels", 0, 256).bounds().maxX());
        assertEquals(128, view.scene(world, SceneRequest.of("tunnels", 0)).orElseThrow().bounds().maxX());
        assertTrue("a size the view does not offer", view.scene(world, new SceneRequest("tunnels", 0, OptionalInt.of(100))).isEmpty());
    }

    @Test
    public void eachVariantIsTheCaveSomewhereElse() {
        GeneratedObject first = scene("tunnels", 0, 128);

        assertEquals(first.blocks(), scene("tunnels", 0, 128).blocks());
        assertNotEquals(first.blocks(), scene("tunnels", 1, 128).blocks());
        assertTrue(view.scene(world, request("tunnels", CavePreview.VARIANTS, 128)).isEmpty());
        assertTrue(view.scene(world, request("missing", 0, 128)).isEmpty());
    }

    @Test
    public void theViewOnlyShowsCavesAndEditsNone() {
        SceneView registered = view;
        assertFalse(registered instanceof EditableSceneView);
    }

    private GeneratedObject scene(String key, int variant, int size) {
        return view.scene(world, request(key, variant, size)).orElseThrow();
    }

    private static SceneRequest request(String key, int variant, int size) {
        return new SceneRequest(key, variant, OptionalInt.of(size));
    }

    private static boolean touchesSomethingNotKept(BlockPosition position, Set<BlockPosition> kept, GeneratedObject scene) {
        for (int[] step : NEIGHBOURS) {
            BlockPosition beside = new BlockPosition(position.x() + step[0], position.y() + step[1], position.z() + step[2]);
            if (!scene.bounds().contains(beside.x(), beside.y(), beside.z()) || !kept.contains(beside)) {
                return true;
            }
        }
        return false;
    }
}
