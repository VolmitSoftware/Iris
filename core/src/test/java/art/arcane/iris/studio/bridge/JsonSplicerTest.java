package art.arcane.iris.studio.bridge;

import art.arcane.iris.studio.scene.JsonPath;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class JsonSplicerTest {
    private static final String COMMENTED = """
            {
              // How many to bake
              "variants": 6,
              "parts": [
                {"stem": {"length": 6}},
                {"blob": {"radius": 2}} /* the crown */
              ]
            }""";

    @Test
    public void commentsAreReadAsBlanks() {
        JsonText text = JsonText.parse(COMMENTED);

        assertTrue(text.at(JsonPath.ROOT.member("parts").element(1).member("blob")).isPresent());
        assertEquals("6", text.textOf(text.at(JsonPath.ROOT.member("variants")).orElseThrow()));
    }

    @Test
    public void textThatIsNotJsonIsRefusedNamingItsLine() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> JsonText.parse("{\n\"a\": 1,\n\"b\" 2}"));

        assertTrue(refused.getMessage().endsWith("on line 3"));
    }

    @Test
    public void gsonReadsTheSameValues() {
        JsonObject read = JsonText.parse("{\"n\": 1.50, \"on\": true, \"off\": null, \"s\": \"a\\nb\", \"list\": [1, {}]}").toGson().getAsJsonObject();

        assertEquals(1.5, read.get("n").getAsDouble(), 0);
        assertTrue(read.get("on").getAsBoolean());
        assertTrue(read.get("off").isJsonNull());
        assertEquals("a\nb", read.get("s").getAsString());
        assertEquals(2, read.getAsJsonArray("list").size());
    }

    @Test
    public void replacingAValueLeavesTheCommentsAndLayout() {
        String edited = edit(COMMENTED, (splicer, text) -> splicer.put(object(text, JsonPath.ROOT), "variants", "12"));

        assertEquals(COMMENTED.replace("\"variants\": 6", "\"variants\": 12"), edited);
    }

    @Test
    public void aNewMemberOfAMultiLineObjectGoesOnItsOwnLine() {
        String edited = edit(COMMENTED, (splicer, text) -> splicer.put(object(text, JsonPath.ROOT), "seed", "7"));

        assertTrue(edited.endsWith("  ],\n  \"seed\": 7\n}"));
    }

    @Test
    public void aNewMemberOfAOneLineObjectStaysOnItsLine() {
        String edited = edit("{\"a\": 1}", (splicer, text) -> splicer.put(object(text, JsonPath.ROOT), "b", "2"));

        assertEquals("{\"a\": 1, \"b\": 2}", edited);
    }

    @Test
    public void aMemberOfAnEmptyObjectIsWrittenInsideIt() {
        String edited = edit("{\"stem\": {}}", (splicer, text) -> splicer.put(object(text, JsonPath.ROOT.member("stem")), "length", "4"));

        assertEquals("{\"stem\": {\"length\": 4}}", edited);
    }

    @Test
    public void removingAMemberTakesItsSeparatorWithIt() {
        assertEquals("{\"b\": 2, \"c\": 3}", edit("{\"a\": 1, \"b\": 2, \"c\": 3}", (splicer, text) -> splicer.remove(object(text, JsonPath.ROOT), "a")));
        assertEquals("{\"a\": 1, \"c\": 3}", edit("{\"a\": 1, \"b\": 2, \"c\": 3}", (splicer, text) -> splicer.remove(object(text, JsonPath.ROOT), "b")));
        assertEquals("{}", edit("{\"a\": 1}", (splicer, text) -> splicer.remove(object(text, JsonPath.ROOT), "a")));
    }

    @Test
    public void aMultiLineElementIsAppendedIndentedLikeItsNeighbours() {
        String edited = edit(COMMENTED, (splicer, text) -> splicer.append(array(text, JsonPath.ROOT.member("parts")), "{\n  \"blob\": {}\n}"));

        assertTrue(edited.contains("{\"blob\": {\"radius\": 2}},\n    {\n      \"blob\": {}\n    } /* the crown */\n  ]"));
    }

    @Test
    public void elementsAreAppendedToOneLineAndEmptyArrays() {
        assertEquals("[1, 2]", edit("[1]", (splicer, text) -> splicer.append(array(text, JsonPath.ROOT), "2")));
        assertEquals("[2]", edit("[]", (splicer, text) -> splicer.append(array(text, JsonPath.ROOT), "2")));
    }

    @Test
    public void removingAnElementTakesItsSeparatorWithIt() {
        assertEquals("[2, 3]", edit("[1, 2, 3]", (splicer, text) -> splicer.removeElement(array(text, JsonPath.ROOT), 0)));
        assertEquals("[1, 2]", edit("[1, 2, 3]", (splicer, text) -> splicer.removeElement(array(text, JsonPath.ROOT), 2)));
        assertEquals("[]", edit("[1]", (splicer, text) -> splicer.removeElement(array(text, JsonPath.ROOT), 0)));
    }

    @Test
    public void overlappingSplicesAreRefused() {
        JsonText text = JsonText.parse("{\"a\": [1, 2]}");
        JsonSplicer splicer = new JsonSplicer(text);
        JsonText.Arr list = array(text, JsonPath.ROOT.member("a"));

        assertThrows(IllegalArgumentException.class, () -> splicer.apply(List.of(splicer.replace(list, "[]"), splicer.removeElement(list, 0))));
    }

    @Test
    public void textIsQuotedWithItsEscapes() {
        assertEquals("\"a\\\"b\\\\c\\nd\\u0001\"", JsonSplicer.quote("a\"b\\c\nd\u0001"));
        assertFalse(JsonText.isValue("{"));
    }

    private interface SpliceOf {
        JsonSplicer.Splice of(JsonSplicer splicer, JsonText text);
    }

    private static String edit(String source, SpliceOf splice) {
        JsonText text = JsonText.parse(source);
        JsonSplicer splicer = new JsonSplicer(text);
        String edited = splicer.apply(List.of(splice.of(splicer, text)));
        JsonText.parse(edited);
        return edited;
    }

    private static JsonText.Obj object(JsonText text, JsonPath path) {
        return (JsonText.Obj) text.at(path).orElseThrow();
    }

    private static JsonText.Arr array(JsonText text, JsonPath path) {
        return (JsonText.Arr) text.at(path).orElseThrow();
    }
}
