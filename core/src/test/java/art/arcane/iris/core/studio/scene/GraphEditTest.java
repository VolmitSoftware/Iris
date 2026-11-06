package art.arcane.iris.core.studio.scene;

import com.volmit.tooling.JsonPath;
import com.volmit.tooling.JsonText;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class GraphEditTest {
    private static final String TREE = """
            {
                "parts": [
                    {
                        "stem": {"length": 20},
                        "children": [
                            {
                                "blob": {"radius": 3}
                            },
                            {
                                "stem": {"length": 4},
                                "children": [
                                    {"blob": {"radius": 1}}
                                ]
                            }
                        ]
                    }
                ]
            }
            """;

    private static JsonText edited(GraphEdit edit) {
        JsonText source = JsonText.parse(TREE);
        return JsonText.parse(source.apply(edit.splices(source)));
    }

    private static String textAt(JsonText source, Object... steps) {
        return source.textOf(source.at(JsonPath.of(steps)).orElseThrow());
    }

    @Test
    public void aNewPartGrowsFromItsParentInAListWrittenForIt() {
        JsonText result = edited(new GraphEdit.Append(JsonPath.of("parts", 0, "children", 0, "children"), "{\"stem\": {\"length\": 6}}"));

        assertEquals("6", textAt(result, "parts", 0, "children", 0, "children", 0, "stem", "length"));
    }

    @Test
    public void deletingTheOnlyChildDropsItsList() {
        JsonText result = edited(new GraphEdit.Delete(JsonPath.of("parts", 0, "children", 1, "children", 0)));

        assertTrue(result.at(JsonPath.of("parts", 0, "children", 1, "children")).isEmpty());
        assertEquals("4", textAt(result, "parts", 0, "children", 1, "stem", "length"));
    }

    @Test
    public void aMovedPartTakesWhatGrowsFromItAlong() {
        JsonText result = edited(new GraphEdit.Move(JsonPath.of("parts", 0, "children", 1), JsonPath.of("parts", 0, "children", 0, "children")));

        assertEquals(1, ((JsonText.Arr) result.at(JsonPath.of("parts", 0, "children")).orElseThrow()).elements().size());
        assertEquals("1", textAt(result, "parts", 0, "children", 0, "children", 0, "children", 0, "blob", "radius"));
    }

    @Test
    public void aMovedPartIsIndentedForItsNewPlace() {
        JsonText source = JsonText.parse(TREE);

        String result = source.apply(new GraphEdit.Move(JsonPath.of("parts", 0, "children", 1), JsonPath.of("parts")).splices(source));

        assertTrue(result, result.contains("""
                        },
                        {
                            "stem": {"length": 4},
                            "children": [
                                {"blob": {"radius": 1}}
                            ]
                        }
                    ]
                }"""));
    }

    @Test
    public void aDuplicateJoinsTheSameListWithWhatGrowsFromIt() {
        JsonText result = edited(new GraphEdit.Duplicate(JsonPath.of("parts", 0, "children", 1)));

        assertEquals(3, ((JsonText.Arr) result.at(JsonPath.of("parts", 0, "children")).orElseThrow()).elements().size());
        assertEquals("1", textAt(result, "parts", 0, "children", 2, "children", 0, "blob", "radius"));
    }

    @Test
    public void settingWritesTheMemberAndAnObjectThatIsMissing() {
        JsonText lean = edited(new GraphEdit.Set(JsonPath.of("parts", 0, "stem"), "lean", "7"));
        JsonText offset = edited(new GraphEdit.Set(JsonPath.of("parts", 0, "offset"), "y", "2"));

        assertEquals("7", textAt(lean, "parts", 0, "stem", "lean"));
        assertEquals("2", textAt(offset, "parts", 0, "offset", "y"));
    }

    @Test
    public void unsettingWhatIsNotWrittenChangesNothing() {
        JsonText source = JsonText.parse(TREE);

        assertTrue(new GraphEdit.Unset(JsonPath.of("parts", 0, "stem"), "lean").splices(source).isEmpty());
        assertTrue(edited(new GraphEdit.Unset(JsonPath.of("parts", 0, "stem"), "length")).at(JsonPath.of("parts", 0, "stem", "length")).isEmpty());
    }

    @Test
    public void aPartCanNotMoveIntoItselfOrWhereItIs() {
        JsonPath part = JsonPath.of("parts", 0, "children", 1);

        assertThrows(IllegalArgumentException.class, () -> new GraphEdit.Move(part, part.member("children")));
        assertThrows(IllegalArgumentException.class, () -> new GraphEdit.Move(part, JsonPath.of("parts", 0, "children")));
        assertThrows(IllegalArgumentException.class, () -> new GraphEdit.Move(JsonPath.of("parts", 0, "branches", "part"), JsonPath.of("parts")));
        assertThrows(IllegalArgumentException.class, () -> new GraphEdit.Set(JsonPath.ROOT, "variants", "eight"));
    }
}
