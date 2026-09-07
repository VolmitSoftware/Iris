package art.arcane.iris.core.project;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.object.IrisBlockData;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockProperty;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.volmlib.util.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ExternalBlockSchemaTest {
    private static final String QUALIFIED_BLOCK = "craftengine:example/ores/copper";

    private IrisPlatform previous;

    @Before
    public void bindPlatform() {
        previous = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatforms.unbind();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.blockTypeKeys()).thenReturn(List.of(QUALIFIED_BLOCK));
        when(registries.blockStateProperties()).thenReturn(Map.of(QUALIFIED_BLOCK,
                List.of(new PlatformBlockProperty("axis", "string", "y", List.of("x", "y", "z"), null))));
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
    }

    @After
    public void restorePlatform() {
        IrisPlatforms.unbind();
        if (previous != null) {
            IrisPlatforms.bind(previous);
        }
    }

    @Test
    public void qualifiedBlockReferencesResolveToItsEnumAndPropertySchema() {
        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisBlockData> blocks = mock(ResourceLoader.class);
        when(blocks.getPossibleKeys()).thenReturn(new String[0]);
        when(data.getBlockLoader()).thenReturn(blocks);

        JSONObject schema = new SchemaBuilder(IrisBlockData.class, data).construct();
        JSONObject branch = schema.getJSONArray("allOf").getJSONObject(0)
                .getJSONArray("anyOf").getJSONObject(0);
        String enumReference = branch.getJSONObject("if").getJSONObject("properties")
                .getJSONObject("block").getString("$ref");
        String propertyReference = branch.getJSONObject("then").getJSONObject("properties")
                .getJSONObject("data").getString("$ref");

        assertEquals(QUALIFIED_BLOCK, resolve(schema, enumReference).getJSONArray("enum").getString(0));
        JSONObject axis = resolve(schema, propertyReference).getJSONObject("properties").getJSONObject("axis");
        assertEquals("string", axis.getString("type"));
        assertEquals("x", axis.getJSONArray("enum").getString(0));
        assertEquals("z", axis.getJSONArray("enum").getString(2));
    }

    private static JSONObject resolve(JSONObject schema, String reference) {
        JSONObject resolved = schema;
        for (String token : reference.substring(2).split("/")) {
            resolved = resolved.getJSONObject(token.replace("~1", "/").replace("~0", "~"));
        }
        return resolved;
    }
}
