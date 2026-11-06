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
                    {"stem": {"length": [16.0, 26.0], "radius": 0.5}}
                ]
            }
            """;
    private static final JsonPath STEM = JsonPath.ROOT.member("parts").element(0).member("stem");

    @Test
    void findsAValueByItsPathAndKeepsItsText() {
        JsonText source = JsonText.parse(TREE);

        assertEquals("[16.0, 26.0]", source.textOf(source.at(STEM.member("length")).orElseThrow()));
        assertInstanceOf(JsonText.Obj.class, source.at(STEM).orElseThrow());
    }

    @Test
    void readsStringsAndRefusesBrokenText() {
        JsonText source = JsonText.parse("{\"role\": \"wo\\\"od\"} // the wood");

        assertEquals(Optional.of("wo\"od"), ((JsonText.Literal) source.at(JsonPath.ROOT.member("role")).orElseThrow()).string());
        assertFalse(JsonText.isValue("{\"role\": }"));
        assertFalse(JsonText.isValue("2.8, 4.55"));
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> JsonText.parse("{\n\"a\": tru\n}"));
        assertEquals("Not a value: tru on line 2", refused.getMessage());
    }

    @Test
    void aPathOutsideTheFileFindsNothing() {
        JsonText source = JsonText.parse(TREE);

        assertTrue(source.at(JsonPath.ROOT.member("parts").element(3)).isEmpty());
        assertTrue(source.at(JsonPath.ROOT.member("variants").member("x")).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> JsonPath.ROOT.element(-1));
    }

    @Test
    void aPathKnowsItsStepsAndWhereItSits() {
        assertEquals(List.of(new JsonPath.Key("parts"), new JsonPath.Index(0), new JsonPath.Key("stem")), STEM.steps());
        assertEquals("parts.0.stem", STEM.toString());
        assertTrue(STEM.parent().isElement());
        assertFalse(STEM.isElement());
        assertTrue(STEM.startsWith(JsonPath.ROOT.member("parts")));
        assertThrows(IllegalStateException.class, JsonPath.ROOT::parent);
    }
}
