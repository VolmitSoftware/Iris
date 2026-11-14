package art.arcane.iris.pack.schema;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.cave.IrisCave;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.IrisRegistrant;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisCaveSchemaTest {
    private static final String CAVE_VALUE_DEFINITION = "obj-art-arcane-iris-generation-cave-cavevalue";

    @Test
    public void aCaveValueIsANumberWithinItsBoundsOrAnObject() {
        JSONObject schema = new SchemaBuilder(IrisCave.class, schemaData()).construct();

        JSONObject fade = schema.getJSONObject("properties").getJSONObject("fade");
        JSONArray alternatives = fade.getJSONArray("oneOf");

        assertFalse(fade.has("type"));
        assertEquals(2, alternatives.length());
        assertEquals("number", alternatives.getJSONObject(0).getString("type"));
        assertEquals(0D, alternatives.getJSONObject(0).getDouble("minimum"), 0D);
        assertEquals(128D, alternatives.getJSONObject(0).getDouble("maximum"), 0D);
        assertEquals("#/definitions/" + CAVE_VALUE_DEFINITION, alternatives.getJSONObject(1).getString("$ref"));
    }

    @Test
    public void theObjectFormOfACaveValueOffersMinMaxAndStyle() {
        JSONObject definition = new SchemaBuilder(IrisCave.class, schemaData()).construct()
                .getJSONObject("definitions").getJSONObject(CAVE_VALUE_DEFINITION);

        JSONObject properties = definition.getJSONObject("properties");

        assertTrue(properties.has("min"));
        assertTrue(properties.has("max"));
        assertTrue(properties.has("style"));
        assertEquals("min", definition.getJSONArray("required").getString(0));
    }

    @Test
    public void aTunnelCoverageIsAShareOrAnObject() {
        JSONObject schema = new SchemaBuilder(IrisCave.class, schemaData()).construct();

        JSONObject tunnel = schema.getJSONObject("definitions").getJSONObject("obj-art-arcane-iris-generation-cave-cavetunnel");
        JSONObject coverage = tunnel.getJSONObject("properties").getJSONObject("coverage");

        assertEquals(2, coverage.getJSONArray("oneOf").length());
        assertEquals("number", coverage.getJSONArray("oneOf").getJSONObject(0).getString("type"));
    }

    @Test
    public void dimensionsRegionsAndBiomesOfferTheCavesOfThePackToList() throws Exception {
        for (Class<?> listing : new Class<?>[]{IrisDimension.class, IrisRegion.class, IrisBiome.class}) {
            SchemaBuilder builder = new SchemaBuilder(listing, schemaData());

            JSONObject caves = new SchemaPropertyBuilder(builder).buildProperty(listing.getDeclaredField("caves"), listing);

            assertEquals(listing.getSimpleName(), "#/definitions/erzcaves", caves.getJSONObject("items").getString("$ref"));
            assertEquals(listing.getSimpleName(), "tunnels", builder.definitions.get("erzcaves").getJSONArray("enum").getString(0));
        }
    }

    private static IrisData schemaData() {
        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisCave> caveLoader = mock(ResourceLoader.class);
        KMap<Class<? extends IrisRegistrant>, ResourceLoader<? extends IrisRegistrant>> loaders = new KMap<>();
        loaders.put(IrisCave.class, caveLoader);
        when(data.getLoaders()).thenReturn(loaders);
        when(data.getPossibleSnippets(anyString())).thenReturn(new KList<String>());
        when(caveLoader.getPossibleKeys()).thenReturn(new String[]{"tunnels", "caverns"});
        when(caveLoader.getFolderName()).thenReturn("caves");
        when(caveLoader.getResourceTypeName()).thenReturn("Cave");
        return data;
    }
}
