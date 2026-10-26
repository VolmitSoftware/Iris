package art.arcane.iris.studio.workspace;

import art.arcane.iris.pack.schema.SchemaBuilder;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.IrisRegistrant;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.noise.IrisExpression;
import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyPolicy;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformRegistries;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisRiverSchemaTest {
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
    public void hydrologySchemaExposesTiersPondsUndergroundAndFluids() {
        JSONObject schema = new SchemaBuilder(IrisHydrology.class, schemaData()).construct();
        JSONObject definitions = schema.getJSONObject("definitions");
        JSONObject properties = schema.getJSONObject("properties");
        JSONObject tier = referencedProperties(definitions, properties.getJSONObject("tiers").getJSONObject("items"));
        JSONObject fluid = referencedProperties(definitions, properties.getJSONObject("fluids").getJSONObject("items"));

        assertEquals("boolean", properties.getJSONObject("enabled").getString("type"));
        JSONObject density = properties.getJSONObject("density");
        assertEquals("number", density.getString("type"));
        assertEquals(0D, density.getDouble("minimum"), 0D);
        assertEquals(1D, density.getDouble("maximum"), 0D);
        assertTrue(density.getString("description").contains("Default Value is 1.0"));
        assertEquals("boolean", properties.getJSONObject("persist").getString("type"));
        assertEquals(0, properties.getJSONObject("prefetchRings").getInt("minimum"));
        assertEquals(4, properties.getJSONObject("prefetchRings").getInt("maximum"));
        for (String section : List.of("drainage", "ponds", "lakes", "deformation", "underground")) {
            assertTrue(section, properties.getJSONObject(section).has("$ref"));
        }
        assertEquals("array", tier.getJSONObject("halfWidth").getString("type"));
        assertEquals(2, tier.getJSONObject("waterfallMinimumDrop").getInt("minimum"));
        assertEquals(64, tier.getJSONObject("waterfallMinimumDrop").getInt("maximum"));
        assertEquals(64, tier.getJSONObject("bankBand").getInt("maximum"));
        assertEquals(0.05D, tier.getJSONObject("depthRatio").getDouble("minimum"), 0D);
        assertEquals(List.of("SMOOTH", "LINEAR", "CONCAVE", "TERRACED", "CLIFF"),
                enumValues(definitions, tier.getJSONObject("bankStyle")));
        assertEquals("string", tier.getJSONObject("fluid").getString("type"));
        assertEquals("array", tier.getJSONObject("decorators").getString("type"));
        assertTrue(tier.getJSONObject("decorators").getJSONObject("items").getString("$ref").startsWith("#/definitions/"));
        JSONObject ponds = referencedProperties(definitions, properties.getJSONObject("ponds"));
        assertEquals("array", ponds.getJSONObject("decorators").getString("type"));
        assertEquals(tier.getJSONObject("decorators").getJSONObject("items").getString("$ref"),
                ponds.getJSONObject("decorators").getJSONObject("items").getString("$ref"));
        assertEquals("string", fluid.getJSONObject("name").getString("type"));
        assertTrue(fluid.has("palette"));
    }

    @Test
    public void hydrologyPolicySchemaKeepsEveryOverlayOptionalAndTyped() {
        JSONObject schema = new SchemaBuilder(IrisHydrologyPolicy.class, schemaData()).construct();
        JSONObject definitions = schema.getJSONObject("definitions");
        JSONObject properties = schema.getJSONObject("properties");

        assertTrue(!schema.has("required") || schema.getJSONArray("required").length() == 0);
        assertEquals(0D, properties.getJSONObject("rainfall").getDouble("minimum"), 0D);
        assertEquals(16D, properties.getJSONObject("rainfall").getDouble("maximum"), 0D);
        assertEquals(16D, properties.getJSONObject("pondDensity").getDouble("maximum"), 0D);
        assertEquals(List.of("ALLOW", "BLOCK"), enumValues(definitions, properties.getJSONObject("streams")));
        assertEquals(List.of("ALLOW", "BLOCK"), enumValues(definitions, properties.getJSONObject("ponds")));
        assertEquals("string", properties.getJSONObject("fluid").getString("type"));
        assertEquals("array", properties.getJSONObject("shoreBiomes").getString("type"));
        assertEquals("#/definitions/erzbiomes",
                properties.getJSONObject("shoreBiomes").getJSONObject("items").getString("$ref"));
        assertEquals(0D, properties.getJSONObject("shoreBiomeWidth").getDouble("minimum"), 0D);
        assertEquals(31D, properties.getJSONObject("shoreBiomeWidth").getDouble("maximum"), 0D);
        assertEquals("array", properties.getJSONObject("decorators").getString("type"));
        assertTrue(properties.getJSONObject("decorators").getJSONObject("items").getString("$ref").startsWith("#/definitions/"));
    }

    @SuppressWarnings("unchecked")
    private static IrisData schemaData() {
        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisBiome> biomeLoader = mock(ResourceLoader.class);
        ResourceLoader<IrisExpression> expressionLoader = mock(ResourceLoader.class);
        ResourceLoader<IrisBlockData> blockLoader = mock(ResourceLoader.class);
        KMap<Class<? extends IrisRegistrant>, ResourceLoader<? extends IrisRegistrant>> loaders = new KMap<>();
        loaders.put(IrisBiome.class, biomeLoader);
        loaders.put(IrisExpression.class, expressionLoader);
        when(data.getBlockLoader()).thenReturn(blockLoader);
        when(data.getLoaders()).thenReturn(loaders);
        when(data.getPossibleSnippets(anyString())).thenReturn(new KList<>());
        when(biomeLoader.getPossibleKeys()).thenReturn(new String[]{"river/surface"});
        when(biomeLoader.getFolderName()).thenReturn("biomes");
        when(biomeLoader.getResourceTypeName()).thenReturn("Biome");
        when(blockLoader.getPossibleKeys()).thenReturn(new String[0]);
        when(expressionLoader.getPossibleKeys()).thenReturn(new String[0]);
        when(expressionLoader.getFolderName()).thenReturn("expressions");
        when(expressionLoader.getResourceTypeName()).thenReturn("Expression");
        return data;
    }

    private static JSONObject referencedProperties(JSONObject definitions, JSONObject reference) {
        String key = reference.getString("$ref").substring("#/definitions/".length());
        return definitions.getJSONObject(key).getJSONObject("properties");
    }

    private static List<String> enumValues(JSONObject definitions, JSONObject reference) {
        String key = reference.getString("$ref").substring("#/definitions/".length());
        JSONObject definition = definitions.getJSONObject(key);
        List<String> names = new ArrayList<>();
        if (definition.has("oneOf")) {
            // A described enum lists one {const, description} entry per value.
            JSONArray options = definition.getJSONArray("oneOf");
            for (int index = 0; index < options.length(); index++) {
                names.add(options.getJSONObject(index).getString("const"));
            }
            return names;
        }
        JSONArray values = definition.getJSONArray("enum");
        for (int index = 0; index < values.length(); index++) {
            names.add(values.getString(index));
        }
        return names;
    }
}
