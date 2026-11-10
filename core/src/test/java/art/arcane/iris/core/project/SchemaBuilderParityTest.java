/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.core.project;

import art.arcane.iris.builtin.structure.StructureSettings;
import art.arcane.iris.core.modules.StructureModelModule;
import art.arcane.iris.engine.framework.structure.StructureResources;
import art.arcane.iris.engine.mantle.components.StructureModule;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.object.material.IrisBlockData;
import art.arcane.iris.engine.object.noise.expression.IrisExpression;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.structure.IrisStructure;
import art.arcane.iris.engine.object.annotations.ArrayType;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MaxNumber;
import art.arcane.iris.engine.object.annotations.MinNumber;
import art.arcane.iris.engine.object.annotations.RegistryListBiome;
import art.arcane.iris.engine.object.annotations.RegistryListEnchantment;
import art.arcane.iris.engine.object.annotations.RegistryListEntityType;
import art.arcane.iris.engine.object.annotations.RegistryListFunction;
import art.arcane.iris.engine.object.annotations.RegistryListItemType;
import art.arcane.iris.engine.object.annotations.RegistryListPotionEffect;
import art.arcane.iris.engine.object.annotations.RegistryListSpecialEntity;
import art.arcane.iris.engine.object.annotations.RegistryListVanillaStructure;
import art.arcane.iris.engine.object.annotations.functions.LootTableKeyFunction;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.LogLevel;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBiomeWriter;
import art.arcane.iris.spi.PlatformBlockProperty;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformEntityType;
import art.arcane.iris.spi.PlatformItem;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.spi.PlatformScheduler;
import art.arcane.iris.spi.PlatformStructureHooks;
import art.arcane.iris.spi.PlatformWorld;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import art.arcane.iris.core.modules.PackModuleSet;
import art.arcane.iris.core.modules.ModuleResourceFixture;
import com.volmit.config.Level;
import com.volmit.IrisFeature;
import art.arcane.iris.core.modules.BuiltInPack;
import art.arcane.iris.core.modules.IrisModuleHost;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.config.Feature;
import com.volmit.host.LoadedPack;
import com.volmit.host.ModuleHost;
import com.volmit.host.schema.PackSchema;
import com.volmit.stage.Stage;
import com.volmit.vanilla.DecorationStep;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SchemaBuilderParityTest {
    private static final List<String> POTION_KEYS = List.of("minecraft:speed", "minecraft:slow_falling", "sniffer_mod:mega_boost");
    private static final List<String> ENCHANT_KEYS = List.of("minecraft:sharpness", "cool_mod:vorpal");
    private static final List<String> ITEM_KEYS = List.of("minecraft:stone", "minecraft:diamond_sword", "cool_mod:ruby");
    private static final List<String> ENTITY_KEYS = List.of("minecraft:zombie", "cool_mod:grizzly_bear");
    private static final List<String> STRUCTURE_KEYS = List.of("minecraft:monument", "minecraft:stronghold", "cool_mod:sky_temple");
    private static final List<String> BIOME_KEYS = List.of("minecraft:plains", "cool_mod:sky_meadow");
    private static final List<String> SPECIAL_ENTITY_KEYS = List.of("mythicmobs:skeleton_king");
    private static final List<String> LOOT_TABLE_KEYS = List.of("minecraft:chests/simple_dungeon", "cool_mod:chests/sky_temple");

    // Namespaced key first, then the legacy short form for the vanilla namespace only. A mod key is addressable
    // by its full key instead of a namespace-stripped path that could collide with vanilla content.
    private static final List<String> EXPECTED_POTIONS = List.of(
            "minecraft:speed", "SPEED", "minecraft:slow_falling", "SLOW_FALLING", "sniffer_mod:mega_boost");
    private static final List<String> EXPECTED_ENCHANTS = List.of(
            "minecraft:sharpness", "sharpness", "cool_mod:vorpal");
    private static final List<String> EXPECTED_BIOMES = List.of("minecraft:plains", "plains", "cool_mod:sky_meadow");
    private static final List<String> EXPECTED_ITEMS = List.of("stone", "diamond_sword", "cool_mod:ruby");
    private static final List<String> EXPECTED_ENTITIES = List.of("minecraft:zombie", "cool_mod:grizzly_bear");

    private static final String EXPECTED_COUNT_DESCRIPTION = "count\nThe count.\n\nInteger\n* Default Value is 5\n* Minimum allowed is 2\n* Maximum allowed is 9";
    private static final String EXPECTED_COUNT_HTML_DESCRIPTION = "<h>count</h><br>The count.<hr></hr><br><h>Integer</h><br>* Default Value is 5<br>* Minimum allowed is 2<br>* Maximum allowed is 9";

    private IrisPlatform previous;

    @Before
    public void bindFakePlatform() {
        previous = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        if (previous != null) {
            IrisPlatforms.unbind();
        }
        IrisPlatforms.bind(new FakePlatform(null));
    }

    @After
    public void restorePlatform() {
        IrisPlatforms.unbind();
        if (previous != null) {
            IrisPlatforms.bind(previous);
        }
    }

    @Test
    public void registryDependentEnumsMatchLegacyTransforms() {
        JSONObject definitions = new SchemaBuilder(RegistryModel.class, (IrisData) null).construct().getJSONObject("definitions");
        assertEquals(EXPECTED_POTIONS, enumValues(definitions, "enum-potion-effect-type"));
        assertEquals(EXPECTED_ENCHANTS, enumValues(definitions, "enum-enchantment"));
        assertEquals(EXPECTED_ITEMS, enumValues(definitions, "enum-item-type"));
        assertEquals(EXPECTED_ENTITIES, enumValues(definitions, "enum-entity-type"));
        assertEquals(EXPECTED_BIOMES, enumValues(definitions, "enum-biome-type"));
        assertEquals(SPECIAL_ENTITY_KEYS, enumValues(definitions, "enum-reg-specialentity"));
    }

    @Test
    public void registryIndependentPathsAreByteIdentical() {
        JSONObject schema = new SchemaBuilder(IndependentModel.class, (IrisData) null).construct();
        JSONObject count = schema.getJSONObject("properties").getJSONObject("count");
        assertEquals("integer", count.getString("type"));
        assertEquals(2, count.getInt("minimum"));
        assertEquals(9, count.getInt("maximum"));
        assertEquals(EXPECTED_COUNT_DESCRIPTION, count.getString("description"));
        assertEquals(EXPECTED_COUNT_HTML_DESCRIPTION, count.getString("x-intellij-html-description"));

        JSONObject flavor = schema.getJSONObject("properties").getJSONObject("flavor");
        assertEquals("string", flavor.getString("type"));
        assertEquals(List.of("ALPHA", "BETA"), enumValues(schema.getJSONObject("definitions"), flavorDefinitionKey()));
    }

    @Test
    public void structurePlacementSchemaIncludesFoundationSettingsAndOmitsUnsupportedTransforms() {
        JSONObject schema = new SchemaBuilder(IrisBiome.class, structureSchemaData()).construct();
        JSONObject structures = rootProperties(schema).getJSONObject("structures");
        JSONObject properties = schema.getJSONObject("definitions")
                .getJSONObject(definitionKey(structures.getJSONObject("items"))).getJSONObject("properties");
        JSONObject stilt = properties.getJSONObject("stilt");
        JSONObject stiltDefinition = schema.getJSONObject("definitions")
                .getJSONObject(stilt.getString("$ref").substring("#/definitions/".length()));
        JSONObject stiltProperties = stiltDefinition.getJSONObject("properties");
        JSONObject maxDepth = stiltProperties.getJSONObject("maxDepth");
        JSONObject terrain = properties.getJSONObject("terrain");
        JSONObject terrainDefinition = schema.getJSONObject("definitions")
                .getJSONObject(terrain.getString("$ref").substring("#/definitions/".length()));
        JSONObject terrainProperties = terrainDefinition.getJSONObject("properties");
        JSONObject terrainMode = terrainProperties.getJSONObject("mode");
        JSONObject carveShape = terrainProperties.getJSONObject("shape");
        JSONObject erosionStrength = terrainProperties.getJSONObject("erosionStrength");
        JSONObject erosionFrequency = terrainProperties.getJSONObject("erosionFrequency");
        JSONObject lobeFrequency = terrainProperties.getJSONObject("lobeFrequency");
        JSONObject lobeStrength = terrainProperties.getJSONObject("lobeStrength");

        assertTrue(properties.has("structures"));
        assertTrue(properties.has("nativeStructures"));
        assertTrue(properties.has("distribution"));
        assertTrue(terrain.getString("description").contains(
                "The editable structures backend supports SOURCE, PRESERVE, BORE, and FORCE_CARVE. "
                        + "The nativeStructures backend supports every terrain mode."));
        assertEquals(List.of("SOURCE", "PRESERVE", "BORE", "FORCE_CARVE", "VACUUM", "ENCASE"),
                values(terrainMode.getJSONArray("enum")));
        assertEquals(List.of("BOX", "ROUNDED", "ERODED"), values(carveShape.getJSONArray("enum")));
        assertEquals(0D, erosionStrength.getDouble("minimum"), 0D);
        assertEquals(1D, erosionStrength.getDouble("maximum"), 0D);
        assertEquals(0.001D, erosionFrequency.getDouble("minimum"), 0D);
        assertEquals(1D, erosionFrequency.getDouble("maximum"), 0D);
        assertEquals(0D, lobeFrequency.getDouble("minimum"), 0D);
        assertEquals(1D, lobeFrequency.getDouble("maximum"), 0D);
        assertEquals(0D, lobeStrength.getDouble("minimum"), 0D);
        assertEquals(1D, lobeStrength.getDouble("maximum"), 0D);
        assertEquals("module-art-arcane-iris-builtin-structure-structurestilts", definitionKey(stilt));
        assertTrue(stiltProperties.has("palette"));
        assertTrue(stiltProperties.has("supportNonOccluding"));
        assertEquals(1, maxDepth.getInt("minimum"));
        assertEquals(4064, maxDepth.getInt("maximum"));
        assertFalse(properties.has("rotation"));
        assertFalse(properties.has("translate"));
        assertEquals("@IrisStructureRef completes the keys of Iris's structure model",
                "#/definitions/erz" + StructureResources.STRUCTURES.folder(),
                properties.getJSONObject("structures").getJSONObject("items").getString("$ref"));
        assertEquals("@ResourceRef(PackBiome.class) completes the pack's biome keys", "#/definitions/erzbiomes",
                properties.getJSONObject("caveBiomes").getJSONObject("items").getString("$ref"));
        assertFalse(properties.has("scale"));
    }

    @Test
    public void settingsThatAreOneOptionsValueRenderThatValueAsTheKeysOnlyShape() throws IOException {
        JSONObject properties = rootProperties(new SchemaBuilder(IrisBiome.class, structureSchemaData()).construct());

        JSONObject deposits = properties.getJSONObject("deposits");
        JSONObject decorators = properties.getJSONObject("decorators");
        assertEquals("array", deposits.getString("type"));
        assertEquals("array", decorators.getString("type"));
        assertFalse(decorators.has("minItems"));
        assertEquals("module-art-arcane-iris-modules-deposit-depositgenerator", definitionKey(deposits.getJSONObject("items")));
        assertEquals("module-art-arcane-iris-builtin-decorant-decorator", definitionKey(decorators.getJSONObject("items")));
        for (JSONObject rooted : List.of(deposits, decorators)) {
            assertFalse("one shape, no alternatives", rooted.has("anyOf"));
            assertFalse("the list is the value, not a reference to an object of options", rooted.has("$ref"));
            assertTrue(rooted.getString("description").contains("from module"));
        }
        assertFalse("the feature ids are not keys", properties.has("iris:deposit"));
        assertFalse("the feature ids are not keys", properties.has("iris:decorant"));
    }

    @Test
    public void terrainSettingsRenderTheirOneValueUnderTheOldKeys() throws IOException {
        JSONObject biome = rootProperties(new SchemaBuilder(IrisBiome.class, structureSchemaData()).construct());
        JSONObject dimension = rootProperties(new SchemaBuilder(IrisDimension.class, structureSchemaData()).construct());

        JSONObject layers = biome.getJSONObject("layers");
        assertEquals("array", layers.getString("type"));
        assertEquals("module-art-arcane-iris-builtin-terrain-palettelayer", definitionKey(layers.getJSONObject("items")));
        assertEquals("a slab is one layer", "module-art-arcane-iris-builtin-terrain-palettelayer", definitionKey(biome.getJSONObject("slab")));
        assertEquals("module-art-arcane-iris-builtin-terrain-palettelayer", definitionKey(biome.getJSONObject("wall")));
        assertEquals("array", biome.getJSONObject("seaLayers").getString("type"));
        assertEquals("array", biome.getJSONObject("caveCeilingLayers").getString("type"));
        assertEquals("boolean", biome.getJSONObject("lockLayers").getString("type"));
        assertEquals("integer", biome.getJSONObject("lockLayersMax").getString("type"));
        assertEquals("module-com-volmit-block-palettespec", definitionKey(dimension.getJSONObject("rockPalette")));
        assertEquals("module-com-volmit-block-palettespec", definitionKey(dimension.getJSONObject("fluidPalette")));
        for (String key : List.of("postProcessing", "postProcessingSlabs", "postProcessingWalls")) {
            assertEquals(key, "boolean", dimension.getJSONObject(key).getString("type"));
        }
        for (String id : List.of("iris:terrain/layers", "iris:terrain/slab", "iris:terrain/lock-layers")) {
            assertFalse("the feature ids are not keys", biome.has(id));
        }
    }

    /** A native structure's jigsaw start pool names one of the platform's template pools, and studio lists them. */
    @Test
    public void jigsawStartPoolsCompleteThePlatformsTemplatePools() {
        PlatformStructureHooks hooks = mock(PlatformStructureHooks.class);
        when(hooks.templatePoolKeys()).thenReturn(List.of("minecraft:village/plains/town_centers"));
        IrisPlatforms.unbind();
        IrisPlatforms.bind(new FakePlatform(hooks));

        JSONObject schema = new SchemaBuilder(IrisDimension.class, structureSchemaData()).construct();
        JSONObject definitions = schema.getJSONObject("definitions");
        JSONObject placement = definitions.getJSONObject(definitionKey(rootProperties(schema).getJSONObject("structures")
                .getJSONObject("items"))).getJSONObject("properties");
        JSONObject source = definitions.getJSONObject(definitionKey(placement.getJSONObject("nativeStructures")
                .getJSONObject("items"))).getJSONObject("properties");
        JSONObject jigsaw = definitions.getJSONObject(definitionKey(source.getJSONObject("jigsaw")))
                .getJSONObject("properties");

        assertEquals("enum-native-jigsaw-pool", definitionKey(jigsaw.getJSONObject("startPool")));
        assertEquals(List.of("minecraft:village/plains/town_centers"),
                values(definitions.getJSONObject("enum-native-jigsaw-pool").getJSONArray("enum")));
    }

    /** {@code importedFeatures} comes from {@code iris:vanilla-features}; its steps keep the descriptions studio showed. */
    @Test
    public void importedFeaturesListTheDecorationStepsWithTheirDescriptions() throws IOException {
        JSONObject schema = new SchemaBuilder(IrisDimension.class, structureSchemaData()).construct();
        JSONObject definitions = schema.getJSONObject("definitions");
        JSONObject settings = definitions.getJSONObject(definitionKey(rootProperties(schema).getJSONObject("importedFeatures")))
                .getJSONObject("properties");

        assertEquals("boolean", settings.getJSONObject("enabled").getString("type"));
        assertFalse(settings.getJSONObject("disabled").has("minItems"));
        for (String field : List.of("steps", "disabledSteps")) {
            JSONObject items = settings.getJSONObject(field).getJSONObject("items");
            JSONObject steps = items.has("$ref") ? definitions.getJSONObject(definitionKey(items)) : items;
            assertFalse(settings.getJSONObject(field).has("minItems"));
            assertEquals(Arrays.stream(DecorationStep.values()).map(step -> "\"" + step.name() + "\"").collect(Collectors.joining(",")),
                    steps.getJSONArray("enum").join(","));
            JSONArray descriptions = steps.getJSONArray("enumDescriptions");
            for (int index = 0; index < descriptions.length(); index++) {
                assertFalse(field + " " + index, descriptions.getString(index).isBlank());
            }
        }
    }

    @Test
    public void structureSettingsRenderTheirFitsWithTheModulesDefinitions() throws IOException {
        JSONObject schema = new SchemaBuilder(IrisDimension.class, structureSchemaData(structureSettingsSchema())).construct();
        JSONObject definitions = schema.getJSONObject("definitions");
        JSONObject settings = definitions.getJSONObject(definitionKey(rootProperties(schema).getJSONObject("importedStructures")))
                .getJSONObject("properties");
        JSONObject adjustment = definitions.getJSONObject(definitionKey(settings.getJSONObject("adjustments").getJSONObject("items")))
                .getJSONObject("properties");
        JSONObject band = definitions.getJSONObject(definitionKey(adjustment.getJSONObject("yBand"))).getJSONObject("properties");
        JSONObject stilt = definitions.getJSONObject(definitionKey(adjustment.getJSONObject("stilt"))).getJSONObject("properties");
        JSONObject terrain = definitions.getJSONObject(definitionKey(adjustment.getJSONObject("terrain"))).getJSONObject("properties");

        assertEquals("module-art-arcane-iris-builtin-structure-structureyband", definitionKey(adjustment.getJSONObject("yBand")));
        assertEquals("boolean", adjustment.getJSONObject("preserveSourceY").getString("type"));
        assertEquals(-4064, band.getJSONObject("min").getInt("minimum"));
        assertEquals(4064, band.getJSONObject("max").getInt("maximum"));
        assertEquals(1, stilt.getJSONObject("spacing").getInt("minimum"));
        assertEquals(64, stilt.getJSONObject("spacing").getInt("maximum"));
        assertTrue(terrain.has("mode"));
        assertTrue(terrain.has("shape"));
        assertTrue(terrain.has("lobeFrequency"));
        assertTrue(terrain.has("lobeStrength"));
        assertTrue(terrain.has("encasePalette"));
        assertFalse(settings.getJSONObject("adjustments").has("minItems"));
    }

    /**
     * {@code disabled} and {@code adjustments[].match} accept family and namespace prefixes ("minecraft:village",
     * "nova_structures:"), so studio offers the registered keys and also accepts a key/prefix pattern; exact-key
     * options keep the strict list, and structure set options list the registered sets.
     */
    @Test
    public void structureSettingsAcceptPrefixesWhereTheyMatchFamilies() throws IOException {
        JSONObject schema = new SchemaBuilder(IrisDimension.class, structureSchemaData(structureSettingsSchema())).construct();
        JSONObject definitions = schema.getJSONObject("definitions");
        JSONObject settings = definitions.getJSONObject(definitionKey(rootProperties(schema).getJSONObject("importedStructures")))
                .getJSONObject("properties");
        JSONObject adjustment = definitions.getJSONObject(definitionKey(settings.getJSONObject("adjustments").getJSONObject("items")))
                .getJSONObject("properties");
        JSONObject frequency = definitions.getJSONObject(definitionKey(settings.getJSONObject("frequencyOverrides").getJSONObject("items")))
                .getJSONObject("properties");

        assertEquals("enum-vanilla-structure-or-prefix", definitionKey(settings.getJSONObject("disabled").getJSONObject("items")));
        assertEquals("enum-vanilla-structure-or-prefix", definitionKey(adjustment.getJSONObject("match").getJSONObject("items")));
        assertEquals("enum-vanilla-structure", definitionKey(settings.getJSONObject("disabledExact").getJSONObject("items")));
        assertEquals("the fake platform lists no structure sets, so the set key is plain text",
                "string", frequency.getJSONObject("structureSet").getString("type"));
        JSONArray anyOf = definitions.getJSONObject("enum-vanilla-structure-or-prefix").getJSONArray("anyOf");
        Pattern prefix = null;
        for (int index = 0; index < anyOf.length(); index++) {
            if (anyOf.getJSONObject(index).has("pattern")) {
                prefix = Pattern.compile(anyOf.getJSONObject(index).getString("pattern"));
            }
        }
        assertTrue("the prefix alternative is a pattern", prefix != null);
        assertTrue(prefix.matcher("minecraft:village").matches());
        assertTrue(prefix.matcher("nova_structures:").matches());
        assertTrue(prefix.matcher("towns_and_towers:exclusives/village_piglin").matches());
        assertFalse(prefix.matcher("village").matches());
        assertFalse(prefix.matcher("Nova Structures:tavern").matches());
    }

    @Test
    public void objectPlacementSchemaExposesSurfaceSupportSettings() {
        JSONObject properties = rootProperties(placementSchema(structureSchemaData()));
        JSONObject buffer = properties.getJSONObject("surfaceSupportBuffer");
        JSONObject depth = properties.getJSONObject("surfaceSupportDepth");

        assertEquals("integer", buffer.getString("type"));
        assertEquals(0, buffer.getInt("minimum"));
        assertEquals(16, buffer.getInt("maximum"));
        assertEquals("integer", depth.getString("type"));
        assertEquals(1, depth.getInt("minimum"));
        assertEquals(16, depth.getInt("maximum"));
        assertEquals("boolean", properties.getJSONObject("requireSurfaceSupport").getString("type"));
        assertFalse(properties.has("surfaceOpeningClearance"));
    }

    @Test
    public void objectPlacementSchemaDescribesTheClassesItHoldsFromTheirModules() {
        JSONObject schema = placementSchema(structureSchemaData());
        JSONObject definitions = schema.getJSONObject("definitions");
        JSONObject properties = rootProperties(schema);

        JSONObject clamp = definitions.getJSONObject(definitionKey(properties.getJSONObject("clamp")));
        JSONObject clampProperties = clamp.getJSONArray("anyOf").getJSONObject(0).getJSONObject("properties");
        assertEquals("module-art-arcane-iris-builtin-validate-objectlimit", definitionKey(properties.getJSONObject("clamp")));
        assertEquals(-2048, clampProperties.getJSONObject("minimumHeight").getInt("minimum"));
        assertEquals(2048, clampProperties.getJSONObject("maximumHeight").getInt("maximum"));
        assertEquals("The maximum height for placement (top of object)", clampProperties.getJSONObject("maximumHeight").getString("description"));
        assertEquals("a held type may be a snippet, as its Iris class was", "^snippet/",
                clamp.getJSONArray("anyOf").getJSONObject(1).getString("pattern"));

        JSONObject stilt = definitions.getJSONObject(definitionKey(properties.getJSONObject("stiltSettings")));
        JSONObject stiltProperties = stilt.getJSONArray("anyOf").getJSONObject(0).getJSONObject("properties");
        assertEquals(48, stiltProperties.getJSONObject("organicMaxScan").getInt("default"));
        JSONObject palette = definitions.getJSONObject(definitionKey(stiltProperties.getJSONObject("palette")));
        assertEquals("a held type's palette is Iris's palette", "obj-art-arcane-iris-engine-object-material-irismaterialpalette",
                definitionKey(palette.getJSONArray("anyOf").getJSONObject(0)));

        JSONObject edit = properties.getJSONObject("edit");
        JSONObject replace = definitions.getJSONObject(definitionKey(edit.getJSONObject("items")));
        assertEquals("module-art-arcane-iris-builtin-blocks-objectreplace", definitionKey(edit.getJSONObject("items")));
        assertFalse(replace.getJSONArray("anyOf").getJSONObject(0).getJSONObject("properties").getJSONObject("find").has("minItems"));
    }

    @Test
    public void objectPlacementSchemaRendersModuleSettingsUnderTheirKey() throws IOException {
        JSONObject schema = placementSchema(structureSchemaData(glowModuleSchema()));
        JSONObject properties = rootProperties(schema);
        String definition = definitionKey(properties.getJSONObject("glow"));
        JSONObject level = schema.getJSONObject("definitions").getJSONObject(definition)
                .getJSONObject("properties").getJSONObject("level");

        assertFalse("the feature id is not a key", properties.has("example:glow"));
        assertFalse("settings are their object only, no bare value form", properties.getJSONObject("glow").has("anyOf"));
        assertFalse("no alias constraint", schema.has("allOf"));
        assertEquals("integer", level.getString("type"));
        assertEquals(1, level.getInt("minimum"));
        assertEquals(15, level.getInt("maximum"));
        assertEquals(7, level.getInt("default"));
        assertEquals("How bright the glow is", level.getString("description"));
    }

    @Test
    public void moduleSettingsHoldingApiValueTypesUseIrisDescriptionsOfTheSameJson() throws IOException {
        JSONObject schema = placementSchema(structureSchemaData(tintModuleSchema()));
        JSONObject definitions = schema.getJSONObject("definitions");
        JSONObject tint = definitions.getJSONObject(definitionKey(rootProperties(schema).getJSONObject("tint")))
                .getJSONObject("properties");

        assertEquals("@MinItems renders as minItems", 1, tint.getJSONObject("palette").getInt("minItems"));
        assertFalse("options without it have none", tint.getJSONObject("style").has("minItems"));
        JSONObject spec = definitions.getJSONObject(definitionKey(tint.getJSONObject("palette").getJSONObject("items")));
        assertEquals("obj-art-arcane-iris-engine-object-material-irisblockdata", definitionKey(spec));
        assertTrue(definitions.getJSONObject("obj-art-arcane-iris-engine-object-material-irisblockdata").getJSONObject("properties").has("block"));
        JSONArray style = definitions.getJSONObject(definitionKey(tint.getJSONObject("style"))).getJSONArray("anyOf");
        assertEquals("obj-art-arcane-iris-engine-object-noise-irisgeneratorstyle", definitionKey(style.getJSONObject(0)));
        JSONArray ground = definitions.getJSONObject(definitionKey(tint.getJSONObject("ground"))).getJSONArray("anyOf");
        assertEquals("obj-art-arcane-iris-engine-object-material-irismaterialpalette", definitionKey(ground.getJSONObject(0)));
        assertEquals("a palette may be a snippet, as Iris's palettes may", "^snippet/", ground.getJSONObject(1).getString("pattern"));
        assertEquals("a style may be a snippet, as Iris's styles may", "^snippet/", style.getJSONObject(1).getString("pattern"));
        assertEquals("any JSON value", 0, tint.getJSONObject("extra").getJSONObject("additionalProperties").length());
        JSONObject mode = tint.getJSONObject("mode");
        assertEquals("\"SOFT\",\"HARD\"", mode.getJSONArray("enum").join(","));
        assertEquals("\"Blends into the ground\",\"\"", mode.getJSONArray("enumDescriptions").join(","));
    }

    @Test
    public void dimensionSchemaExposesSurfaceSupportDefaults() {
        JSONObject properties = rootProperties(
                new SchemaBuilder(IrisDimension.class, structureSchemaData()).construct());
        JSONObject buffer = properties.getJSONObject("objectSurfaceSupportBuffer");

        assertEquals("integer", buffer.getString("type"));
        assertEquals(0, buffer.getInt("minimum"));
        assertEquals(16, buffer.getInt("maximum"));
        assertEquals("boolean", properties.getJSONObject("requireObjectSurfaceSupport").getString("type"));
    }

    @Test
    public void jigsawStructureSchemaExposesBranchFailurePolicy() {
        JSONObject schema = new SchemaBuilder(IrisStructure.class, structureSchemaData()).construct();
        JSONObject policy = rootProperties(schema).getJSONObject("branchFailurePolicy");
        String policyDefinition = definitionKey(policy);

        assertEquals(List.of("FAIL_ASSEMBLY", "TERMINATE_BRANCH"),
                oneOfValues(schema.getJSONObject("definitions"), policyDefinition));
        assertTrue(policy.getString("description").contains("Default Value is FAIL_ASSEMBLY"));
    }

    @Test
    public void structurePolicyArraysUseLiveRegistryKeys() {
        JSONObject schema = new SchemaBuilder(StructureArrayModel.class, (IrisData) null).construct();
        JSONObject disabled = schema.getJSONObject("properties").getJSONObject("disabled");

        assertEquals("#/definitions/enum-vanilla-structure",
                disabled.getJSONObject("items").getString("$ref"));
        assertEquals(STRUCTURE_KEYS,
                enumValues(schema.getJSONObject("definitions"), "enum-vanilla-structure"));
    }

    @Test
    public void lootTableFunctionUsesPlatformRegistryKeys() {
        JSONObject schema = new SchemaBuilder(LootTableModel.class, (IrisData) null).construct();
        JSONObject lootTable = schema.getJSONObject("properties").getJSONObject("lootTable");

        assertEquals("#/definitions/loot-table-key", lootTable.getString("$ref"));
        assertEquals(LOOT_TABLE_KEYS,
                schema.getJSONObject("definitions").getJSONObject("loot-table-key").get("enum"));
    }

    private static IrisData structureSchemaData() {
        try {
            return structureSchemaData(schemaOfBuiltInsWith());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /** {@code modules} is what the pack's modules add to its JSON. */
    private static IrisData structureSchemaData(PackSchema modules) {
        IrisData data = mock(IrisData.class);
        LoadedPack pack = mock(LoadedPack.class);
        PackModuleSet moduleSet = mock(PackModuleSet.class);
        when(pack.schema()).thenReturn(modules);
        when(pack.catalog()).thenReturn(ModuleResourceFixture.catalogOf(new StructureModelModule(), new StructureModule()));
        when(moduleSet.pack()).thenReturn(pack);
        when(data.getModules()).thenReturn(moduleSet);
        ModuleResourceFixture.serve(data);
        ResourceLoader<IrisExpression> expressionLoader = mock(ResourceLoader.class);
        ResourceLoader<IrisBlockData> blockLoader = mock(ResourceLoader.class);
        ResourceLoader<IrisBiome> biomeLoader = mock(ResourceLoader.class);
        KMap<Class<? extends IrisRegistrant>, ResourceLoader<? extends IrisRegistrant>> loaders = new KMap<>();
        loaders.put(IrisExpression.class, expressionLoader);
        loaders.put(IrisBiome.class, biomeLoader);
        when(biomeLoader.getPossibleKeys()).thenReturn(new String[]{"plains"});
        when(biomeLoader.getFolderName()).thenReturn("biomes");
        when(biomeLoader.getResourceTypeName()).thenReturn("Biome");
        when(data.getBlockLoader()).thenReturn(blockLoader);
        when(data.getLoaders()).thenReturn(loaders);
        when(data.getPossibleSnippets(anyString())).thenReturn(new KList<>());
        when(blockLoader.getPossibleKeys()).thenReturn(new String[0]);
        when(expressionLoader.getPossibleKeys()).thenReturn(new String[0]);
        when(expressionLoader.getFolderName()).thenReturn("expressions");
        when(expressionLoader.getResourceTypeName()).thenReturn("Expression");
        return data;
    }

    private static JSONObject rootProperties(JSONObject schema) {
        if (schema.has("properties")) {
            return schema.getJSONObject("properties");
        }
        JSONArray anyOf = schema.getJSONArray("anyOf");
        for (int index = 0; index < anyOf.length(); index++) {
            JSONObject candidate = anyOf.getJSONObject(index);
            if (candidate.has("properties")) {
                return candidate.getJSONObject("properties");
            }
        }
        throw new IllegalStateException("schema exposes no properties");
    }

    /** The definition an API value type's reference leads to: the module's, which refers to Iris's own. */
    /** The schema of one object placement as studio writes it, reached through a biome's {@code objects}, with every definition. */
    private static JSONObject placementSchema(IrisData data) {
        JSONObject biome = new SchemaBuilder(IrisBiome.class, data).construct();
        JSONObject definitions = biome.getJSONObject("definitions");
        JSONObject objects = rootProperties(biome).getJSONObject("objects");
        if (objects.has("$ref")) {
            objects = rootProperties(definitions.getJSONObject(definitionKey(objects))).getJSONObject("objects");
        }
        JSONObject placement = definitions.getJSONObject(definitionKey(objects.getJSONObject("items")));
        return new JSONObject().put("definitions", definitions).put("properties", rootProperties(placement));
    }

    private static String definitionKey(JSONObject reference) {
        return reference.getString("$ref").substring("#/definitions/".length());
    }

    private static String flavorDefinitionKey() {
        return "enum-" + Flavor.class.getCanonicalName().replaceAll("\\Q.\\E", "-").toLowerCase();
    }

    private static List<String> enumValues(JSONObject definitions, String key) {
        return values(definitions.getJSONObject(key).getJSONArray("enum"));
    }

    private static List<String> values(JSONArray array) {
        List<String> values = new ArrayList<>(array.length());
        for (int index = 0; index < array.length(); index++) {
            values.add(array.getString(index));
        }
        return values;
    }

    private static List<String> oneOfValues(JSONObject definitions, String key) {
        JSONArray array = definitions.getJSONObject(key).getJSONArray("oneOf");
        List<String> values = new ArrayList<>(array.length());
        for (int index = 0; index < array.length(); index++) {
            values.add(array.getJSONObject(index).getString("const"));
        }
        return values;
    }

    @Desc("Registry dependent model.")
    public static class RegistryModel {
        @Desc("Potion field.")
        @RegistryListPotionEffect
        private String potion = "";

        @Desc("Enchant field.")
        @RegistryListEnchantment
        private String enchant = "";

        @Desc("Item field.")
        @RegistryListItemType
        private String item = "";

        @Desc("Entity field.")
        @RegistryListEntityType
        private String entity = "";

        @Desc("Biome field.")
        @RegistryListBiome
        private String biome = "";

        @Desc("Biome scatter field.")
        @ArrayType(type = String.class)
        @RegistryListBiome
        private KList<String> biomeScatter = new KList<>();

        @Desc("Special entity field.")
        @RegistryListSpecialEntity
        private String specialEntity = "";
    }

    @Desc("Independent model.")
    public static class IndependentModel {
        @Desc("The count.")
        @MinNumber(2)
        @MaxNumber(9)
        private int count = 5;

        @Desc("A flag.")
        private boolean flag = false;

        @Desc("A flavor.")
        private Flavor flavor = Flavor.ALPHA;
    }

    @Desc("Structure array model.")
    public static class StructureArrayModel {
        @Desc("Disabled structures.")
        @ArrayType(type = String.class)
        @RegistryListVanillaStructure
        private KList<String> disabled = new KList<>();
    }

    @Desc("Loot table model.")
    public static class LootTableModel {
        @Desc("Loot table.")
        @RegistryListFunction(LootTableKeyFunction.class)
        private String lootTable = "";
    }

    public enum Flavor {
        ALPHA,
        BETA
    }

    private static final class FakeRegistries implements PlatformRegistries {
        @Override
        public PlatformBlockState block(String key) {
            return null;
        }

        @Override
        public PlatformBlockState blockOrNull(String key) {
            return null;
        }

        @Override
        public PlatformBlockState blockOrNull(String key, boolean warn) {
            return null;
        }

        @Override
        public PlatformBlockState air() {
            return null;
        }

        @Override
        public boolean isInTag(PlatformBlockState block, String tag) {
            return false;
        }

        @Override
        public PlatformBlockState deepSlateOre(PlatformBlockState block, PlatformBlockState ore) {
            return null;
        }

        @Override
        public PlatformBiome biome(String key) {
            return null;
        }

        @Override
        public PlatformItem item(String key) {
            return null;
        }

        @Override
        public PlatformEntityType entity(String key) {
            return null;
        }

        @Override
        public List<String> blockKeys() {
            return List.of();
        }

        @Override
        public List<String> biomeKeys() {
            return BIOME_KEYS;
        }

        @Override
        public List<String> specialEntityKeys() {
            return SPECIAL_ENTITY_KEYS;
        }

        @Override
        public List<String> structureKeys() {
            return STRUCTURE_KEYS;
        }

        @Override
        public List<String> itemKeys() {
            return ITEM_KEYS;
        }

        @Override
        public List<String> entityKeys() {
            return ENTITY_KEYS;
        }

        @Override
        public List<String> blockTypeKeys() {
            return List.of();
        }

        @Override
        public List<String> enchantmentKeys() {
            return ENCHANT_KEYS;
        }

        @Override
        public List<String> potionEffectKeys() {
            return POTION_KEYS;
        }

        @Override
        public List<String> lootTableKeys() {
            return LOOT_TABLE_KEYS;
        }

        @Override
        public Map<String, List<PlatformBlockProperty>> blockStateProperties() {
            return Map.of();
        }
    }

    private static final class FakePlatform implements IrisPlatform {
        private final PlatformRegistries registries = new FakeRegistries();
        private final PlatformStructureHooks structureHooks;

        private FakePlatform(PlatformStructureHooks structureHooks) {
            this.structureHooks = structureHooks;
        }

        @Override
        public String platformName() {
            return "fake";
        }

        @Override
        public String minecraftVersion() {
            return "0.0.0";
        }

        @Override
        public PlatformRegistries registries() {
            return registries;
        }

        @Override
        public PlatformScheduler scheduler() {
            return null;
        }

        @Override
        public PlatformStructureHooks structureHooks() {
            return structureHooks;
        }

        @Override
        public PlatformBiomeWriter biomeWriter() {
            return null;
        }

        @Override
        public File dataFolder() {
            return new File(".");
        }

        @Override
        public File dataFile(String... path) {
            return new File(".");
        }

        @Override
        public File pluginJar() {
            return new File(".");
        }

        @Override
        public int irisVersionNumber() {
            return 0;
        }

        @Override
        public int minecraftVersionNumber() {
            return 0;
        }

        @Override
        public void callEvent(Object event) {
        }

        @Override
        public void dispatchConsoleCommand(String command) {
        }

        @Override
        public boolean spawnEntity(PlatformWorld world, String entityKey, double x, double y, double z) {
            return false;
        }

        @Override
        public void log(LogLevel level, String message) {
        }

        @Override
        public void msg(String message) {
        }

        @Override
        public void reportError(Throwable error) {
        }
    }

    private static PackSchema glowModuleSchema() throws IOException {
        return schemaOfBuiltInsWith(new GlowModule());
    }

    /** The schema of {@code iris:structure}'s settings alone, without the module's stages. */
    private static PackSchema structureSettingsSchema() throws IOException {
        Path root = Files.createTempDirectory("structure-pack");
        try (LoadedPack pack = LoadedPack.load(Files.createDirectory(root.resolve("pack")),
                ModuleHost.of(List.of(new StructureSettingsModule())).copyingJarsTo(Files.createDirectory(root.resolve("copies"))))) {
            return pack.schema();
        }
    }

    /** A module with {@code iris:structure}'s id whose only part is its settings. */
    public static final class StructureSettingsModule implements IrisModule {
        @Override
        public ModuleInfo info() {
            return ModuleInfo.of("iris:structure", "Structures", "1");
        }

        @Override
        public void setup(ModuleSetup setup) {
            setup.registry().registerSettings(StructureSettings.class);
        }
    }

    private static PackSchema tintModuleSchema() throws IOException {
        return schemaOfBuiltInsWith(new TintModule());
    }

    /** What the pack tells studio when Iris's built-in modules and {@code extra} are loaded: Iris's classes hold built-in types. */
    private static PackSchema schemaOfBuiltInsWith(IrisModule... extra) throws IOException {
        Path root = Files.createTempDirectory("schema-pack");
        List<IrisModule> modules = Stream.concat(BuiltInPack.allBuiltIns().stream(), Stream.of(extra)).toList();
        try (LoadedPack pack = LoadedPack.load(Files.createDirectory(root.resolve("pack")),
                ModuleHost.of(modules)
                        .exposing(IrisModuleHost.EXPOSED_PACKAGES.toArray(String[]::new))
                        .describingHostTypesIn(IrisModuleHost.HOST_TYPE_PACKAGES)
                        .copyingJarsTo(Files.createDirectory(root.resolve("copies"))))) {
            return pack.schema();
        }
    }

    @Feature(id = "example:tint", name = "Tint", key = "tint", targets = Level.PLACEMENT)
    public static final class TintSettings implements IrisFeature {
        @com.volmit.config.MinItems(1)
        private List<com.volmit.block.BlockSpec> palette = List.of();
        private com.volmit.noise.NoiseStyle style = com.volmit.noise.NoiseStyle.of("SIMPLEX");
        private com.volmit.block.PaletteSpec ground = com.volmit.block.PaletteSpec.of(List.of());
        private java.util.Map<String, Object> extra = java.util.Map.of();
        private TintMode mode = TintMode.SOFT;
    }

    public enum TintMode {
        @com.volmit.config.Desc("Blends into the ground") SOFT,
        HARD
    }

    public static final class TintModule implements IrisModule {
        @Override
        public ModuleInfo info() {
            return ModuleInfo.of("example:tint", "Tint", "1");
        }

        @Override
        public void setup(ModuleSetup setup) {
            setup.stages().define(Stage.placement("example:tint/paint")).when(TintSettings.class).run(context -> {
            });
        }
    }

    @Feature(id = "example:glow", name = "Glow", key = "glow", targets = Level.PLACEMENT)
    public static final class GlowSettings implements IrisFeature {
        @com.volmit.config.Desc("How bright the glow is")
        @com.volmit.config.MinNumber(1)
        @com.volmit.config.MaxNumber(15)
        private int level = 7;
    }

    public static final class GlowModule implements IrisModule {
        @Override
        public ModuleInfo info() {
            return ModuleInfo.of("example:glow", "Glow", "1");
        }

        @Override
        public void setup(ModuleSetup setup) {
            setup.stages().define(Stage.placement("example:glow/light")).when(GlowSettings.class).run(context -> {
            });
        }
    }
}
