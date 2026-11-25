package art.arcane.iris.generation.procedural;

import art.arcane.iris.generation.procedural.part.PartOutline;
import art.arcane.iris.generation.procedural.part.ProceduralObject;
import art.arcane.iris.generation.procedural.part.ProceduralPart;
import art.arcane.iris.studio.scene.GraphNode;
import art.arcane.iris.studio.scene.JsonPath;
import art.arcane.iris.studio.scene.SceneBlocks;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProceduralObjectGraphTest {
    private static final Gson GSON = new Gson();
    private static final String TREE = """
            {
              "materials": [{"role": "wood", "block": "minecraft:oak_log"}, {"role": "leaves", "block": "minecraft:oak_leaves"}],
              "parts": [
                {
                  "stem": {"length": 6},
                  "children": [{"role": "leaves", "blob": {"radius": 3}}]
                },
                {"branches": {"count": 3, "part": {"stem": {"length": 2}}}}
              ],
              "decorations": [{"block": "minecraft:vine", "on": ["wood", "leaves"]}]
            }""";

    @Test
    public void theObjectHoldsItsPartsWithItsMaterialsAndDecorationsBeside() {
        List<GraphNode> roots = graphOf(TREE, TREE);

        assertEquals(List.of("Object", "Material", "Material", "Decoration"), roots.stream().map(GraphNode::kind).toList());
        GraphNode object = roots.get(0);
        assertEquals("oak", object.title());
        assertEquals(JsonPath.ROOT, object.path());
        assertEquals(Optional.of(JsonPath.ROOT.member("parts")), object.edits().childList());
        assertEquals(List.of("Stem", "Branches"), object.children().stream().map(link -> link.node().kind()).toList());
        assertEquals(List.of("leaves"), roots.get(2).roles());
        assertEquals(List.of("wood", "leaves"), roots.get(3).roles());
    }

    @Test
    public void aPartTakesItsParentsRoleUnlessItSetsOne() {
        GraphNode stem = graphOf(TREE, TREE).get(0).children().get(0).node();
        GraphNode crown = stem.children().get(0).node();

        assertEquals(List.of("wood"), stem.roles());
        assertEquals(List.of("leaves"), crown.roles());
        assertEquals(JsonPath.ROOT.member("parts").element(0).member("children").element(0), crown.path());
        assertTrue(crown.edits().movable());
    }

    @Test
    public void aPartShowsItsOwnSettingsAndItsShapesWithoutWhatAreNodes() {
        GraphNode branches = graphOf(TREE, TREE).get(0).children().get(1).node();

        assertEquals(List.of("Part", "Branches"), branches.settings().stream().map(GraphNode.NodeSettings::title).toList());
        assertTrue(branches.settings().get(0).shownAsNodes().contains("branches"));
        assertEquals(Set.of("part"),branches.settings().get(1).shownAsNodes());
        assertEquals(JsonPath.ROOT.member("parts").element(1).member("branches"), branches.settings().get(1).path());
    }

    @Test
    public void eachCopyOfABranchIsWiredAsSuchAndStaysInPlace() {
        GraphNode.Link copy = graphOf(TREE, TREE).get(0).children().get(1).node().children().get(0);

        assertEquals("each copy", copy.wire());
        assertEquals(JsonPath.ROOT.member("parts").element(1).member("branches").member("part"), copy.node().path());
        assertFalse(copy.node().edits().movable());
    }

    @Test
    public void partsAreNumberedAsTheOutlineOfWhatThePackReadNumbersThem() {
        ProceduralObject read = GSON.fromJson(TREE, ProceduralObject.class);
        PartOutline outline = PartOutline.of(read);
        List<GraphNode> roots = ProceduralObjectGraph.of("oak", parse(TREE), read);

        GraphNode stem = roots.get(0).children().get(0).node();
        GraphNode crown = stem.children().get(0).node();
        ProceduralPart readStem = read.parts().get(0);
        assertEquals(outline.idOf(readStem), stem.part());
        assertEquals(outline.idOf(readStem.children().get(0)), crown.part());
        assertEquals(SceneBlocks.NO_PART, roots.get(0).part());
    }

    @Test
    public void aPartThePackHasNotReadYetDrawsNoPart() {
        String older = """
                {"parts": [{"stem": {"length": 6}}]}""";

        List<GraphNode> roots = graphOf(TREE, older);

        assertEquals(SceneBlocks.NO_PART, roots.get(0).children().get(1).node().part());
    }

    @Test
    public void aPartWrittenAsASnippetIsShownButNotEdited() {
        String file = """
                {"parts": ["snippet/procedural-part/crown"]}""";
        String asThePackReadsIt = """
                {"parts": [{"blob": {"radius": 3}}]}""";

        GraphNode shared = graphOf(file, asThePackReadsIt).get(0).children().get(0).node();

        assertEquals("Shared part", shared.kind());
        assertEquals("snippet/procedural-part/crown", shared.title());
        assertTrue(shared.settings().isEmpty());
        assertEquals(GraphNode.Edits.NONE, shared.edits());
        assertEquals(0, shared.part());
    }

    @Test
    public void aMaterialWrittenAsASnippetIsShownButNotEdited() {
        String file = """
                {"materials": ["snippet/procedural-material/bark"]}""";

        GraphNode shared = graphOf(file, "{}").get(1);

        assertEquals("Shared material", shared.kind());
        assertTrue(shared.settings().isEmpty());
        assertEquals(GraphNode.Edits.NONE, shared.edits());
    }

    private static List<GraphNode> graphOf(String file, String asThePackReadsIt) {
        return ProceduralObjectGraph.of("oak", parse(file), GSON.fromJson(asThePackReadsIt, ProceduralObject.class));
    }

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
