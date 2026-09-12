package art.arcane.iris.studio.workspace;

import art.arcane.iris.pack.schema.SchemaBuilder;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.IrisRegistrant;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisDimensionStack;
import art.arcane.iris.generation.terrain.IrisDimensionStackBlend;
import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.iris.pack.schema.annotation.RegistryListResource;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import com.google.gson.Gson;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisDimensionStackSchemaTest {
    @Test
    public void dimensionExposesNullableStackConfiguration() throws Exception {
        IrisDimension dimension = new IrisDimension();
        Field stackField = IrisDimension.class.getDeclaredField("dimensionStack");
        Field dimensionsField = IrisDimensionStack.class.getDeclaredField("dimensions");

        assertNull(dimension.getDimensionStack());
        assertFalse(dimension.hasDimensionStack());
        dimension.setDimensionStack(new IrisDimensionStack());
        assertTrue(dimension.hasDimensionStack());
        assertEquals(IrisDimensionStack.class, stackField.getType());
        assertEquals(2, dimensionsField.getAnnotation(ArrayType.class).min());
        assertEquals(IrisDimension.class,
                dimensionsField.getAnnotation(RegistryListResource.class).value());
    }

    @Test
    public void schemaCarriesStackOrderAndNumericBounds() {
        JSONObject schema = new SchemaBuilder(IrisDimensionStack.class, schemaData()).construct();
        JSONObject properties = schema.getJSONObject("properties");
        JSONObject dimensions = properties.getJSONObject("dimensions");
        JSONObject spacer = properties.getJSONObject("spacer");
        JSONObject blend = referencedSchema(schema, properties.getJSONObject("blend"));
        JSONObject blendProperties = blend.getJSONObject("properties");
        JSONObject amplitude = blendProperties.getJSONObject("amplitude");

        assertEquals("array", dimensions.getString("type"));
        assertEquals(2, dimensions.getInt("minItems"));
        assertEquals(0, spacer.getInt("minimum"));
        assertEquals(256, spacer.getInt("maximum"));
        assertEquals(0, amplitude.getInt("minimum"));
        assertEquals(256, amplitude.getInt("maximum"));
        assertTrue(contains(schema.getJSONArray("required"), "dimensions"));
        assertFalse(blend.has("required"));
    }

    @Test
    public void gsonUsesTheCanonicalNestedShape() {
        IrisDimensionStack stack = new IrisDimensionStack()
                .setDimensions(new KList<>("sky", "main"))
                .setSpacer(24)
                .setBlend(new IrisDimensionStackBlend().setAmplitude(8));

        JSONObject json = new JSONObject(new Gson().toJson(stack));

        assertEquals(2, json.getJSONArray("dimensions").length());
        assertEquals("main", json.getJSONArray("dimensions").getString(1));
        assertEquals(24, json.getInt("spacer"));
        assertEquals(8, json.getJSONObject("blend").getInt("amplitude"));
        assertTrue(json.getJSONObject("blend").getJSONObject("style").has("style"));
    }

    private static JSONObject referencedSchema(JSONObject schema, JSONObject property) {
        String key = property.getString("$ref").substring("#/definitions/".length());
        return schema.getJSONObject("definitions").getJSONObject(key);
    }

    private static boolean contains(JSONArray values, String expected) {
        for (int index = 0; index < values.length(); index++) {
            if (expected.equals(values.optString(index, null))) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static IrisData schemaData() {
        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisDimension> dimensionLoader = mock(ResourceLoader.class);
        KMap<Class<? extends IrisRegistrant>, ResourceLoader<? extends IrisRegistrant>> loaders = new KMap<>();
        loaders.put(IrisDimension.class, dimensionLoader);
        when(data.getLoaders()).thenReturn(loaders);
        when(dimensionLoader.getFolderName()).thenReturn("dimensions");
        when(dimensionLoader.getResourceTypeName()).thenReturn("Dimension");
        when(dimensionLoader.getPossibleKeys()).thenReturn(new String[]{"sky", "main"});
        when(data.getPossibleSnippets(anyString())).thenReturn(new KList<>());
        return data;
    }
}
