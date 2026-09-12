package art.arcane.iris.studio.workspace;

import art.arcane.iris.pack.schema.SchemaBuilder;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.IrisRegistrant;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.noise.IrisExpression;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisStaticObject;
import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisStaticObjectSchemaTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private IrisPlatform previousPlatform;

    @Before
    public void bindPlatform() {
        previousPlatform = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        if (previousPlatform != null) {
            IrisPlatforms.unbind();
        }
        IrisPlatform platform = mock(IrisPlatform.class);
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(platform.registries()).thenReturn(registries);
        when(registries.blockTypeKeys()).thenReturn(List.of());
        IrisPlatforms.bind(platform);
    }

    @After
    public void restorePlatform() {
        IrisPlatforms.unbind();
        if (previousPlatform != null) {
            IrisPlatforms.bind(previousPlatform);
        }
    }

    @Test
    public void dimensionExposesAnOptionalTypedStaticObjectList() throws Exception {
        Field field = IrisDimension.class.getDeclaredField("staticObjects");

        assertEquals(IrisStaticObject.class, field.getAnnotation(ArrayType.class).type());
        assertTrue(new IrisDimension().getStaticObjects().isEmpty());
    }

    @Test
    public void schemaRequiresTheObjectAndOriginAndExposesFixedTransforms() {
        JSONObject schema = new SchemaBuilder(IrisStaticObject.class, schemaData()).construct();
        JSONObject properties = schema.getJSONObject("properties");
        JSONObject definitions = schema.getJSONObject("definitions");
        JSONObject rotation = definitions.getJSONObject(properties.getJSONObject("rotation")
                .getString("$ref").substring("#/definitions/".length())).getJSONObject("properties");

        assertEquals(2, schema.getJSONArray("required").length());
        assertEquals("object", schema.getJSONArray("required").getString(0));
        assertEquals("position", schema.getJSONArray("required").getString(1));
        assertEquals("#/definitions/erzobjects", properties.getJSONObject("object").getString("$ref"));
        assertEquals("landmarks/tower", definitions.getJSONObject("erzobjects").getJSONArray("enum").getString(0));
        assertEquals(IrisStaticObject.MINIMUM_SCALE, properties.getJSONObject("scale").getDouble("minimum"), 0D);
        assertEquals(IrisStaticObject.MAXIMUM_SCALE, properties.getJSONObject("scale").getDouble("maximum"), 0D);
        assertEquals("integer", properties.getJSONObject("seed").getString("type"));
        for (String axis : List.of("x", "y", "z")) {
            assertEquals("number", rotation.getJSONObject(axis).getString("type"));
            assertEquals(-360D, rotation.getJSONObject(axis).getDouble("minimum"), 0D);
            assertEquals(360D, rotation.getJSONObject(axis).getDouble("maximum"), 0D);
        }
        assertTrue(properties.has("edit"));
        assertTrue(properties.has("bore"));
        assertTrue(properties.has("smartBore"));
        assertFalse(properties.has("chance"));
        assertFalse(properties.has("density"));
    }

    @SuppressWarnings("unchecked")
    private static IrisData schemaData() {
        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisObject> objectLoader = mock(ResourceLoader.class);
        ResourceLoader<IrisExpression> expressionLoader = mock(ResourceLoader.class);
        ResourceLoader<IrisBlockData> blockLoader = mock(ResourceLoader.class);
        KMap<Class<? extends IrisRegistrant>, ResourceLoader<? extends IrisRegistrant>> loaders = new KMap<>();
        loaders.put(IrisObject.class, objectLoader);
        loaders.put(IrisExpression.class, expressionLoader);
        when(data.getLoaders()).thenReturn(loaders);
        when(data.getBlockLoader()).thenReturn(blockLoader);
        when(data.getPossibleSnippets(anyString())).thenReturn(new KList<>());
        when(objectLoader.getPossibleKeys()).thenReturn(new String[]{"landmarks/tower"});
        when(objectLoader.getFolderName()).thenReturn("objects");
        when(objectLoader.getResourceTypeName()).thenReturn("Object");
        when(expressionLoader.getPossibleKeys()).thenReturn(new String[0]);
        when(expressionLoader.getFolderName()).thenReturn("expressions");
        when(expressionLoader.getResourceTypeName()).thenReturn("Expression");
        when(blockLoader.getPossibleKeys()).thenReturn(new String[0]);
        return data;
    }
}
