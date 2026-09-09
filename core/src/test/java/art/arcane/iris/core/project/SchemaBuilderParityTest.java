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

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.engine.object.IrisBlockData;
import art.arcane.iris.engine.object.IrisExpression;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisJigsawPiece;
import art.arcane.iris.engine.object.IrisObjectPlacement;
import art.arcane.iris.engine.object.IrisStructure;
import art.arcane.iris.engine.object.IrisStructurePlacement;
import art.arcane.iris.engine.object.IrisStructureTerrain;
import art.arcane.iris.engine.object.IrisStructureTerrainMode;
import art.arcane.iris.engine.object.IrisVanillaStructureAdjustment;
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
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SchemaBuilderParityTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

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
        IrisPlatforms.bind(new FakePlatform());
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
        JSONObject schema = new SchemaBuilder(IrisStructurePlacement.class, structureSchemaData()).construct();
        JSONObject properties = schema.getJSONObject("properties");
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
        JSONObject flattenRange = terrainProperties.getJSONObject("flattenRange");
        JSONObject horizontalPadding = terrainProperties.getJSONObject("horizontalPadding");
        JSONObject carveShape = terrainProperties.getJSONObject("shape");
        JSONObject erosionStrength = terrainProperties.getJSONObject("erosionStrength");
        JSONObject erosionFrequency = terrainProperties.getJSONObject("erosionFrequency");
        JSONObject lobeFrequency = terrainProperties.getJSONObject("lobeFrequency");
        JSONObject lobeStrength = terrainProperties.getJSONObject("lobeStrength");
        String carveShapeDefinition = carveShape.getString("$ref")
                .substring("#/definitions/".length());
        String terrainModeDefinition = terrainMode.getString("$ref")
                .substring("#/definitions/".length());

        assertTrue(properties.has("structures"));
        assertTrue(properties.has("nativeStructures"));
        assertTrue(properties.has("distribution"));
        assertTrue(terrain.getString("description").contains(
                "The editable structures backend supports SOURCE, PRESERVE, BORE, and FORCE_CARVE. "
                        + "The nativeStructures backend supports every terrain mode."));
        assertEquals(List.of("SOURCE", "PRESERVE", "BORE", "FORCE_CARVE", "VACUUM", "FLATTEN", "ENCASE"),
                oneOfValues(schema.getJSONObject("definitions"), terrainModeDefinition));
        assertEquals("integer", flattenRange.getString("type"));
        assertEquals(0, flattenRange.getInt("minimum"));
        assertEquals(128, flattenRange.getInt("maximum"));
        assertEquals("integer", horizontalPadding.getString("type"));
        assertEquals(0, horizontalPadding.getInt("minimum"));
        assertEquals(128, horizontalPadding.getInt("maximum"));
        IrisStructureTerrain terrainDefaults = new IrisStructureTerrain();
        assertEquals(IrisStructureTerrainMode.SOURCE, terrainDefaults.resolvedMode());
        assertEquals(64, terrainDefaults.getFlattenRange());
        assertEquals(0, terrainDefaults.getHorizontalPadding());
        assertEquals(List.of("BOX", "ROUNDED", "ERODED"), oneOfValues(
                schema.getJSONObject("definitions"), carveShapeDefinition));
        assertEquals(0D, erosionStrength.getDouble("minimum"), 0D);
        assertEquals(1D, erosionStrength.getDouble("maximum"), 0D);
        assertEquals(0.001D, erosionFrequency.getDouble("minimum"), 0D);
        assertEquals(1D, erosionFrequency.getDouble("maximum"), 0D);
        assertEquals(0D, lobeFrequency.getDouble("minimum"), 0D);
        assertEquals(1D, lobeFrequency.getDouble("maximum"), 0D);
        assertEquals(0D, lobeStrength.getDouble("minimum"), 0D);
        assertEquals(1D, lobeStrength.getDouble("maximum"), 0D);
        assertEquals("object", stilt.getString("type"));
        assertTrue(stiltProperties.has("palette"));
        assertTrue(stiltProperties.has("supportNonOccluding"));
        assertEquals(1, maxDepth.getInt("minimum"));
        assertEquals(4064, maxDepth.getInt("maximum"));
        assertFalse(properties.has("rotation"));
        assertFalse(properties.has("translate"));
        assertFalse(properties.has("scale"));
    }

    @Test
    public void nativeAdjustmentSchemaExposesPlacementOverrides() {
        JSONObject schema = new SchemaBuilder(
                IrisVanillaStructureAdjustment.class, structureSchemaData()).construct();
        JSONObject definitions = schema.getJSONObject("definitions");
        JSONObject properties = schema.getJSONObject("properties");
        JSONObject band = definitions.getJSONObject(
                definitionKey(properties.getJSONObject("yBand"))).getJSONObject("properties");
        JSONObject stiltProperties = definitions.getJSONObject(
                definitionKey(properties.getJSONObject("stilt"))).getJSONObject("properties");
        JSONObject terrainProperties = definitions.getJSONObject(
                definitionKey(properties.getJSONObject("terrain"))).getJSONObject("properties");
        JSONObject spacing = stiltProperties.getJSONObject("spacing");

        assertEquals("boolean", properties.getJSONObject("preserveSourceY").getString("type"));
        assertEquals(-4064, band.getJSONObject("min").getInt("minimum"));
        assertEquals(4064, band.getJSONObject("min").getInt("maximum"));
        assertEquals(-4064, band.getJSONObject("max").getInt("minimum"));
        assertEquals(4064, band.getJSONObject("max").getInt("maximum"));
        assertEquals(1, spacing.getInt("minimum"));
        assertEquals(64, spacing.getInt("maximum"));
        assertTrue(terrainProperties.has("mode"));
        assertTrue(terrainProperties.has("shape"));
        assertTrue(terrainProperties.has("lobeFrequency"));
        assertTrue(terrainProperties.has("lobeStrength"));
        assertTrue(terrainProperties.has("encasePalette"));
        assertTrue(oneOfValues(definitions, definitionKey(terrainProperties.getJSONObject("mode")))
                .contains("FLATTEN"));
        assertEquals(0, terrainProperties.getJSONObject("flattenRange").getInt("minimum"));
        assertEquals(128, terrainProperties.getJSONObject("flattenRange").getInt("maximum"));
    }

    @Test
    public void objectPlacementSchemaExposesSurfaceSupportSettings() {
        JSONObject properties = rootProperties(
                new SchemaBuilder(IrisObjectPlacement.class, structureSchemaData()).construct());
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
    public void dimensionSchemaListsBlockFallbacksAsPlainObjectProperty() {
        JSONObject properties = rootProperties(
                new SchemaBuilder(IrisDimension.class, structureSchemaData()).construct());
        JSONObject fallbacks = properties.getJSONObject("blockFallbacks");

        assertEquals("object", fallbacks.getString("type"));
        assertFalse(fallbacks.has("!top"));
        assertFalse(fallbacks.has("anyOf"));
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
        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisStructure> structureLoader = mock(ResourceLoader.class);
        ResourceLoader<IrisJigsawPiece> pieceLoader = mock(ResourceLoader.class);
        ResourceLoader<IrisExpression> expressionLoader = mock(ResourceLoader.class);
        ResourceLoader<IrisBlockData> blockLoader = mock(ResourceLoader.class);
        KMap<Class<? extends IrisRegistrant>, ResourceLoader<? extends IrisRegistrant>> loaders = new KMap<>();
        loaders.put(IrisExpression.class, expressionLoader);
        when(data.getStructureLoader()).thenReturn(structureLoader);
        when(data.getJigsawPieceLoader()).thenReturn(pieceLoader);
        when(data.getBlockLoader()).thenReturn(blockLoader);
        when(data.getLoaders()).thenReturn(loaders);
        when(data.getPossibleSnippets(anyString())).thenReturn(new KList<>());
        when(structureLoader.getPossibleKeys()).thenReturn(new String[0]);
        when(pieceLoader.getPossibleKeys()).thenReturn(new String[0]);
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

    private static String definitionKey(JSONObject reference) {
        return reference.getString("$ref").substring("#/definitions/".length());
    }

    private static String flavorDefinitionKey() {
        return "enum-" + Flavor.class.getCanonicalName().replaceAll("\\Q.\\E", "-").toLowerCase();
    }

    private static List<String> enumValues(JSONObject definitions, String key) {
        JSONArray array = definitions.getJSONObject(key).getJSONArray("enum");
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
            return null;
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
}
