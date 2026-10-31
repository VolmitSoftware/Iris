package art.arcane.iris.pack.datapack.export.compile.biome;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.ExportTestStyles;
import art.arcane.iris.pack.datapack.export.compile.DimensionStage;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.ir.BiomeDef;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.JsonResource;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.testsupport.CompatTestPlatform;
import art.arcane.iris.testsupport.FakeVanillaDataSource;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;

public class BiomeDefinitionCompilerTest {
    private static final List<String> LEGACY_KEYS = List.of("surface_builder", "depth", "scale", "precipitation", "category", "starts");
    private static final String MEADOW = "iris_export:meadow";
    private static final String PLAINS_JSON = """
            {"has_precipitation": true, "temperature": 0.8, "downfall": 0.4,
             "effects": {"water_color": "#3f76e4", "grass_color": "#91bd59"},
             "attributes": {
               "minecraft:audio/background_music": {"default": {"sound": "minecraft:music.overworld.plains"}},
               "minecraft:gameplay/natural_mob_spawns": {"argument": {"spawn_costs": {},
                 "spawns_by_category": {"creature": [{"type": "minecraft:sheep", "count": 4, "weight": 12}]}}, "modifier": "overlay"}},
             "carvers": ["minecraft:cave"],
             "features": [["minecraft:lake_lava_underground"]]}
            """;

    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExportTestPacks.BoundPlatform platform = new ExportTestPacks.BoundPlatform();

    @Test
    public void derivativeSpawnsBakedIntoBiome() throws Exception {
        IrisData data = IrisData.openRuntime(pack());
        try {
            PackCompileContext context = context(data, DataVersion.V26_3);
            FakeVanillaDataSource vanilla = FakeVanillaDataSource.of(Map.of(
                    "data/minecraft/worldgen/biome/plains.json", PLAINS_JSON,
                    "data/minecraft/tags/worldgen/biome/has_structure/village_plains.json", "{\"values\": [\"minecraft:plains\", \"minecraft:meadow\"]}",
                    "data/minecraft/tags/worldgen/biome/is_overworld.json", "{\"values\": [{\"id\": \"minecraft:plains\", \"required\": false}, \"#minecraft:is_forest\"]}"));

            BiomeDefinitionCompiler.Definitions definitions = new BiomeDefinitionCompiler(Optional.of(vanilla))
                    .compile(context, Map.of(MEADOW, "meadow"));

            JsonObject biome = only(definitions.biomes()).json();
            JsonObject attributes = biome.getAsJsonObject("attributes");
            JsonArray creatures = attributes.getAsJsonObject("minecraft:gameplay/natural_mob_spawns")
                    .getAsJsonObject("argument").getAsJsonObject("spawns_by_category").getAsJsonArray("creature");
            assertEquals(List.of("minecraft:sheep", "minecraft:fox"), types(creatures));
            assertEquals(JsonParser.parseString("{\"type\": \"minecraft:uniform\", \"min_inclusive\": 1, \"max_inclusive\": 2}"), creatures.get(1).getAsJsonObject().get("count"));
            assertTrue(attributes.has("minecraft:audio/background_music"));
            assertFalse(attributes.has("minecraft:gameplay/creature_world_gen_spawn_probability"));
            assertEquals("#112233", attributes.get("minecraft:visual/sky_color").getAsString());
            assertEquals("#445566", biome.getAsJsonObject("effects").get("grass_color").getAsString());
            assertEquals("#3f76e4", biome.getAsJsonObject("effects").get("water_color").getAsString());
            assertEquals(0.3, biome.get("temperature").getAsDouble(), 1.0e-9);
            assertEquals(new JsonArray(), biome.get("carvers"));
            assertEquals(new JsonArray(), biome.get("features"));
            assertNoLegacyKeys(biome);
            Map<String, JsonElement> tags = tags(definitions);
            assertEquals(List.of("data/minecraft/tags/worldgen/biome/has_structure/village_plains.json",
                    "data/minecraft/tags/worldgen/biome/is_forest.json",
                    "data/minecraft/tags/worldgen/biome/is_overworld.json"), List.copyOf(tags.keySet()));
            assertEquals(JsonParser.parseString("{\"replace\": false, \"values\": [\"" + MEADOW + "\"]}"),
                    tags.get("data/minecraft/tags/worldgen/biome/has_structure/village_plains.json"));
            assertTrue(context.diagnostics().isEmpty());
        } finally {
            data.close();
        }
    }

