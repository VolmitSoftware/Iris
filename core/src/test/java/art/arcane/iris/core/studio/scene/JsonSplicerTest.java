package art.arcane.iris.core.studio.scene;

import com.volmit.tooling.JsonText;
import org.junit.Test;

import java.util.List;

import static art.arcane.iris.core.studio.scene.TestPaths.path;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class JsonSplicerTest {
    private static final String TREE = """
            {
                "variants": 8,
                "parts": [
                    {
                        "stem": {"length": [16.0, 26.0], "radius": 0.5},
                        "children": [
                            {
                                "blob": {
                                    "radius": 3
                                }
                            }
                        ]
                    }
                ]
            }
            """;

    private final JsonSplicer file = new JsonSplicer(JsonText.parse(TREE));

    @Test
    public void replacingAValueKeepsTheRestOfTheText() {
        JsonText.Value length = file.at(path("parts", 0, "stem", "length")).orElseThrow();

        String edited = file.apply(List.of(file.replace(length, "[12, 20]")));

        assertEquals(TREE.replace("[16.0, 26.0]", "[12, 20]"), edited);
    }

    @Test
    public void aNewMemberFollowsAnInlineObjectsStyle() {
        JsonText.Obj stem = (JsonText.Obj) file.at(path("parts", 0, "stem")).orElseThrow();

        String edited = file.apply(List.of(file.put(stem, "lean", "7")));

        assertTrue(edited.contains("\"stem\": {\"length\": [16.0, 26.0], \"radius\": 0.5, \"lean\": 7},"));
    }

    @Test
    public void aNewMemberFollowsAMultilineObjectsStyle() {
        JsonText.Obj blob = (JsonText.Obj) file.at(path("parts", 0, "children", 0, "blob")).orElseThrow();

        String edited = file.apply(List.of(file.put(blob, "depth", "0")));

        assertTrue(edited.contains("\"radius\": 3,\n                        \"depth\": 0\n"));
    }

    @Test
    public void removingMembersLeavesValidJson() {
        JsonText.Obj stem = (JsonText.Obj) file.at(path("parts", 0, "stem")).orElseThrow();

        String withoutFirst = file.apply(List.of(file.remove(stem, "length")));
        String withoutLast = file.apply(List.of(file.remove(stem, "radius")));

        assertTrue(withoutFirst.contains("\"stem\": {\"radius\": 0.5},"));
        assertTrue(withoutLast.contains("\"stem\": {\"length\": [16.0, 26.0]},"));
        assertTrue(JsonText.isValue(withoutFirst));
        assertTrue(JsonText.isValue(withoutLast));
    }

    @Test
    public void aMovedSubtreeIsIndentedForItsNewPlace() {
        JsonText.Arr children = (JsonText.Arr) file.at(path("parts", 0, "children")).orElseThrow();
        JsonText.Arr parts = (JsonText.Arr) file.at(path("parts")).orElseThrow();
        JsonText.Obj parent = (JsonText.Obj) file.at(path("parts", 0)).orElseThrow();

        String edited = file.apply(List.of(file.remove(parent, "children"), file.append(parts, file.detachedTextOf(children.elements().getFirst()))));

        JsonText moved = JsonText.parse(edited);
        assertEquals("3", moved.textOf(moved.at(path("parts", 1, "blob", "radius")).orElseThrow()));
        assertTrue(edited.contains("\n        {\n            \"blob\": {\n                \"radius\": 3\n            }\n        }\n    ]"));
    }

    @Test
    public void quotesTextAndRefusesOverlappingEdits() {
        JsonText.Value variants = file.at(path("variants")).orElseThrow();

        assertEquals("\"a\\\"b\"", JsonSplicer.quote("a\"b"));
        assertThrows(IllegalArgumentException.class, () -> file.apply(List.of(file.replace(variants, "1"), file.replace(variants, "2"))));
    }
}
