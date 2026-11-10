package art.arcane.iris.core.project;

import art.arcane.iris.engine.object.world.IrisWorldBoundary;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SnippetFileSchemaTest {
    @Test
    public void snippetFileSchemaTakesOneEntryOrAListOfThem() {
        JSONObject entry = new SchemaBuilder(IrisWorldBoundary.class, null).construct();
        JSONObject schema = new SchemaBuilder(IrisWorldBoundary.class, null).constructSnippet();

        JSONArray anyOf = schema.getJSONArray("anyOf");
        assertEquals(2, anyOf.length());
        assertEquals("object", anyOf.getJSONObject(0).getString("type"));
        assertEquals("array", anyOf.getJSONObject(1).getString("type"));
        assertEquals(anyOf.getJSONObject(0).toString(), anyOf.getJSONObject(1).getJSONObject("items").toString());
        assertEquals(entry.getJSONObject("properties").keySet(), anyOf.getJSONObject(0).getJSONObject("properties").keySet());
    }

    @Test
    public void snippetFileSchemaKeepsTheFileLevelKeysAtTheRoot() {
        JSONObject schema = new SchemaBuilder(IrisWorldBoundary.class, null).constructSnippet();

        assertTrue(schema.has("$schema"));
        assertTrue(schema.has("$id"));
        assertTrue(schema.has("definitions"));
        JSONObject single = schema.getJSONArray("anyOf").getJSONObject(0);
        assertFalse(single.has("$schema"));
        assertFalse(single.has("$id"));
        assertFalse(single.has("definitions"));
    }
}
