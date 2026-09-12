package art.arcane.iris.studio.workspace;

import art.arcane.iris.pack.schema.SchemaBuilder;

import art.arcane.iris.structure.nativegen.NativeStructureSuppression;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class NativeStructureSuppressionSchemaTest {
    @Test
    public void suppressionTypeAndValuesAreSchemaDescribed() throws NoSuchFieldException {
        assertNotNull(NativeStructureSuppression.class.getAnnotation(Description.class));
        assertNotNull(NativeStructureSuppression.class.getField("NONE").getAnnotation(Description.class));
        assertNotNull(NativeStructureSuppression.class.getField("REPLACE_SOURCE").getAnnotation(Description.class));

        JSONObject schema = new SchemaBuilder(SuppressionModel.class, null).construct();
        JSONObject suppression = schema.getJSONObject("properties").getJSONObject("suppression");
        String definitionKey = suppression.getString("$ref").substring("#/definitions/".length());
        JSONArray values = schema.getJSONObject("definitions")
                .getJSONObject(definitionKey)
                .getJSONArray("oneOf");

        assertEquals("NONE", values.getJSONObject(0).getString("const"));
        assertEquals("Leaves native generation enabled alongside this Iris structure placement.",
                values.getJSONObject(0).getString("description"));
        assertEquals("REPLACE_SOURCE", values.getJSONObject(1).getString("const"));
        assertEquals("Suppresses the structure's vanillaSource when used by a validated dimension-level replacement placement.",
                values.getJSONObject(1).getString("description"));
    }

    @Description("Schema model for native structure suppression.")
    public static class SuppressionModel {
        @Description("Native structure suppression behavior.")
        private NativeStructureSuppression suppression = NativeStructureSuppression.NONE;
    }
}
