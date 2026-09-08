package art.arcane.iris.core.project;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisBlockData;
import art.arcane.iris.engine.object.IrisExpression;
import art.arcane.iris.engine.object.IrisHydrology;
import art.arcane.iris.engine.object.IrisRiverPolicy;
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
import static org.junit.Assert.assertFalse;
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
    public void hydrologySchemaExposesTypedPhysicalHierarchy() {
        JSONObject schema = new SchemaBuilder(IrisHydrology.class, schemaData()).construct();
        JSONObject definitions = schema.getJSONObject("definitions");
        JSONObject properties = schema.getJSONObject("properties");
        JSONObject rivers = referencedProperties(definitions, properties.getJSONObject("rivers"));
        JSONObject routing = referencedProperties(definitions, rivers.getJSONObject("routing"));
        JSONObject geometry = referencedProperties(definitions, rivers.getJSONObject("geometry"));
        JSONObject drops = referencedProperties(definitions, geometry.getJSONObject("drops"));
        JSONObject surface = referencedProperties(definitions, rivers.getJSONObject("surface"));
        JSONObject sources = referencedProperties(definitions, surface.getJSONObject("sources"));
        JSONObject channel = referencedProperties(definitions, surface.getJSONObject("channel"));
        JSONObject banks = referencedProperties(definitions, surface.getJSONObject("banks"));
        JSONObject flow = referencedProperties(definitions, surface.getJSONObject("flow"));
        JSONObject mouths = referencedProperties(definitions, surface.getJSONObject("mouths"));
        JSONObject underground = referencedProperties(definitions, rivers.getJSONObject("underground"));
        JSONObject undergroundSources = referencedProperties(definitions, underground.getJSONObject("sources"));
        JSONObject grottos = referencedProperties(definitions, rivers.getJSONObject("grottos"));
        JSONObject coastal = referencedProperties(definitions, grottos.getJSONObject("coastal"));
        JSONObject inland = referencedProperties(definitions, grottos.getJSONObject("inland"));
        JSONObject profiles = rivers.getJSONObject("profiles");
        JSONObject profile = referencedProperties(definitions, profiles.getJSONObject("items"));
        JSONObject deepFluids = properties.getJSONObject("deepFluids");
        JSONObject deepFluid = referencedProperties(definitions, deepFluids.getJSONObject("items"));

        assertFalse(routing.has("refinementSpacing"));
        assertFalse(routing.has("branching"));
        assertFalse(surface.has("hydraulics"));
        assertFalse(surface.has("ridgeTunnels"));
        assertFalse(channel.has("surfaceInset"));
        assertFalse(channel.has("terrainBlendWidth"));
        assertFalse(mouths.has("levelingDistance"));
        assertTrue(geometry.has("surface"));
        JSONObject surfaceShape = referencedProperties(definitions, geometry.getJSONObject("surface"));
        JSONObject undergroundShape = referencedProperties(definitions, geometry.getJSONObject("underground"));
        JSONObject grottoShape = referencedProperties(definitions, geometry.getJSONObject("grottos"));
        assertEquals("number", surfaceShape.getJSONObject("bedRoundness").getString("type"));
        assertEquals(1D, surfaceShape.getJSONObject("bedRoundness").getDouble("minimum"), 0D);
        assertEquals(6D, surfaceShape.getJSONObject("bedRoundness").getDouble("maximum"), 0D);
        assertEquals("number", surfaceShape.getJSONObject("bedRoughness").getString("type"));
        assertEquals(0D, surfaceShape.getJSONObject("bedRoughness").getDouble("minimum"), 0D);
        assertEquals(1D, surfaceShape.getJSONObject("bedRoughness").getDouble("maximum"), 0D);
        assertEquals("number", surfaceShape.getJSONObject("wallRoughness").getString("type"));
        assertEquals(0D, surfaceShape.getJSONObject("wallRoughness").getDouble("minimum"), 0D);
        assertEquals(1D, surfaceShape.getJSONObject("wallRoughness").getDouble("maximum"), 0D);
        assertEquals("integer", surfaceShape.getJSONObject("roughnessWavelength").getString("type"));
        assertEquals(3, surfaceShape.getJSONObject("roughnessWavelength").getInt("minimum"));
        assertEquals(128, surfaceShape.getJSONObject("roughnessWavelength").getInt("maximum"));
        assertSharedShapeBounds(surfaceShape);
        assertSharedShapeBounds(undergroundShape);
        assertSharedShapeBounds(grottoShape);
        assertEquals(0, drops.getJSONObject("undergroundCascadeRunPerBlock").getInt("minimum"));
        assertEquals(16, drops.getJSONObject("undergroundCascadeRunPerBlock").getInt("maximum"));

        assertEquals(8192, routing.getJSONObject("tileSize").getInt("maximum"));
        assertEquals(32768, routing.getJSONObject("maximumRouteLength").getInt("maximum"));
        assertEquals(32768, routing.getJSONObject("minimumSurfaceCourseLength").getInt("maximum"));
        assertEquals(32768, routing.getJSONObject("minimumUndergroundCourseLength").getInt("maximum"));
        assertEquals(8D, routing.getJSONObject("valleyPreference").getDouble("maximum"), 0D);
        assertEquals(128D, routing.getJSONObject("uphillPenalty").getDouble("maximum"), 0D);
        assertEquals(16D, routing.getJSONObject("slopePenalty").getDouble("maximum"), 0D);
        assertEquals(1D, routing.getJSONObject("confluenceAttraction").getDouble("maximum"), 0D);
        JSONObject meanders = referencedProperties(definitions, geometry.getJSONObject("meanders"));
        assertEquals(10D, meanders.getJSONObject("maximumTurnDegrees").getDouble("minimum"), 0D);
        assertEquals(List.of("SINKHOLE_GROTTO"),
                enumValues(definitions, routing.getJSONObject("inlandOutlets").getJSONObject("items")));
        assertEquals(64D, sources.getJSONObject("density").getDouble("maximum"), 0D);
        assertEquals(64D, undergroundSources.getJSONObject("density").getDouble("maximum"), 0D);
        assertEquals(8192, sources.getJSONObject("minimumSpacing").getInt("maximum"));
        assertEquals(8192, undergroundSources.getJSONObject("minimumSpacing").getInt("maximum"));
        assertEquals(16, drops.getJSONObject("cascadeRunPerBlock").getInt("maximum"));
        assertEquals(6D, drops.getJSONObject("cascadeExponent").getDouble("maximum"), 0D);
        assertEquals(4, drops.getJSONObject("maximumCascadeStep").getInt("maximum"));
        assertEquals(1D, drops.getJSONObject("flowWidthRatio").getDouble("maximum"), 0D);
        assertEquals(16, drops.getJSONObject("maximumFlowDepth").getInt("maximum"));
        assertEquals(4D, drops.getJSONObject("basinWidthRatio").getDouble("maximum"), 0D);
        assertEquals(32, drops.getJSONObject("maximumBasinDepth").getInt("maximum"));
        assertSnippetBackedObjectReference(channel.getJSONObject("width"));
        assertSnippetBackedObjectReference(channel.getJSONObject("depth"));
        assertEquals(0, channel.getJSONObject("sink").getInt("minimum"));
        assertEquals(3, channel.getJSONObject("sink").getInt("maximum"));
        assertFalse(channel.has("inset"));
        assertEquals(32, channel.getJSONObject("maximumIncision").getInt("maximum"));
        assertEquals(1D, channel.getJSONObject("roughness").getDouble("maximum"), 0D);
        assertEquals(64, channel.getJSONObject("roughnessWavelength").getInt("maximum"));
        assertEquals(0, channel.getJSONObject("smoothingRadius").getInt("minimum"));
        assertEquals(64, channel.getJSONObject("smoothingRadius").getInt("maximum"));
        assertEquals(0.2D, channel.getJSONObject("outlineMinimumRatio").getDouble("minimum"), 0D);
        assertEquals(1D, channel.getJSONObject("outlineMinimumRatio").getDouble("maximum"), 0D);
        assertEquals(1D, channel.getJSONObject("outlineMaximumRatio").getDouble("minimum"), 0D);
        assertEquals(3D, channel.getJSONObject("outlineMaximumRatio").getDouble("maximum"), 0D);
        assertEquals(0D, channel.getJSONObject("springExtraDepth").getDouble("minimum"), 0D);
        assertEquals(8D, channel.getJSONObject("springExtraDepth").getDouble("maximum"), 0D);
        assertFalse(banks.has("freeboard"));
        JSONObject erosion = referencedProperties(definitions, surface.getJSONObject("erosion"));
        assertEquals("boolean", erosion.getJSONObject("enabled").getString("type"));
        assertEquals(64, erosion.getJSONObject("smoothingRadius").getInt("maximum"));
        assertEquals(0.95D, erosion.getJSONObject("thalwegFraction").getDouble("maximum"), 0D);
        assertEquals(0.25D, erosion.getJSONObject("blendCurve").getDouble("minimum"), 0D);
        assertEquals(2D, erosion.getJSONObject("bedNoise").getDouble("maximum"), 0D);
        assertEquals(List.of("SMOOTH", "LINEAR", "CONCAVE", "TERRACED", "CLIFF"),
                enumValues(definitions, erosion.getJSONObject("style")));
        assertEquals(2, erosion.getJSONObject("terraceSteps").getInt("minimum"));
        assertEquals(16, erosion.getJSONObject("terraceSteps").getInt("maximum"));
        assertEquals(0D, erosion.getJSONObject("cliffFraction").getDouble("minimum"), 0D);
        assertEquals(1D, erosion.getJSONObject("cliffFraction").getDouble("maximum"), 0D);
        assertEquals(List.of("BOWL", "FLAT", "V", "U"),
                enumValues(definitions, erosion.getJSONObject("bedProfile")));
        JSONObject ponds = referencedProperties(definitions, surface.getJSONObject("ponds"));
        JSONObject sourcePond = referencedProperties(definitions, ponds.getJSONObject("source"));
        JSONObject terminalPond = referencedProperties(definitions, ponds.getJSONObject("terminal"));
        assertEquals("boolean", sourcePond.getJSONObject("enabled").getString("type"));
        assertEquals(32, sourcePond.getJSONObject("maximumRadius").getInt("maximum"));
        assertEquals(8, terminalPond.getJSONObject("depth").getInt("maximum"));
        assertEquals(0D, banks.getJSONObject("shoreWidth").getDouble("minimum"), 0D);
        assertEquals(16D, banks.getJSONObject("shoreWidth").getDouble("maximum"), 0D);
        assertEquals(0.5D, banks.getJSONObject("blendSlope").getDouble("minimum"), 0D);
        assertEquals(12D, banks.getJSONObject("blendSlope").getDouble("maximum"), 0D);
        assertEquals(64, banks.getJSONObject("minimumBlendWidth").getInt("maximum"));
        assertEquals(64, banks.getJSONObject("maximumBlendWidth").getInt("maximum"));
        assertEquals("boolean", banks.getJSONObject("exposeCutStrata").getString("type"));
        assertEquals(0D, banks.getJSONObject("shoreRise").getDouble("minimum"), 0D);
        assertEquals(4D, banks.getJSONObject("shoreRise").getDouble("maximum"), 0D);
        assertEquals(0D, banks.getJSONObject("blendBaseWidth").getDouble("minimum"), 0D);
        assertEquals(32D, banks.getJSONObject("blendBaseWidth").getDouble("maximum"), 0D);
        assertMaterialBounds(referencedProperties(definitions, banks.getJSONObject("shoreMaterial")));
        assertMaterialBounds(referencedProperties(definitions, banks.getJSONObject("bankMaterial")));
        JSONObject bed = referencedProperties(definitions, surface.getJSONObject("bed"));
        assertEquals("boolean", bed.getJSONObject("allowGravityBlocks").getString("type"));
        assertEquals(8, bed.getJSONObject("padding").getInt("maximum"));
        assertMaterialBounds(referencedProperties(definitions, bed.getJSONObject("material")));
        assertEquals(1, flow.getJSONObject("cascadeRun").getInt("minimum"));
        assertEquals(8, flow.getJSONObject("cascadeRun").getInt("maximum"));
        assertEquals(2, flow.getJSONObject("waterfallMinimumDrop").getInt("minimum"));
        assertEquals(32, flow.getJSONObject("waterfallMinimumDrop").getInt("maximum"));
        assertEquals(0D, flow.getJSONObject("waterfallThalwegFraction").getDouble("minimum"), 0D);
        assertEquals(0.95D, flow.getJSONObject("waterfallThalwegFraction").getDouble("maximum"), 0D);
        assertEquals(1, flow.getJSONObject("plungeBasinMinimumDrop").getInt("minimum"));
        assertEquals(8, flow.getJSONObject("plungeBasinMinimumDrop").getInt("maximum"));
        assertEquals(0D, flow.getJSONObject("plungeBasinLengthRatio").getDouble("minimum"), 0D);
        assertEquals(8D, flow.getJSONObject("plungeBasinLengthRatio").getDouble("maximum"), 0D);
        assertEquals(0, flow.getJSONObject("plungeBasinDepth").getInt("minimum"));
        assertEquals(8, flow.getJSONObject("plungeBasinDepth").getInt("maximum"));
        assertEquals(1D, mouths.getJSONObject("flareRatio").getDouble("minimum"), 0D);
        assertEquals(32, mouths.getJSONObject("maximumOceanApron").getInt("maximum"));
        assertEquals(0.05D, mouths.getJSONObject("inletCourseFraction").getDouble("minimum"), 0D);
        assertEquals(1D, mouths.getJSONObject("inletCourseFraction").getDouble("maximum"), 0D);
        assertEquals(0.1D, mouths.getJSONObject("inletRampSlope").getDouble("minimum"), 0D);
        assertEquals(4D, mouths.getJSONObject("inletRampSlope").getDouble("maximum"), 0D);
        assertEquals(16, underground.getJSONObject("mouthLevelingDistance").getInt("minimum"));
        assertEquals(512, underground.getJSONObject("mouthLevelingDistance").getInt("maximum"));
        assertSnippetBackedObjectReference(underground.getJSONObject("fluidLevel"));
        assertSnippetBackedObjectReference(underground.getJSONObject("channelWidth"));
        assertSnippetBackedObjectReference(underground.getJSONObject("headroom"));
        assertEquals(1, underground.getJSONObject("minimumRockCover").getInt("minimum"));
        assertEquals(64, underground.getJSONObject("minimumRockCover").getInt("maximum"));
        assertEquals(1, underground.getJSONObject("minimumFloorCover").getInt("minimum"));
        assertEquals(32, underground.getJSONObject("minimumFloorCover").getInt("maximum"));
        assertEquals(1, underground.getJSONObject("wideningSources").getInt("minimum"));
        assertEquals(64, underground.getJSONObject("wideningSources").getInt("maximum"));
        assertMaterialBounds(referencedProperties(definitions, underground.getJSONObject("bedMaterial")));

        assertGrottoBounds(coastal);
        assertGrottoBounds(inland);
        assertEquals("integer", coastal.getJSONObject("cliffMinimumHeight").getString("type"));
        assertEquals(0, coastal.getJSONObject("cliffMinimumHeight").getInt("minimum"));
        assertEquals(128, coastal.getJSONObject("cliffMinimumHeight").getInt("maximum"));
        assertEquals(0D, coastal.getJSONObject("cliffSlopeFactor").getDouble("minimum"), 0D);
        assertEquals(4D, coastal.getJSONObject("cliffSlopeFactor").getDouble("maximum"), 0D);
        JSONObject seaCaves = referencedProperties(definitions, coastal.getJSONObject("seaCaves"));
        assertEquals(128, seaCaves.getJSONObject("depth").getInt("maximum"));
        assertEquals(0D, seaCaves.getJSONObject("sweepJitterDegrees").getDouble("minimum"), 0D);
        assertEquals(90D, seaCaves.getJSONObject("sweepJitterDegrees").getDouble("maximum"), 0D);
        assertEquals("boolean", inland.getJSONObject("connectSurfaceRivers").getString("type"));
        assertEquals("array", profiles.getString("type"));
        assertEquals(1, profiles.getInt("minItems"));
        assertTrue(profile.has("id"));
        assertTrue(profile.has("fluidPalette"));

        assertEquals("array", deepFluids.getString("type"));
        assertTrue(deepFluid.has("id"));
        assertTrue(deepFluid.has("fluidPalette"));
        assertTrue(deepFluid.has("height"));
        assertEquals(64D, deepFluid.getJSONObject("density").getDouble("maximum"), 0D);
        assertEquals(8192, deepFluid.getJSONObject("spacing").getInt("maximum"));
        assertEquals(16, deepFluid.getJSONObject("spacing").getInt("minimum"));
        assertEquals(128, deepFluid.getJSONObject("horizontalRadius").getInt("maximum"));
        assertEquals(2, deepFluid.getJSONObject("horizontalRadius").getInt("minimum"));
        assertEquals(64, deepFluid.getJSONObject("verticalRadius").getInt("maximum"));
        assertEquals(2, deepFluid.getJSONObject("verticalRadius").getInt("minimum"));
        assertEquals(32, deepFluid.getJSONObject("channelWidth").getInt("maximum"));
        assertEquals(32, deepFluid.getJSONObject("depth").getInt("maximum"));
        assertEquals(63, deepFluid.getJSONObject("headroom").getInt("maximum"));
    }

    @Test
    public void riverPolicySchemaKeepsEveryOverlayOptionalAndTyped() {
        JSONObject schema = new SchemaBuilder(IrisRiverPolicy.class, schemaData()).construct();
        JSONObject definitions = schema.getJSONObject("definitions");
        JSONObject properties = schema.getJSONObject("properties");

        assertTrue(!schema.has("required") || schema.getJSONArray("required").length() == 0);
        assertEquals(List.of(
                        "DISABLED",
                        "TRANSIT_ONLY",
                        "NATURAL",
                        "PREFERRED_HEADWATER",
                        "REQUIRED_HEADWATER"
                ),
                enumValues(definitions, properties.getJSONObject("placement")));
        assertEquals(List.of("BLOCK", "AVOID", "ALLOW", "PREFER"),
                enumValues(definitions, properties.getJSONObject("routing")));
        assertEquals("boolean", properties.getJSONObject("outletAdmission").getString("type"));
        assertEquals("array", properties.getJSONObject("profiles").getString("type"));
        assertEquals("array", properties.getJSONObject("surfaceBiomes").getString("type"));
        assertEquals("array", properties.getJSONObject("mouthBiomes").getString("type"));
        assertEquals("array", properties.getJSONObject("shoreBiomes").getString("type"));
        assertEquals("array", properties.getJSONObject("bankBiomes").getString("type"));
        assertEquals("array", properties.getJSONObject("floodedCaveBiomes").getString("type"));
        assertEquals(16D, properties.getJSONObject("widthMultiplier").getDouble("maximum"), 0D);
        assertEquals(32D, properties.getJSONObject("shoreBiomeWidth").getDouble("maximum"), 0D);
        assertEquals(0D, properties.getJSONObject("shoreBiomeWidth").getDouble("minimum"), 0D);
        assertEquals("number", properties.getJSONObject("shoreWidth").getString("type"));
        assertEquals(0D, properties.getJSONObject("shoreWidth").getDouble("minimum"), 0D);
        assertEquals(16D, properties.getJSONObject("shoreWidth").getDouble("maximum"), 0D);
        assertEquals("boolean", properties.getJSONObject("erosion").getString("type"));
        assertEquals("boolean", properties.getJSONObject("confined").getString("type"));
        assertEquals(0D, properties.getJSONObject("incisionMultiplier").getDouble("minimum"), 0D);
        assertEquals(64D, properties.getJSONObject("routingMultiplier").getDouble("maximum"), 0D);
        assertEquals("#/definitions/erzbiomes",
                properties.getJSONObject("shoreBiomes").getJSONObject("items").getString("$ref"));
    }

    private static void assertSharedShapeBounds(JSONObject shape) {
        assertEquals(0.4D, shape.getJSONObject("radialBase").getDouble("minimum"), 0D);
        assertEquals(1.2D, shape.getJSONObject("radialBase").getDouble("maximum"), 0D);
        assertEquals(0.2D, shape.getJSONObject("radialMinimum").getDouble("minimum"), 0D);
        assertEquals(1D, shape.getJSONObject("radialMinimum").getDouble("maximum"), 0D);
        assertEquals(1D, shape.getJSONObject("radialMaximum").getDouble("minimum"), 0D);
        assertEquals(2D, shape.getJSONObject("radialMaximum").getDouble("maximum"), 0D);
        assertEquals(0D, shape.getJSONObject("primaryLobeStrength").getDouble("minimum"), 0D);
        assertEquals(0.5D, shape.getJSONObject("primaryLobeStrength").getDouble("maximum"), 0D);
        assertEquals(0D, shape.getJSONObject("detailLobeStrength").getDouble("minimum"), 0D);
        assertEquals(0.5D, shape.getJSONObject("detailLobeStrength").getDouble("maximum"), 0D);
        assertEquals(0D, shape.getJSONObject("ceilingRoughness").getDouble("minimum"), 0D);
        assertEquals(1D, shape.getJSONObject("ceilingRoughness").getDouble("maximum"), 0D);
        assertEquals(0.2D, shape.getJSONObject("aspectMinimum").getDouble("minimum"), 0D);
        assertEquals(1D, shape.getJSONObject("aspectMinimum").getDouble("maximum"), 0D);
        assertEquals(0D, shape.getJSONObject("aspectRange").getDouble("minimum"), 0D);
        assertEquals(0.8D, shape.getJSONObject("aspectRange").getDouble("maximum"), 0D);
    }

    private static void assertMaterialBounds(JSONObject material) {
        assertEquals("boolean", material.getJSONObject("enabled").getString("type"));
        assertSnippetBackedObjectReference(material.getJSONObject("palette"));
        assertEquals(1, material.getJSONObject("depth").getInt("minimum"));
        assertEquals(8, material.getJSONObject("depth").getInt("maximum"));
    }

    private static void assertGrottoBounds(JSONObject grotto) {
        assertEquals(1, grotto.getJSONObject("horizontalRadius").getInt("minimum"));
        assertEquals(1, grotto.getJSONObject("verticalRadius").getInt("minimum"));
        assertEquals(128, grotto.getJSONObject("horizontalRadius").getInt("maximum"));
        assertEquals(64, grotto.getJSONObject("verticalRadius").getInt("maximum"));
        assertEquals(63, grotto.getJSONObject("headroom").getInt("maximum"));
        assertEquals(1, grotto.getJSONObject("maximumVolume").getInt("minimum"));
        assertEquals(1048576, grotto.getJSONObject("maximumVolume").getInt("maximum"));
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

    private static void assertSnippetBackedObjectReference(JSONObject property) {
        JSONArray variants = property.getJSONArray("anyOf");
        boolean hasObjectReference = false;
        boolean hasSnippetReference = false;
        for (int index = 0; index < variants.length(); index++) {
            JSONObject variant = variants.getJSONObject(index);
            if ("object".equals(variant.getString("type")) && variant.has("$ref")) {
                hasObjectReference = true;
            }
            if ("string".equals(variant.getString("type")) && variant.has("$ref")) {
                hasSnippetReference = true;
            }
        }
        assertTrue(hasObjectReference);
        assertTrue(hasSnippetReference);
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
