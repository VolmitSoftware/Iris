package art.arcane.iris.core.studio.scene;

import com.volmit.tooling.JsonPath;
import com.volmit.tooling.JsonText;
import org.junit.Test;

import static art.arcane.iris.core.studio.scene.TestPaths.path;
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
        JsonSplicer file = new JsonSplicer(JsonText.parse(TREE));
        return JsonText.parse(file.apply(edit.splices(file)));
    }

    private static String textAt(JsonText source, Object... steps) {
        return source.textOf(source.at(path(steps)).orElseThrow());
    }

    @Test
    public void aNewPartGrowsFromItsParentInAListWrittenForIt() {
        JsonText result = edited(new GraphEdit.Append(path("parts", 0, "children", 0, "children"), "{\"stem\": {\"length\": 6}}"));

        assertEquals("6", textAt(result, "parts", 0, "children", 0, "children", 0, "stem", "length"));
    }

    @Test
    public void deletingTheOnlyChildDropsItsList() {
        JsonText result = edited(new GraphEdit.Delete(path("parts", 0, "children", 1, "children", 0)));

        assertTrue(result.at(path("parts", 0, "children", 1, "children")).isEmpty());
        assertEquals("4", textAt(result, "parts", 0, "children", 1, "stem", "length"));
    }

    @Test
    public void aMovedPartTakesWhatGrowsFromItAlong() {
        JsonText result = edited(new GraphEdit.Move(path("parts", 0, "children", 1), path("parts", 0, "children", 0, "children")));

        assertEquals(1, ((JsonText.Arr) result.at(path("parts", 0, "children")).orElseThrow()).elements().size());
        assertEquals("1", textAt(result, "parts", 0, "children", 0, "children", 0, "children", 0, "blob", "radius"));
    }

    @Test
    public void aMovedPartIsIndentedForItsNewPlace() {
        JsonSplicer file = new JsonSplicer(JsonText.parse(TREE));

        String result = file.apply(new GraphEdit.Move(path("parts", 0, "children", 1), path("parts")).splices(file));

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
        JsonText result = edited(new GraphEdit.Duplicate(path("parts", 0, "children", 1)));

        assertEquals(3, ((JsonText.Arr) result.at(path("parts", 0, "children")).orElseThrow()).elements().size());
        assertEquals("1", textAt(result, "parts", 0, "children", 2, "children", 0, "blob", "radius"));
    }

    @Test
    public void settingWritesTheMemberAndAnObjectThatIsMissing() {
        JsonText lean = edited(new GraphEdit.Set(path("parts", 0, "stem"), "lean", "7"));
        JsonText offset = edited(new GraphEdit.Set(path("parts", 0, "offset"), "y", "2"));

        assertEquals("7", textAt(lean, "parts", 0, "stem", "lean"));
        assertEquals("2", textAt(offset, "parts", 0, "offset", "y"));
    }

    @Test
    public void unsettingWhatIsNotWrittenChangesNothing() {
        JsonSplicer file = new JsonSplicer(JsonText.parse(TREE));

        assertTrue(new GraphEdit.Unset(path("parts", 0, "stem"), "lean").splices(file).isEmpty());
        assertTrue(edited(new GraphEdit.Unset(path("parts", 0, "stem"), "length")).at(path("parts", 0, "stem", "length")).isEmpty());
    }

    @Test
    public void aPartCanNotMoveIntoItselfOrWhereItIs() {
        JsonPath part = path("parts", 0, "children", 1);

        assertThrows(IllegalArgumentException.class, () -> new GraphEdit.Move(part, part.member("children")));
        assertThrows(IllegalArgumentException.class, () -> new GraphEdit.Move(part, path("parts", 0, "children")));
        assertThrows(IllegalArgumentException.class, () -> new GraphEdit.Move(path("parts", 0, "branches", "part"), path("parts")));
        assertThrows(IllegalArgumentException.class, () -> new GraphEdit.Set(JsonPath.ROOT, "variants", "eight"));
    }
}
