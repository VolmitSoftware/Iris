package art.arcane.iris.studio.bridge;

import art.arcane.iris.studio.scene.JsonPath;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class GraphEditTest {
    private static final JsonPath PARTS = JsonPath.ROOT.member("parts");
    private static final String OBJECT = """
            {
              "parts": [
                {
                  "stem": {"length": 6},
                  "children": [{"blob": {"radius": 2}}]
                }
              ]
            }""";

    @Test
    public void settingAValueOfAnUnwrittenObjectWritesTheObject() {
        String edited = apply(OBJECT, new GraphEdit.Set(PARTS.element(0).member("offset"), "y", "2"));

        assertEquals(OBJECT.replace("\"children\": [{\"blob\": {\"radius\": 2}}]", "\"children\": [{\"blob\": {\"radius\": 2}}],\n      \"offset\": {\"y\": 2}"), edited);
    }

    @Test
    public void settingAValueOverwritesWhatIsWritten() {
        String edited = apply(OBJECT, new GraphEdit.Set(PARTS.element(0).member("stem"), "length", "[4, 8]"));

        assertEquals(OBJECT.replace("\"length\": 6", "\"length\": [4, 8]"), edited);
    }

    @Test
    public void aValueCantBeSetWhereNoObjectCanBeWritten() {
        assertThrows(IllegalArgumentException.class, () -> apply(OBJECT, new GraphEdit.Set(PARTS.element(4), "role", "\"wood\"")));
    }

    @Test
    public void aSetThatIsNotJsonIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new GraphEdit.Set(JsonPath.ROOT, "seed", "seven"));
    }

    @Test
    public void unsettingWhatIsNotWrittenChangesNothing() {
        assertEquals(OBJECT, apply(OBJECT, new GraphEdit.Unset(PARTS.element(0), "role")));
    }

    @Test
    public void unsettingAValueLeavesItsDefault() {
        String edited = apply(OBJECT, new GraphEdit.Unset(PARTS.element(0).member("stem"), "length"));

        assertEquals(OBJECT.replace("{\"length\": 6}", "{}"), edited);
    }

    @Test
    public void appendingToAnUnwrittenListWritesTheList() {
        String edited = apply("{\"parts\": []}", new GraphEdit.Append(JsonPath.ROOT.member("materials"), "{\"role\": \"main\"}"));

        assertEquals("{\"parts\": [], \"materials\": [{\"role\": \"main\"}]}", edited);
    }

    @Test
    public void anAddedItemStartsAsACopyOfTheLast() {
        String edited = apply("{\"rules\": [{\"block\": \"minecraft:moss_block\"}]}", new GraphEdit.AddItem(JsonPath.ROOT.member("rules")));

        assertEquals("{\"rules\": [{\"block\": \"minecraft:moss_block\"}, {\"block\": \"minecraft:moss_block\"}]}", edited);
    }

    @Test
    public void deletingTheOnlyElementRemovesItsList() {
        String edited = apply(OBJECT, new GraphEdit.Delete(PARTS.element(0).member("children").element(0)));

        assertEquals(OBJECT.replace(",\n      \"children\": [{\"blob\": {\"radius\": 2}}]", ""), edited);
    }

    @Test
    public void theFileItselfCantBeDeleted() {
        assertThrows(IllegalArgumentException.class, () -> new GraphEdit.Delete(JsonPath.ROOT));
    }

    @Test
    public void aDuplicateIsAppendedWithAllItHolds() {
        String edited = apply("{\"parts\": [{\"stem\": {}}, {\"blob\": {}}]}", new GraphEdit.Duplicate(PARTS.element(0)));

        assertEquals("{\"parts\": [{\"stem\": {}}, {\"blob\": {}}, {\"stem\": {}}]}", edited);
    }

    @Test
    public void onlyAnElementCanBeDuplicated() {
        assertThrows(IllegalArgumentException.class, () -> new GraphEdit.Duplicate(PARTS));
    }

    @Test
    public void aMovedPartJoinsTheEndOfItsNewList() {
        String source = "{\"parts\": [{\"stem\": {}, \"children\": [{\"blob\": {}}]}, {\"cluster\": {}}]}";

        String edited = apply(source, new GraphEdit.Move(PARTS.element(1), PARTS.element(0).member("children")));

        assertEquals("{\"parts\": [{\"stem\": {}, \"children\": [{\"blob\": {}}, {\"cluster\": {}}]}]}", edited);
    }

    @Test
    public void aPartCantMoveIntoItselfOrWhereItIs() {
        assertThrows(IllegalArgumentException.class, () -> new GraphEdit.Move(PARTS.element(0), PARTS.element(0).member("children")));
        assertThrows(IllegalArgumentException.class, () -> new GraphEdit.Move(PARTS.element(0), PARTS));
    }

    @Test
    public void anEditIsReadAsTheStudioSendsIt() {
        GraphEditRequest request = GraphEditRequest.parse("""
                {"revision": "r1", "edit": {"op": "move", "path": ["parts", 1], "toList": ["parts", 0, "children"]}}""");

        assertEquals("r1", request.revision());
        assertEquals(new GraphEdit.Move(PARTS.element(1), PARTS.element(0).member("children")), request.edit());
    }

    @Test
    public void anEditOfAnUnknownOpOrWithoutItsPathIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> GraphEditRequest.parse("{\"revision\": \"r\", \"edit\": {\"op\": \"rename\"}}"));
        assertThrows(IllegalArgumentException.class, () -> GraphEditRequest.parse("{\"revision\": \"r\", \"edit\": {\"op\": \"delete\"}}"));
        assertThrows(IllegalArgumentException.class, () -> GraphEditRequest.parse("not json"));
    }

    @Test
    public void aWholeFileIsReadAsTheStudioSendsIt() {
        GraphEditRequest.FileWrite write = GraphEditRequest.FileWrite.parse("{\"revision\": \"r2\", \"text\": \"{}\"}");

        assertEquals(new GraphEditRequest.FileWrite("r2", "{}"), write);
    }

    private static String apply(String source, GraphEdit edit) {
        JsonSplicer splicer = new JsonSplicer(JsonText.parse(source));
        String edited = splicer.apply(edit.splices(splicer));
        JsonText.parse(edited);
        return edited;
    }
}