    @Test
    public void withoutVanillaDataBiomesComeFromIrisFields() throws Exception {
        IrisData data = IrisData.openRuntime(pack());
        try {
            DataVersion target = DataVersion.V26_3;
            PackCompileContext context = context(data, target);

            BiomeDefinitionCompiler.Definitions definitions = BiomeDefinitionCompiler.forTarget(target)
                    .compile(context, Map.of(MEADOW, "meadow", "iris_export:deep", "deep"));

            for (BiomeDef biome : definitions.biomes()) {
                assertNoLegacyKeys(biome.json());
                assertTrue(biome.json().toString(), biome.json().has("effects"));
                assertTrue(biome.json().toString(), biome.json().has("temperature"));
                assertEquals(new JsonArray(), biome.json().get("carvers"));
            }
            assertFalse(definitions.biomes().getLast().json().has("spawners"));
            assertTrue(tags(definitions).containsKey("data/minecraft/tags/worldgen/biome/is_overworld.json"));
            assertTrue(tags(definitions).containsKey("data/minecraft/tags/worldgen/biome/is_forest.json"));
            assertEquals(List.of(ExportLimit.VANILLA_BIOME_DATA, ExportLimit.BIOME_VARIANT), limits(context.diagnostics()));
        } finally {
            data.close();
        }
    }

    @Test
    public void runningVersionSuppliesVanillaData() throws Exception {
        IrisData data = IrisData.openRuntime(pack());
        try {
            doReturn("26.3").when(IrisPlatforms.get()).minecraftVersion();
            doReturn(FakeVanillaDataSource.of(Map.of("data/minecraft/worldgen/biome/plains.json", PLAINS_JSON))).when(IrisPlatforms.get()).vanillaData();
            PackCompileContext current = context(data, DataVersion.V26_3);
            PackCompileContext older = context(data, DataVersion.V26_3);

            JsonObject biome = only(BiomeDefinitionCompiler.forTarget(DataVersion.V26_3).compile(current, Map.of(MEADOW, "meadow")).biomes()).json();
            doReturn("26.2").when(IrisPlatforms.get()).minecraftVersion();
            BiomeDefinitionCompiler.forTarget(DataVersion.V26_3).compile(older, Map.of(MEADOW, "meadow"));

            assertTrue(biome.getAsJsonObject("attributes").has("minecraft:audio/background_music"));
            assertTrue(current.diagnostics().isEmpty());
            assertEquals(List.of(ExportLimit.VANILLA_BIOME_DATA), limits(older.diagnostics()));
        } finally {
            data.close();
        }
    }

    private File pack() throws Exception {
        File pack = temporaryFolder.newFolder("pack");
        CompatTestPlatform.write(pack, "dimensions/" + ExportTestPacks.DIMENSION + ".json", "{\"regions\": [\"region\"]}");
        CompatTestPlatform.write(pack, "regions/region.json", "{\"landBiomes\": [\"meadow\"], \"seaBiomes\": [\"deep\"]}");
        CompatTestPlatform.write(pack, "biomes/meadow.json", """
                {"name": "Meadow", "derivative": "minecraft:plains",
                 "customDerivitives": [{"id": "meadow", "temperature": 0.3, "skyColor": "#112233", "grassColor": "#445566",
                   "tags": ["#minecraft:is_forest"],
                   "spawns": [{"type": "minecraft:fox", "weight": 3, "minCount": 1, "maxCount": 2, "group": "CREATURE"}]}]}
                """);
        CompatTestPlatform.write(pack, "biomes/deep.json", """
                {"name": "Deep", "derivative": "minecraft:deep_ocean", "biomeScatter": ["minecraft:deep_ocean", "minecraft:ocean"]}
                """);
        return pack;
    }

    private static PackCompileContext context(IrisData data, DataVersion target) {
        PackCompileContext context = ExportTestPacks.contextBuilder(data, target).styles(new ExportTestStyles()).build();
        new DimensionStage().apply(context);
        return context;
    }

    private static BiomeDef only(List<BiomeDef> biomes) {
        assertEquals(1, biomes.size());
        return biomes.getFirst();
    }

    private static List<String> types(JsonArray spawns) {
        List<String> types = new ArrayList<>();
        for (JsonElement spawn : spawns) {
            types.add(spawn.getAsJsonObject().get("type").getAsString());
        }
        return types;
    }

    private static Map<String, JsonElement> tags(BiomeDefinitionCompiler.Definitions definitions) {
        Map<String, JsonElement> tags = new TreeMap<>();
        for (JsonResource resource : definitions.tags()) {
            tags.put(resource.path(), resource.json());
        }
        return tags;
    }

    private static List<ExportLimit> limits(List<ExportDiagnostic> diagnostics) {
        List<ExportLimit> limits = new ArrayList<>();
        for (ExportDiagnostic diagnostic : diagnostics) {
            limits.add(diagnostic.limit());
        }
        return limits;
    }

    private static void assertNoLegacyKeys(JsonObject biome) {
        for (String key : LEGACY_KEYS) {
            assertFalse(key + " in " + biome, biome.has(key));
        }
    }
}
