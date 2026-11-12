package art.arcane.iris.modules.carve;

import art.arcane.iris.core.modules.CarveTestWorld;
import art.arcane.iris.engine.object.TestSettings;
import art.arcane.iris.modules.carve.field.CavePart;
import com.volmit.object.GeneratedObject;
import com.volmit.object.ObjectBlock;
import com.volmit.tooling.BlockParts;
import com.volmit.tooling.GraphNode;
import com.volmit.tooling.JsonText;
import com.volmit.tooling.NodeGraph;
import com.volmit.tooling.SceneEntry;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class CaveScenesTest {
    private static final String MIXED = """
            {"tunnel": {"width": {"min": 4, "max": 6}, "stretch": 48},
             "cavern": {"size": 40, "height": 18, "coverage": 0.2, "pillars": {"spacing": 14, "thickness": 4}}}
            """;
    private static final String TUNNELS_ONLY = "{\"tunnel\": {\"width\": {\"min\": 4, \"max\": 6}, \"stretch\": 48}}";

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
    public void theSceneIsTheCarvedSpaceAndEveryBlockBelongsToAPart() {
        GeneratedObject scene = view.scene(world, "deep/mixed", 0).orElseThrow();
        BlockParts parts = view.parts(world, "deep/mixed", 0).orElseThrow();

        assertFalse(scene.blocks().isEmpty());
        Set<Integer> partsSeen = new HashSet<>();
        for (ObjectBlock block : scene.blocks()) {
            int part = parts.partAt(block.position());
            assertNotEquals(BlockParts.NO_PART, part);
            partsSeen.add(part);
        }
        assertEquals(Set.of(CavePart.TUNNEL.ordinal(), CavePart.CAVERN.ordinal()), partsSeen);
    }

    @Test
    public void eachVariantIsTheCaveSomewhereElse() {
        GeneratedObject first = view.scene(world, "tunnels", 0).orElseThrow();

        assertEquals(first.blocks(), view.scene(world, "tunnels", 0).orElseThrow().blocks());
        assertNotEquals(first.blocks(), view.scene(world, "tunnels", 1).orElseThrow().blocks());
        assertTrue(view.scene(world, "tunnels", CavePreview.VARIANTS).isEmpty());
        assertTrue(view.scene(world, "missing", 0).isEmpty());
    }

    @Test
    public void aCaveFileReadsAsACaveWithItsTunnelAndCavernAndPillars() {
        NodeGraph graph = view.graph(world, "deep/mixed", JsonText.parse(MIXED));

        GraphNode cave = graph.roots().getFirst();
        assertEquals("mixed", cave.title());
        GraphNode tunnel = cave.children().get(0).node();
        GraphNode cavern = cave.children().get(1).node();
        assertEquals("Tunnel", tunnel.title());
        assertEquals(CavePart.TUNNEL.ordinal(), tunnel.part().getAsInt());
        assertEquals(CavePart.CAVERN.ordinal(), cavern.part().getAsInt());
        assertEquals("Pillars", cavern.children().getFirst().node().title());
    }

    @Test
    public void anUnwrittenPartIsShownSoSettingItWritesIt() {
        NodeGraph graph = view.graph(world, "tunnels", JsonText.parse(TUNNELS_ONLY));

        GraphNode cavern = graph.roots().getFirst().children().get(1).node();
        assertEquals("Cavern (unset)", cavern.title());
        assertTrue("a cavern the cave does not have draws nothing", cavern.part().isEmpty());
    }
}
