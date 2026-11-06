package com.volmit.tooling;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class JsonTextTest {
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

    @Test
    void replacingAValueKeepsTheRestOfTheText() {
        JsonText source = JsonText.parse(TREE);
        JsonText.Value length = source.at(JsonPath.of("parts", 0, "stem", "length")).orElseThrow();

        String edited = source.apply(List.of(source.replace(length, "[12, 20]")));

        assertEquals(TREE.replace("[16.0, 26.0]", "[12, 20]"), edited);
    }

    @Test
    void aNewMemberFollowsAnInlineObjectsStyle() {
        JsonText source = JsonText.parse(TREE);
        JsonText.Obj stem = (JsonText.Obj) source.at(JsonPath.of("parts", 0, "stem")).orElseThrow();

        String edited = source.apply(List.of(source.put(stem, "lean", "7")));

        assertTrue(edited.contains("\"stem\": {\"length\": [16.0, 26.0], \"radius\": 0.5, \"lean\": 7},"));
    }

    @Test
    void aNewMemberFollowsAMultilineObjectsStyle() {
        JsonText source = JsonText.parse(TREE);
        JsonText.Obj blob = (JsonText.Obj) source.at(JsonPath.of("parts", 0, "children", 0, "blob")).orElseThrow();

        String edited = source.apply(List.of(source.put(blob, "depth", "0")));

        assertTrue(edited.contains("\"radius\": 3,\n                        \"depth\": 0\n"));
    }

    @Test
    void removingMembersLeavesValidJson() {
        JsonText source = JsonText.parse(TREE);
        JsonText.Obj stem = (JsonText.Obj) source.at(JsonPath.of("parts", 0, "stem")).orElseThrow();

        String withoutFirst = source.apply(List.of(source.remove(stem, "length")));
        String withoutLast = source.apply(List.of(source.remove(stem, "radius")));

        assertTrue(withoutFirst.contains("\"stem\": {\"radius\": 0.5},"));
        assertTrue(withoutLast.contains("\"stem\": {\"length\": [16.0, 26.0]},"));
        assertTrue(JsonText.isValue(withoutFirst));
        assertTrue(JsonText.isValue(withoutLast));
    }

    @Test
    void aMovedSubtreeIsIndentedForItsNewPlace() {
        JsonText source = JsonText.parse(TREE);
        JsonText.Arr children = (JsonText.Arr) source.at(JsonPath.of("parts", 0, "children")).orElseThrow();
        JsonText.Arr parts = (JsonText.Arr) source.at(JsonPath.of("parts")).orElseThrow();
        JsonText.Obj parent = (JsonText.Obj) source.at(JsonPath.of("parts", 0)).orElseThrow();

        String edited = source.apply(List.of(source.remove(parent, "children"), source.append(parts, source.detachedTextOf(children.elements().getFirst()))));

        JsonText moved = JsonText.parse(edited);
        assertEquals("3", moved.textOf(moved.at(JsonPath.of("parts", 1, "blob", "radius")).orElseThrow()));
        assertTrue(edited.contains("\n        {\n            \"blob\": {\n                \"radius\": 3\n            }\n        }\n    ]"));
    }

    @Test
    void readsStringsAndRefusesBrokenText() {
        JsonText source = JsonText.parse("{\"role\": \"wo\\\"od\"} // the wood");

        assertEquals(Optional.of("wo\"od"), ((JsonText.Literal) source.at(JsonPath.of("role")).orElseThrow()).string());
        assertFalse(JsonText.isValue("{\"role\": }"));
        assertFalse(JsonText.isValue("2.8, 4.55"));
        assertEquals("\"a\\\"b\"", JsonText.quote("a\"b"));
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> JsonText.parse("{\n\"a\": tru\n}"));
        assertEquals("Not a value: tru on line 2", refused.getMessage());
    }

    @Test
    void aPathOutsideTheFileFindsNothing() {
        JsonText source = JsonText.parse(TREE);

        assertTrue(source.at(JsonPath.of("parts", 3)).isEmpty());
        assertTrue(source.at(JsonPath.of("variants", "x")).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> JsonPath.of("parts", -1));
    }
}
