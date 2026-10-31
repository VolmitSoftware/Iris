package art.arcane.iris.pack.datapack.export.compile.decoration;

import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.compile.StageTestPacks;
import art.arcane.iris.pack.datapack.export.compile.noise.VanillaNoiseModel;
import art.arcane.iris.pack.datapack.export.compile.surface.MaterialProgramCompiler;
import art.arcane.iris.pack.datapack.export.compile.surface.SurfaceStage;
import art.arcane.iris.pack.datapack.export.ir.BiomeDef;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.JsonResource;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import com.google.gson.Gson;
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
import java.util.function.BiPredicate;
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
    public void aquaticDecoratorsPlaceOnlyUnderwater() throws Exception {
        Map<String, String> biomes = new LinkedHashMap<>();
        biomes.put("reef", "{\"decorators\":[" + decorator("seagrass", 0.2) + ","
                + "{\"palette\":[{\"block\":\"kelp_plant\"}],\"chance\":0.02,\"stackMin\":2,\"stackMax\":6},"
                + "{\"palette\":[{\"block\":\"kelp_plant\"}],\"chance\":0.03,\"stackMin\":2,\"stackMax\":6,\"partOf\":\"SEA_FLOOR\"}]}");
        Result result = decorate(biomes, DataVersion.V26_3, context -> {
        });

        List<JsonResource> placed = result.under("worldgen/placed_feature/decorator/");
        assertEquals(3, placed.size());
        for (JsonResource resource : placed) {
            JsonArray placement = resource.json().getAsJsonObject().getAsJsonArray("placement");
            assertEquals(resource.path(), Placements.OCEAN_FLOOR,
                    StageTestPacks.ofType(placement, "minecraft:heightmap").getFirst().get("heightmap").getAsString());
            JsonObject filter = StageTestPacks.ofType(placement, "minecraft:block_predicate_filter").getLast();
            assertFalse(resource.path(), StageTestPacks.ofType(filter, "minecraft:matching_fluids").isEmpty());
            assertTrue(resource.path(), StageTestPacks.ofType(filter, "minecraft:matching_block_tag").isEmpty());
        }
        for (JsonResource resource : result.under("worldgen/feature/decorator/")) {
            JsonObject feature = resource.json().getAsJsonObject();
            if (feature.has("allowed_placement")) {
                assertEquals(resource.path(), "minecraft:matching_fluids",
                        feature.getAsJsonObject("allowed_placement").get("type").getAsString());
            }
        }
    }

    @Test
    public void shoreDecoratorsGrowOnSolidGroundBesideWater() throws Exception {
        Map<String, String> biomes = new LinkedHashMap<>();
        biomes.put("beach", "{\"decorators\":[{\"palette\":[{\"block\":\"sugar_cane\"}],\"partOf\":\"SHORE_LINE\",\"chance\":0.5,"
                + "\"stackMin\":1,\"stackMax\":4}]}");
        Result result = decorate(biomes, DataVersion.V26_3, context -> {
        });

        JsonArray placement = result.under("worldgen/placed_feature/decorator/").getFirst().json().getAsJsonObject().getAsJsonArray("placement");
        JsonObject filter = StageTestPacks.ofType(placement, "minecraft:block_predicate_filter").getLast();
        List<String> supports = new ArrayList<>();
        for (JsonObject solid : StageTestPacks.ofType(filter, "minecraft:solid")) {
            supports.add(solid.get("offset").toString());
        }
        assertEquals(List.of("[0,-1,0]"), supports);
        assertEquals(4, StageTestPacks.ofType(filter, "minecraft:matching_fluids").size());
    }

    @Test
    public void tallPlantsPlaceBothHalves() throws Exception {
        Map<String, String> biomes = new LinkedHashMap<>();
        biomes.put("reef", "{\"decorators\":[{\"palette\":[{\"block\":\"tall_seagrass\",\"data\":{\"half\":\"lower\"}}],"
                + "\"topPalette\":[{\"block\":\"tall_seagrass\",\"data\":{\"half\":\"upper\"}}],\"partOf\":\"SEA_FLOOR\",\"chance\":0.2}]}");
        Result result = decorate(biomes, DataVersion.V26_3, context -> {
        });

        JsonObject feature = result.under("worldgen/feature/decorator/").getFirst().json().getAsJsonObject();
        assertEquals(feature.toString(), "minecraft:simple_block", feature.get("type").getAsString());
        assertEquals("{\"id\":\"minecraft:tall_seagrass\",\"properties\":{\"half\":\"lower\"}}", feature.get("to_place").toString());
        JsonArray placement = result.under("worldgen/placed_feature/decorator/").getFirst().json().getAsJsonObject().getAsJsonArray("placement");
        List<String> water = new ArrayList<>();
        for (JsonObject fluid : StageTestPacks.ofType(placement, "minecraft:matching_fluids")) {
            water.add(fluid.has("offset") ? fluid.get("offset").toString() : "[0,0,0]");
        }
        assertEquals(List.of("[0,0,0]", "[0,1,0]"), water);
    }

    @Test
    public void waterlogFollowsWhereTheDecoratorPlaces() throws Exception {
        Map<String, String> biomes = new LinkedHashMap<>();
        biomes.put("reef", "{\"decorators\":[" + decorator("sea_pickle[pickles=2,waterlogged=false]", 0.2) + ","
                + "{\"palette\":[{\"block\":\"oak_stairs[waterlogged=true]\"},{\"block\":\"short_grass\"}],\"chance\":0.1}]}");
        Result result = decorate(biomes, DataVersion.V26_3, context -> {
        });

        String pickles = result.resource("data/iris_export/worldgen/feature/decorator/sea_pickle.json").toString();
        String stairs = result.resource("data/iris_export/worldgen/feature/decorator/oak_stairs.json").toString();
        assertTrue(pickles, pickles.contains("\"waterlogged\":\"true\""));
        assertTrue(stairs, stairs.contains("\"waterlogged\":\"false\""));
    }

    @Test
    public void paletteEndsCarryHalfWeightLikeIrisRounding() throws Exception {
        Map<String, String> biomes = new LinkedHashMap<>();
        biomes.put("meadow", "{\"decorators\":[{\"palette\":[{\"block\":\"bush\"},{\"block\":\"air\",\"weight\":4}],\"chance\":0.5},"
                + "{\"palette\":[{\"block\":\"fern\",\"weight\":2},{\"block\":\"short_grass\",\"weight\":3}],\"chance\":0.2,\"partOf\":\"SHORE_LINE\"}]}");
        Result result = decorate(biomes, DataVersion.V26_3, context -> {
        });

        JsonArray placement = placement(result, "data/iris_export/worldgen/placed_feature/decorator/bush.json");
        assertEquals(0.5 / 8.0, StageTestPacks.ofType(placement, "minecraft:random_chance").getFirst().get("chance").getAsDouble(), 1.0e-6);
        JsonArray weighted = result.resource("data/iris_export/worldgen/feature/decorator/fern.json").getAsJsonObject()
                .getAsJsonObject("to_place").getAsJsonArray("entries");
        assertEquals(3, weighted.get(0).getAsJsonObject().get("weight").getAsInt());
        assertEquals(5, weighted.get(1).getAsJsonObject().get("weight").getAsInt());
    }

    @Test
    public void styledDecoratorPlacesAtStyleCoverage() throws Exception {
        String style = "{\"style\":\"SIMPLEX\",\"zoom\":0.2,\"fracture\":{\"style\":\"STATIC\",\"multiplier\":8}}";
        Map<String, String> biomes = new LinkedHashMap<>();
        biomes.put("meadow", "{\"decorators\":[{\"palette\":[{\"block\":\"poppy\"}],\"chance\":0.2,\"style\":" + style + "}]}");
        Result result = decorate(biomes, DataVersion.V26_3, context -> {
        });

        JsonArray placement = placement(result, "data/iris_export/worldgen/placed_feature/decorator/poppy.json");
        double exported = StageTestPacks.ofType(placement, "minecraft:random_chance").getFirst().get("chance").getAsDouble();
        double coverage = irisCoverage(style, 0.2);
        assertTrue("coverage " + coverage, coverage < 0.1);
        assertEquals(coverage, exported, coverage * 0.2);
        assertEquals("{\"type\":\"minecraft:simple_block\",\"to_place\":{\"id\":\"minecraft:poppy\"}}",
                result.resource("data/iris_export/worldgen/feature/decorator/poppy.json").toString());
    }

    @Test
    public void decoratorWhoseStyleNeverPassesIsLeftOut() throws Exception {
        Map<String, String> biomes = new LinkedHashMap<>();
        biomes.put("meadow", "{\"decorators\":[{\"palette\":[{\"block\":\"poppy\"}],\"chance\":0.5,\"style\":{\"style\":\"FLAT\"}},"
                + decorator("short_grass", 0.3) + "]}");
        Result result = decorate(biomes, DataVersion.V26_3, context -> {
        });

        assertEquals(List.of("iris_export:decorator/short_grass"), step(result.biomes().getFirst(), GenerationStep.VEGETAL_DECORATION));
    }

    @Test
    public void clusteredStyleFollowsFittedNoisePattern() throws Exception {
        String style = "{\"style\":\"SIMPLEX\",\"zoom\":4}";
        Map<String, String> biomes = new LinkedHashMap<>();
        biomes.put("meadow", "{\"decorators\":[{\"palette\":[{\"block\":\"poppy\"},{\"block\":\"dandelion\",\"weight\":2}],\"chance\":0.3,\"style\":"
                + style + "}]}");
        Result result = decorate(biomes, DataVersion.V26_3, context -> {
        });

        JsonObject provider = result.resource("data/iris_export/worldgen/feature/decorator/poppy.json").getAsJsonObject()
                .getAsJsonObject("to_place");
        assertEquals("minecraft:noise_threshold", provider.get("type").getAsString());
        assertEquals("minecraft:air", provider.getAsJsonObject("default_state").get("id").getAsString());
        assertEquals(0.0, provider.get("high_chance").getAsDouble(), 0.0);
        List<String> low = new ArrayList<>();
        for (JsonElement state : provider.getAsJsonArray("low_states")) {
            low.add(state.getAsJsonObject().get("id").getAsString());
        }
        assertEquals(List.of("minecraft:poppy", "minecraft:dandelion", "minecraft:dandelion", "minecraft:dandelion"), low);
        assertTrue(StageTestPacks.ofType(placement(result, "data/iris_export/worldgen/placed_feature/decorator/poppy.json"),
                "minecraft:random_chance").isEmpty());

        JsonObject noise = provider.getAsJsonObject("noise");
        List<Double> amplitudes = new ArrayList<>();
        for (JsonElement amplitude : noise.getAsJsonArray("amplitude_modifiers")) {
            amplitudes.add(amplitude.getAsDouble());
        }
        VanillaNoiseModel model = VanillaNoiseModel.create("iris_export:test", noise.get("base_octave").getAsInt(), amplitudes, 99L, DataVersion.V26_3);
        double scale = provider.get("scale").getAsDouble();
        double threshold = provider.get("threshold").getAsDouble();
        GatePattern iris = irisPattern(style, 0.3);
        GatePattern vanilla = pattern((x, z) -> model.sample(x * scale, 70.0 * scale, z * scale) < threshold);
        assertTrue("iris coverage " + iris.coverage(), iris.coverage() > 0.1 && iris.coverage() < 0.5);
        assertEquals(iris.coverage(), vanilla.coverage(), 0.03);
        assertTrue("iris patch " + iris.patch() + " vanilla " + vanilla.patch(),
                vanilla.patch() > iris.patch() * 0.7 && vanilla.patch() < iris.patch() * 1.4);
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
        JsonObject target = ore.getAsJsonArray("targets").get(1).getAsJsonObject();
        assertEquals("{\"predicate_type\":\"minecraft:tag_match\",\"tag\":\"iris_export:deposit_host\"}", target.get("target").toString());
        assertEquals("{\"id\":\"minecraft:coal_ore\"}", target.get("state").toString());
        assertNotNull(result.resource("data/iris_export/tags/block/deposit_host.json"));
    }

    @Test
    public void depositOresFollowTheirHostStone() throws Exception {
        String explicit = "{\"palette\":[{\"block\":\"deepslate_redstone_ore[lit=false]\"}],\"minHeight\":0,\"maxHeight\":40,"
                + "\"minPerChunk\":1,\"maxPerChunk\":2,\"minSize\":4,\"maxSize\":4,\"replaceableBlocks\":[\"stone\",\"deepslate\",\"tuff\"]}";
        Map<String, String> biomes = new LinkedHashMap<>();
        biomes.put("hills", "{\"deposits\":[" + COAL + "," + explicit + "]}");
        Result result = decorate(biomes, DataVersion.V26_3, context -> {
        });

        JsonArray coal = result.resource("data/iris_export/worldgen/feature/deposit/coal_ore.json").getAsJsonObject().getAsJsonArray("targets");
        assertEquals(2, coal.size());
        assertEquals("{\"predicate_type\":\"minecraft:block_match\",\"block\":\"minecraft:deepslate\"}",
                coal.get(0).getAsJsonObject().get("target").toString());
        assertEquals("{\"id\":\"minecraft:deepslate_coal_ore\"}", coal.get(0).getAsJsonObject().get("state").toString());
        assertEquals("{\"id\":\"minecraft:coal_ore\"}", coal.get(1).getAsJsonObject().get("state").toString());

        JsonArray redstone = result.resource("data/iris_export/worldgen/feature/deposit/deepslate_redstone_ore.json").getAsJsonObject()
                .getAsJsonArray("targets");
        Map<String, String> byHost = new LinkedHashMap<>();
        for (JsonElement element : redstone) {
            JsonObject target = element.getAsJsonObject();
            byHost.put(target.getAsJsonObject("target").get("block").getAsString(), target.get("state").toString());
        }
        assertEquals("{\"id\":\"minecraft:redstone_ore\",\"properties\":{\"lit\":\"false\"}}", byHost.get("minecraft:stone"));
        assertEquals("{\"id\":\"minecraft:deepslate_redstone_ore\",\"properties\":{\"lit\":\"false\"}}", byHost.get("minecraft:deepslate"));
        assertEquals("{\"id\":\"minecraft:redstone_ore\",\"properties\":{\"lit\":\"false\"}}", byHost.get("minecraft:tuff"));
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

    private static double irisCoverage(String styleJson, double chance) {
        return irisPattern(styleJson, chance).coverage();
    }

    private static GatePattern irisPattern(String styleJson, double chance) {
        IrisGeneratorStyle style = new Gson().fromJson(styleJson, IrisGeneratorStyle.class);
        CNG generator = style.create(new RNG(4242L), null);
        double zoom = style.getZoom();
        return pattern((x, z) -> generator.fitDouble(0.0, 1.0, x / zoom, z / zoom) <= chance);
    }

    private static GatePattern pattern(BiPredicate<Double, Double> gate) {
        int[] lags = {1, 2, 4, 8, 16, 32, 64, 128};
        int hits = 0;
        int total = 0;
        int[] both = new int[lags.length];
        for (int i = 0; i < 150; i++) {
            for (int j = 0; j < 150; j++) {
                double x = i * 197.0 + 3.0;
                double z = j * 173.0 - 11.0;
                total++;
                if (!gate.test(x, z)) {
                    continue;
                }
                hits++;
                for (int lag = 0; lag < lags.length; lag++) {
                    both[lag] += gate.test(x + lags[lag], z) ? 1 : 0;
                    both[lag] += gate.test(x, z + lags[lag]) ? 1 : 0;
                }
            }
        }
        double coverage = (double) hits / total;
        double previousLag = 0.0;
        double previousExcess = 1.0;
        for (int lag = 0; lag < lags.length; lag++) {
            double excess = ((double) both[lag] / (2.0 * hits) - coverage) / (1.0 - coverage);
            if (excess < 0.5) {
                double low = previousLag == 0.0 ? 0.0 : Math.log(previousLag);
                double fraction = (previousExcess - 0.5) / (previousExcess - excess);
                return new GatePattern(coverage, previousLag == 0.0 ? lags[lag] * fraction : Math.exp(low + (Math.log(lags[lag]) - low) * fraction));
            }
            previousLag = lags[lag];
            previousExcess = excess;
        }
        return new GatePattern(coverage, lags[lags.length - 1]);
    }

    private record GatePattern(double coverage, double patch) {
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
