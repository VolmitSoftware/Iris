package art.arcane.iris.studio.workspace;

import art.arcane.iris.pack.schema.SchemaBuilder;

import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.world.IrisWorldBoundary;
import art.arcane.iris.world.IrisWorldBoundaryCenter;
import art.arcane.volmlib.util.json.JSONObject;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class IrisWorldBoundarySchemaTest {
    @Test
    public void dimensionExposesOptionalTypedBoundary() throws Exception {
        Field boundary = IrisDimension.class.getDeclaredField("worldBoundary");

        assertEquals(IrisWorldBoundary.class, boundary.getType());
        assertNull(new IrisDimension().getWorldBoundary());
    }

    @Test
    public void schemaExposesNativeBoundaryLimits() {
        JSONObject schema = new SchemaBuilder(IrisWorldBoundary.class, null).construct();
        JSONObject definitions = schema.getJSONObject("definitions");
        JSONObject properties = schema.getJSONObject("properties");
        JSONObject centerReference = properties.getJSONObject("center");
        String centerKey = centerReference.getString("$ref").substring("#/definitions/".length());
        JSONObject center = definitions.getJSONObject(centerKey).getJSONObject("properties");

        assertEquals("number", properties.getJSONObject("size").getString("type"));
        assertEquals(1D, properties.getJSONObject("size").getDouble("minimum"), 0D);
        assertEquals(IrisWorldBoundary.MAXIMUM_SIZE,
                properties.getJSONObject("size").getDouble("maximum"), 0D);
        assertEquals(Integer.MAX_VALUE, properties.getJSONObject("warningDistance").getInt("maximum"));
        assertEquals(-IrisWorldBoundary.MAXIMUM_CENTER, center.getJSONObject("x").getDouble("minimum"), 0D);
        assertEquals(IrisWorldBoundary.MAXIMUM_CENTER, center.getJSONObject("z").getDouble("maximum"), 0D);
        assertEquals(IrisWorldBoundaryCenter.class,
                new IrisWorldBoundary().getCenter().getClass());
    }
}
