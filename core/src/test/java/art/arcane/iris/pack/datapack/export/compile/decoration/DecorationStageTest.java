package art.arcane.iris.pack.datapack.export.compile.decoration;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.compile.StageTestPacks;
import art.arcane.iris.pack.datapack.export.compile.surface.MaterialProgramCompiler;
import art.arcane.iris.pack.datapack.export.compile.surface.SurfaceStage;
import art.arcane.iris.pack.datapack.export.ir.BiomeDef;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.JsonResource;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DecorationStageTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();
    private static final String DIMENSION = "{\"regions\":[\"region\"]}";
    private static final Field.Shared CAVE_FIELD = new Field.Shared("cave/field", new Field.Const(1.0));
    private static final String COAL = "{\"palette\":[{\"block\":\"coal_ore\"}],\"minHeight\":10,\"maxHeight\":40,\"heightDistribution\":\"UNIFORM\","
            + "\"minPerChunk\":2,\"maxPerChunk\":6,\"minSize\":4,\"maxSize\":12,\"shape\":\"VANILLA_ELLIPSOID\",\"surfaceClearance\":7}";
    private static final String IRON = "{\"palette\":[{\"block\":\"iron_ore\"}],\"minHeight\":0,\"maxHeight\":64,\"heightDistribution\":\"TRIANGLE\","
            + "\"minPerChunk\":1,\"maxPerChunk\":3,\"minSize\":6,\"maxSize\":6,\"placementScope\":\"FULL_HEIGHT\"}";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExportTestPacks.BoundPlatform platform = new ExportTestPacks.BoundPlatform();

    @Test
    public void featureOrderIsGlobalAndAcyclic() throws Exception {
        Map<String, String> biomes = new LinkedHashMap<>();
        biomes.put("first", "{\"deposits\":[" + COAL + "," + IRON + "],\"decorators\":[" + decorator("short_grass", 0.2) + "," + decorator("fern", 0.1) + "]}");
        biomes.put("second", "{\"deposits\":[" + IRON + "," + COAL + "],\"decorators\":[" + decorator("fern", 0.1) + "," + decorator("short_grass", 0.2) + "]}");
        biomes.put("third", "{\"deposits\":[" + IRON + "],\"decorators\":[" + decorator("poppy", 0.05) + "]}");
        Result result = decorate(biomes, DataVersion.V26_3, context -> {
        });

        Map<GenerationStep, List<List<String>>> perStep = new HashMap<>();
        for (BiomeDef biome : result.biomes()) {
            JsonArray steps = biome.json().getAsJsonArray("features");
            assertEquals(GenerationStep.values().length, steps.size());
            for (GenerationStep step : GenerationStep.values()) {
                List<String> features = new ArrayList<>();
                for (JsonElement feature : steps.get(step.ordinal()).getAsJsonArray()) {
                    features.add(feature.getAsString());
                }
                perStep.computeIfAbsent(step, ignored -> new ArrayList<>()).add(features);
            }
        }
        for (Map.Entry<GenerationStep, List<List<String>>> step : perStep.entrySet()) {
            Map<String, Integer> rank = new HashMap<>();
            for (List<String> features : step.getValue()) {
                for (int index = 0; index < features.size(); index++) {
                    for (int later = index + 1; later < features.size(); later++) {
                        String pair = features.get(later) + ">" + features.get(index);
                        assertFalse("cycle between " + features.get(index) + " and " + features.get(later), rank.containsKey(pair));
                        rank.put(features.get(index) + ">" + features.get(later), 1);
                    }
                }
            }
        }
        List<String> firstOres = perStep.get(GenerationStep.UNDERGROUND_ORES).get(0);
        List<String> secondOres = perStep.get(GenerationStep.UNDERGROUND_ORES).get(1);
        assertEquals(2, firstOres.size());
        assertEquals(firstOres, secondOres);
        for (List<List<String>> lists : perStep.values()) {
            for (List<String> features : lists) {
                for (String feature : features) {
                    assertTrue(feature, feature.startsWith(StageTestPacks.NAMESPACE + ":"));
                }
            }
        }
    }

    @Test
    public void duplicateDecoratorsShareOneFeature() throws Exception {
        Map<String, String> biomes = new LinkedHashMap<>();
        biomes.put("meadow", "{\"decorators\":[" + decorator("short_grass", 0.2) + "]}");
        biomes.put("field", "{\"decorators\":[" + decorator("short_grass", 0.2) + "]}");
        Result result = decorate(biomes, DataVersion.V26_3, context -> {
        });

        List<JsonResource> features = result.under("worldgen/feature/decorator/");
        List<JsonResource> placed = result.under("worldgen/placed_feature/decorator/");
        assertEquals(1, features.size());
        assertEquals(1, placed.size());
        assertEquals("data/iris_export/worldgen/feature/decorator/short_grass.json", features.getFirst().path());
        String id = "iris_export:decorator/short_grass";
        for (BiomeDef biome : result.biomes()) {
            assertEquals(List.of(id), step(biome, GenerationStep.VEGETAL_DECORATION));
        }
    }

    @Test
    public void fractionalChanceUsesRandomChance() throws Exception {
        Map<String, String> biomes = new LinkedHashMap<>();
        biomes.put("meadow", "{\"decorators\":[" + decorator("short_grass", 0.3) + "]}");
        Result modern = decorate(biomes, DataVersion.V26_3, context -> {
        });

        JsonObject placed = modern.under("worldgen/placed_feature/decorator/").getFirst().json().getAsJsonObject();
        JsonArray placement = placed.getAsJsonArray("placement");
        assertEquals("minecraft:cuboid", placement.get(0).getAsJsonObject().get("type").getAsString());
        JsonObject chance = StageTestPacks.ofType(placement, "minecraft:random_chance").getFirst();
        assertEquals(0.3f, chance.get("chance").getAsFloat(), 0.0f);
        assertEquals("{\"type\":\"minecraft:simple_block\",\"to_place\":{\"id\":\"minecraft:short_grass\"}}",
                modern.under("worldgen/feature/decorator/").getFirst().json().toString());
    }

    @Test
    public void columnWinnerSharesFollowIrisReservoirPick() {
        assertArrayEquals(new double[]{0.5, 0.5}, DecoratorCompiler.columnShares(new double[]{1.0, 1.0}), 1.0e-12);
        double[] shares = DecoratorCompiler.columnShares(new double[]{0.5, 0.2});
        assertEquals(0.5 * (0.8 + 0.2 / 2.0), shares[0], 1.0e-12);
        assertEquals(0.2 * (0.5 + 0.5 / 2.0), shares[1], 1.0e-12);
        assertArrayEquals(new double[]{0.5, 1.0}, DecoratorCompiler.sequentialChances(new double[]{0.5, 0.5}), 1.0e-12);
    }

    @Test
    public void depositHeightRangeExact() throws Exception {
        Map<String, String> biomes = new LinkedHashMap<>();
        biomes.put("hills", "{\"deposits\":[" + COAL + "," + IRON + "]}");
        Result result = decorate(biomes, DataVersion.V26_3, context -> {
        });

        JsonArray coal = placement(result, "data/iris_export/worldgen/placed_feature/deposit/coal_ore.json");
        JsonObject coalHeight = StageTestPacks.ofType(coal, "minecraft:height_range").getFirst().getAsJsonObject("height");
        assertEquals("{\"type\":\"minecraft:uniform\",\"min_inclusive\":{\"above_bottom\":10},\"max_inclusive\":{\"above_bottom\":40}}", coalHeight.toString());
        assertEquals(-8, StageTestPacks.ofType(coal, "minecraft:surface_relative_threshold_filter").getFirst().get("max_inclusive").getAsInt());
        assertEquals("{\"type\":\"minecraft:uniform\",\"min_inclusive\":2,\"max_inclusive\":6}",
                StageTestPacks.ofType(coal, "minecraft:count").getFirst().get("count").toString());
        JsonArray iron = placement(result, "data/iris_export/worldgen/placed_feature/deposit/iron_ore.json");
        JsonObject ironHeight = StageTestPacks.ofType(iron, "minecraft:height_range").getFirst().getAsJsonObject("height");
        assertEquals("{\"type\":\"minecraft:trapezoid\",\"min_inclusive\":{\"above_bottom\":0},\"max_inclusive\":{\"above_bottom\":64},\"plateau\":0}",
                ironHeight.toString());
        assertTrue(StageTestPacks.ofType(iron, "minecraft:surface_relative_threshold_filter").isEmpty());

        JsonObject ore = result.resource("data/iris_export/worldgen/feature/deposit/coal_ore.json").getAsJsonObject();
        assertEquals("minecraft:ore", ore.get("type").getAsString());
        assertEquals(8, ore.get("size").getAsInt());
        JsonObject target = ore.getAsJsonArray("targets").get(0).getAsJsonObject();
        assertEquals("{\"predicate_type\":\"minecraft:tag_match\",\"tag\":\"iris_export:deposit_host\"}", target.get("target").toString());
        assertEquals("{\"id\":\"minecraft:coal_ore\"}", target.get("state").toString());
        assertNotNull(result.resource("data/iris_export/tags/block/deposit_host.json"));
    }

    @Test
    public void vanillaOreVeinsDisabled() throws Exception {
        Map<String, String> biomes = new LinkedHashMap<>();
        biomes.put("caves", "{\"deposits\":[" + COAL + "],\"layers\":[{\"palette\":[{\"block\":\"stone\"}]}],\"wall\":{\"palette\":[{\"block\":\"tuff\"}]}}");
        DataVersion target = DataVersion.V26_3;
        Result result = decorate(biomes, target, context -> {
            context.setCaveField(CAVE_FIELD, 0.04);
            new SurfaceStage().apply(context);
        });

        for (BiomeDef biome : result.biomes()) {
            for (GenerationStep step : GenerationStep.values()) {
                for (String feature : step(biome, step)) {
                    assertTrue(feature, feature.startsWith(StageTestPacks.NAMESPACE + ":"));
                }
            }
        }
        JsonElement rule = result.context().material().rule();
        for (JsonObject vein : StageTestPacks.ofType(rule, "minecraft:ore_vein")) {
            assertEquals("iris_export:" + MaterialProgramCompiler.WALL_SHELL, vein.get("density").getAsString());
        }
        assertFalse(rule.toString().contains("ore_vein/"));
        assertFalse(rule.toString().contains("minecraft:overworld"));
        assertEquals(1, StageTestPacks.ofType(result.context().material().rule(), "minecraft:ore_vein").size());
    }

    @Test
    public void repaintUsesCuboid() throws Exception {
        Map<String, String> biomes = new LinkedHashMap<>();
        biomes.put("cliffs", "{\"layers\":[{\"palette\":[{\"block\":\"grass_block\"}],\"slopeCondition\":{\"maximumSlope\":3}},"
                + "{\"palette\":[{\"block\":\"stone\"}],\"minHeight\":2,\"maxHeight\":3,\"slopeCondition\":{\"minimumSlope\":3}},"
                + "{\"palette\":[{\"block\":\"dirt\"}],\"minHeight\":2,\"maxHeight\":2}],\"wall\":{\"palette\":[{\"block\":\"andesite\"}]}}");
        PackCompileContext[] holder = new PackCompileContext[1];
        Result modern = decorate(biomes, DataVersion.V26_3, context -> holder[0] = context);

        List<String> repaint = step(modern.biomes().getFirst(), GenerationStep.LOCAL_MODIFICATIONS);
        assertEquals(List.of("iris_export:repaint/slope_stone", "iris_export:repaint/wall_andesite"), repaint);
        JsonArray slope = placement(modern, "data/iris_export/worldgen/placed_feature/repaint/slope_stone.json");
        assertEquals("minecraft:cuboid", slope.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("{\"type\":\"minecraft:inside_world_bounds\",\"offset\":[0,-1,0]}",
                slope.get(1).getAsJsonObject().getAsJsonObject("predicate").toString());
        assertFalse(StageTestPacks.find(slope, object -> object.has("offset") && object.get("offset").toString().equals("[3,3,0]")).isEmpty());
        JsonObject column = modern.resource("data/iris_export/worldgen/feature/repaint/slope_stone.json").getAsJsonObject();
        assertEquals("minecraft:block_column", column.get("type").getAsString());
        assertEquals("down", column.get("direction").getAsString());
        assertEquals("{\"type\":\"minecraft:uniform\",\"min_inclusive\":2,\"max_inclusive\":3}",
                column.getAsJsonArray("layers").get(0).getAsJsonObject().get("height").toString());
        assertTrue(hasDiagnostic(holder[0], ExportLimit.SLOPE));
    }

    @Test
    public void decorationIsDeterministic() throws Exception {
        Map<String, String> biomes = new LinkedHashMap<>();
        biomes.put("forest", "{\"deposits\":[" + COAL + "],\"decorators\":[" + decorator("short_grass", 0.4) + "," + decorator("poppy", 0.02)
                + ",{\"palette\":[{\"block\":\"sugar_cane\"}],\"partOf\":\"SHORE_LINE\",\"chance\":0.5,\"stackMin\":2,\"stackMax\":4}],"
                + "\"wall\":{\"palette\":[{\"block\":\"tuff\"}]},\"slab\":{\"palette\":[{\"block\":\"stone_slab\"}]}}");
        Result first = decorate(biomes, DataVersion.V26_3, context -> {
        });
        Result second = decorate(biomes, DataVersion.V26_3, context -> {
        });

        assertEquals(first.resources().size(), second.resources().size());
        for (int index = 0; index < first.resources().size(); index++) {
            assertEquals(first.resources().get(index).path(), second.resources().get(index).path());
            assertEquals(first.resources().get(index).json().toString(), second.resources().get(index).json().toString());
        }
        assertEquals(first.biomes().toString(), second.biomes().toString());
    }

    private Result decorate(Map<String, String> biomes, DataVersion target, Consumer<PackCompileContext> before) throws Exception {
        File pack = StageTestPacks.pack(temporaryFolder.newFolder(), DIMENSION, biomes);
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = StageTestPacks.context(data, target, new ArrayList<>(biomes.keySet()));
            before.accept(context);
            List<BiomeDef> definitions = new ArrayList<>();
            for (String key : biomes.keySet()) {
                definitions.add(new BiomeDef(StageTestPacks.biomeId(key).substring(StageTestPacks.NAMESPACE.length() + 1), new JsonObject()));
            }
            context.setBiomes(definitions);
            new DecorationStage().apply(context);
            return new Result(context, context.features().apply(context.biomes(), context.namespace()), List.copyOf(context.jsonResources()));
        } finally {
            data.close();
        }
    }

    private static String decorator(String block, double chance) {
        return "{\"palette\":[{\"block\":\"" + block + "\"}],\"chance\":" + chance + "}";
    }

    private static List<String> step(BiomeDef biome, GenerationStep step) {
        List<String> features = new ArrayList<>();
        for (JsonElement feature : biome.json().getAsJsonArray("features").get(step.ordinal()).getAsJsonArray()) {
            features.add(feature.getAsString());
        }
        return features;
    }

    private static JsonArray placement(Result result, String path) {
        return result.resource(path).getAsJsonObject().getAsJsonArray("placement");
    }

    private static boolean hasDiagnostic(PackCompileContext context, ExportLimit limit) {
        for (ExportDiagnostic diagnostic : context.diagnostics()) {
            if (diagnostic.limit() == limit) {
                return true;
            }
        }
        return false;
    }

    private record Result(PackCompileContext context, List<BiomeDef> biomes, List<JsonResource> resources) {
        List<JsonResource> under(String folder) {
            List<JsonResource> found = new ArrayList<>();
            for (JsonResource resource : resources) {
                if (resource.path().startsWith("data/iris_export/" + folder)) {
                    found.add(resource);
                }
            }
            return found;
        }

        JsonElement resource(String path) {
            for (JsonResource resource : resources) {
                if (resource.path().equals(path)) {
                    return resource.json();
                }
            }
            return null;
        }
    }
}
