package art.arcane.iris.pack.datapack.export.compile.surface;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.compile.BlockStateResolver;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.compile.StageTestPacks;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.JsonResource;
import art.arcane.iris.pack.datapack.export.ir.MaterialProgram;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MaterialProgramCompilerTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();
    private static final String DIMENSION = "{\"regions\":[\"region\"]}";
    private static final String NO_BEDROCK = "{\"regions\":[\"region\"],\"bedrock\":false}";
    private static final Field.Shared DEEP_OPENING = new Field.Shared("cave/deep_opening", new Field.Const(1.0));
    private static final Field.Shared CAVE_FIELD = new Field.Shared("cave/field", new Field.Const(1.0));
    private static final double WALL_BAND = 0.04;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExportTestPacks.BoundPlatform platform = new ExportTestPacks.BoundPlatform();

    @Test
    public void depthGuardPrecedesBiomeDispatch() throws Exception {
        Map<String, String> biomes = new LinkedHashMap<>();
        biomes.put("plains", layers("{\"palette\":[{\"block\":\"grass_block\"}],\"minHeight\":1,\"maxHeight\":1}",
                "{\"palette\":[{\"block\":\"dirt\"}],\"minHeight\":3,\"maxHeight\":3}"));
        biomes.put("desert", layers("{\"palette\":[{\"block\":\"sand\"}],\"minHeight\":4,\"maxHeight\":4}"));
        MaterialProgram program = compile(DIMENSION, biomes, DataVersion.V26_3);

        JsonObject rule = program.rule().getAsJsonObject();
        List<JsonObject> guards = new ArrayList<>();
        for (JsonElement entry : rule.getAsJsonArray("sequence")) {
            JsonObject top = entry.getAsJsonObject();
            assertFalse("a biome dispatch sits outside a depth guard", isBiomeCondition(top));
            if (isFloorGuard(top)) {
                guards.add(top);
            }
        }
        assertEquals(1, guards.size());
        JsonObject guard = guards.getFirst();
        assertEquals(3, guard.getAsJsonObject("if_true").get("offset").getAsInt());
        assertEquals(0, guard.getAsJsonObject("if_true").get("secondary_depth_range").getAsInt());
        List<JsonObject> dispatch = StageTestPacks.ofType(guard.get("then_run"), "minecraft:biome");
        assertEquals(2, dispatch.size());
        assertEquals(StageTestPacks.ofType(rule, "minecraft:biome").size(), dispatch.size());
    }

    @Test
    public void layerDepthBandsExact() throws Exception {
        Map<String, String> biomes = new LinkedHashMap<>();
        biomes.put("fixed", layers("{\"palette\":[{\"block\":\"grass_block\"}],\"minHeight\":1,\"maxHeight\":1}",
                "{\"palette\":[{\"block\":\"dirt\"}],\"minHeight\":3,\"maxHeight\":3}",
                "{\"palette\":[{\"block\":\"gravel\"}],\"minHeight\":2,\"maxHeight\":2}"));
        biomes.put("varied", layers("{\"palette\":[{\"block\":\"sand\"}],\"minHeight\":1,\"maxHeight\":1}",
                "{\"palette\":[{\"block\":\"sandstone\"}],\"minHeight\":1,\"maxHeight\":4}",
                "{\"palette\":[{\"block\":\"clay\"}],\"minHeight\":0,\"maxHeight\":2}"));
        PackCompileContext[] holder = new PackCompileContext[1];
        MaterialProgram program = compile(DIMENSION, biomes, DataVersion.V26_3, holder);

        JsonObject rule = program.rule().getAsJsonObject();
        assertEquals(List.of(List.of(0, 0), List.of(3, 0), List.of(5, 0)), bands(rule, StageTestPacks.biomeId("fixed")));
        assertEquals(List.of(List.of(0, 0), List.of(1, 3), List.of(1, 5)), bands(rule, StageTestPacks.biomeId("varied")));
        assertTrue(hasDiagnostic(holder[0], ExportLimit.LAYER_THICKNESS, "biomes/varied"));
        assertFalse(hasDiagnostic(holder[0], ExportLimit.LAYER_THICKNESS, "biomes/fixed"));
    }

    @Test
    public void oreVeinRulesCappedAtEight() throws Exception {
        List<String> walls = List.of("stone_bricks", "mossy_cobblestone", "tuff", "calcite", "deepslate", "andesite", "diorite",
                "granite", "basalt", "blackstone");
        Map<String, String> biomes = new LinkedHashMap<>();
        for (int index = 0; index < walls.size(); index++) {
            biomes.put("cave_" + index, "{\"layers\":[{\"palette\":[{\"block\":\"stone\"}]}],\"wall\":{\"palette\":[{\"block\":\""
                    + walls.get(index) + "\"}]}}");
        }
        biomes.put("shared", "{\"layers\":[{\"palette\":[{\"block\":\"stone\"}]}],\"wall\":{\"palette\":[{\"block\":\"stone_bricks\"}]}}");
        PackCompileContext[] holder = new PackCompileContext[1];
        MaterialProgram modern = compile(NO_BEDROCK, biomes, DataVersion.V26_3, holder, context -> context.setCaveField(CAVE_FIELD, WALL_BAND));

        List<JsonObject> veins = StageTestPacks.ofType(modern.rule(), "minecraft:ore_vein");
        assertEquals(MaterialProgramCompiler.ORE_VEIN_BUDGET, veins.size());
        for (JsonObject vein : veins) {
            assertEquals("iris_export:" + MaterialProgramCompiler.WALL_SHELL, vein.get("density").getAsString());
        }
        List<JsonResource> shells = wallShells(holder[0]);
        assertEquals(1, shells.size());
        JsonObject shell = shells.getFirst().json().getAsJsonObject();
        assertEquals("minecraft:range_choice", shell.get("type").getAsString());
        assertEquals(1.0, shell.get("when_in_range").getAsDouble(), 0.0);
        assertEquals(0.0, shell.get("when_out_of_range").getAsDouble(), 0.0);
        assertEquals(0.0, shell.get("min_inclusive").getAsDouble(), 0.0);
        assertEquals(WALL_BAND, shell.get("max_exclusive").getAsDouble(), 0.0);
        assertEquals("iris_export:" + CAVE_FIELD.name(), shell.get("input").getAsString());
        assertEquals(walls.size() - MaterialProgramCompiler.ORE_VEIN_BUDGET, count(holder[0], ExportLimit.WALL_PALETTE));
    }

    @Test
    public void overLimitWallPalettesUseTheNearestKeptPalette() throws Exception {
        Map<String, String> biomes = new LinkedHashMap<>();
        biomes.put("quarry", wall("{\"block\":\"stone\"}"));
        biomes.put("cliff", wall("{\"block\":\"stone\"}"));
        for (String kept : List.of("gray_terracotta", "packed_ice", "deepslate", "tuff", "calcite", "andesite", "diorite")) {
            biomes.put("kept_" + kept, wall("{\"block\":\"" + kept + "\"}"));
        }
        biomes.put("mesa", wall("{\"block\":\"orange_terracotta\"}"));
        biomes.put("frozen", wall("{\"block\":\"blue_ice\"}"));
        biomes.put("dark", wall("{\"block\":\"granite\",\"weight\":1},{\"block\":\"deepslate\",\"weight\":3}"));
        biomes.put("glassy", wall("{\"block\":\"glass\"}"));
        PackCompileContext[] holder = new PackCompileContext[1];
        MaterialProgram program = compile(NO_BEDROCK, biomes, DataVersion.V26_3, holder, context -> context.setCaveField(CAVE_FIELD, WALL_BAND));

        assertEquals(MaterialProgramCompiler.ORE_VEIN_BUDGET, StageTestPacks.ofType(program.rule(), "minecraft:ore_vein").size());
        assertEquals("minecraft:gray_terracotta", wallFiller(program, "mesa"));
        assertEquals("minecraft:packed_ice", wallFiller(program, "frozen"));
        assertEquals("minecraft:deepslate", wallFiller(program, "dark"));
        assertEquals("minecraft:stone", wallFiller(program, "glassy"));
        assertEquals(4, count(holder[0], ExportLimit.WALL_PALETTE));
        for (ExportDiagnostic diagnostic : holder[0].diagnostics()) {
            if (diagnostic.limit() == ExportLimit.WALL_PALETTE) {
                String fallback = wallFiller(program, diagnostic.resource().substring("biomes/".length()));
                assertTrue(diagnostic.message(), diagnostic.message().contains(fallback));
            }
        }
    }

    @Test
    public void wallPalettesNeedCaves() throws Exception {
        Map<String, String> biomes = new LinkedHashMap<>();
        biomes.put("cave", "{\"layers\":[{\"palette\":[{\"block\":\"stone\"}]}],\"wall\":{\"palette\":[{\"block\":\"tuff\"}]}}");
        PackCompileContext[] holder = new PackCompileContext[1];
        MaterialProgram program = compile(NO_BEDROCK, biomes, DataVersion.V26_3, holder);

        assertTrue(StageTestPacks.ofType(program.rule(), "minecraft:ore_vein").isEmpty());
        assertTrue(wallShells(holder[0]).isEmpty());
        assertEquals(0, count(holder[0], ExportLimit.WALL_PALETTE));
    }

    @Test
    public void blockRuleUsesIdAndProperties() throws Exception {
        Map<String, String> biomes = new LinkedHashMap<>();
        biomes.put("logs", layers("{\"palette\":[{\"block\":\"oak_log\",\"data\":{\"axis\":\"x\"}}],\"minHeight\":1,\"maxHeight\":1}"));
        MaterialProgram program = compile(NO_BEDROCK, biomes, DataVersion.V26_3);

        JsonObject modern = StageTestPacks.ofType(program.rule(), "minecraft:block").getFirst();
        assertEquals("{\"type\":\"minecraft:block\",\"result_state\":{\"id\":\"minecraft:oak_log\",\"properties\":{\"axis\":\"x\"}}}", modern.toString());
    }

    @Test
    public void staticPaletteUsesGradientPairsAndSmoothPaletteUsesRegisteredNoise() throws Exception {
        Map<String, String> biomes = new LinkedHashMap<>();
        biomes.put("speckled", layers("{\"palette\":[{\"block\":\"stone\",\"weight\":3},{\"block\":\"andesite\"}],\"minHeight\":1,\"maxHeight\":1}",
                "{\"palette\":[{\"block\":\"dirt\"},{\"block\":\"coarse_dirt\"}],\"style\":{\"style\":\"SIMPLEX\"},\"zoom\":8,\"minHeight\":1,\"maxHeight\":1}"));
        PackCompileContext[] holder = new PackCompileContext[1];
        MaterialProgram program = compile(NO_BEDROCK, biomes, DataVersion.V26_3, holder);

        List<JsonObject> gradients = StageTestPacks.ofType(program.rule(), "minecraft:vertical_gradient");
        assertEquals(2, gradients.size());
        int seaLevel = holder[0].dimensionDef().fluidHeight();
        assertEquals(0.25, ramp(gradients.get(0), seaLevel) * (1.0 - ramp(gradients.get(1), seaLevel)), 1.0e-3);
        List<JsonObject> thresholds = StageTestPacks.ofType(program.rule(), "minecraft:noise_threshold");
        assertEquals(1, thresholds.size());
        String noise = thresholds.getFirst().get("noise").getAsString();
        assertEquals("iris_export:surface/palette_3_1", noise);
        NoiseDef registered = holder[0].noises().definitions().getFirst();
        assertEquals("surface/palette_3_1", registered.id());
        assertEquals(-3, registered.firstOctave());
        assertTrue(thresholds.getFirst().get("is_3d").getAsBoolean());
    }

    @Test
    public void gradientChanceHoldsProbabilityAcrossSurfaceBand() {
        for (double probability : List.of(0.5, 0.3, 0.1, 0.01, 0.001)) {
            List<JsonObject> conditions = SurfacePalettes.gradientChance(probability, "iris_export:test/", 63);
            for (int y = 63 - 192; y <= 63 + 192; y += 32) {
                double product = 1.0;
                for (int index = 0; index < conditions.size(); index += 2) {
                    product *= ramp(conditions.get(index), y) * (1.0 - ramp(conditions.get(index + 1).getAsJsonObject("invert"), y));
                }
                assertEquals("p=" + probability + " y=" + y, probability, product, probability * 0.06);
            }
        }
    }

    @Test
    public void bedrockAndOreBandsUseExactAnchors() throws Exception {
        String dimension = "{\"regions\":[\"region\"],\"ores\":[{\"palette\":{\"palette\":[{\"block\":\"deepslate\"}]},\"threshold\":1,\"range\":{\"min\":1,\"max\":57}}]}";
        Map<String, String> biomes = new LinkedHashMap<>();
        biomes.put("plains", layers("{\"palette\":[{\"block\":\"grass_block\"}],\"minHeight\":1,\"maxHeight\":1}"));
        PackCompileContext[] holder = new PackCompileContext[1];
        MaterialProgram program = compile(dimension, biomes, DataVersion.V26_3, holder);

        int minY = holder[0].dimensionDef().minY();
        JsonObject sequence = program.rule().getAsJsonObject();
        JsonObject bedrock = sequence.getAsJsonArray("sequence").get(0).getAsJsonObject();
        JsonObject floor = bedrock.getAsJsonObject("if_true");
        assertEquals(minY, floor.getAsJsonObject("true_at_and_below").get("absolute").getAsInt());
        assertEquals(minY + 1, floor.getAsJsonObject("false_at_and_above").get("absolute").getAsInt());
        List<JsonObject> anchors = StageTestPacks.ofType(program.rule(), "minecraft:y_above");
        assertEquals(List.of(minY + 1, minY + 58), List.of(anchors.get(0).getAsJsonObject("anchor").get("absolute").getAsInt(),
                anchors.get(1).getAsJsonObject("anchor").get("absolute").getAsInt()));
        JsonElement last = sequence.getAsJsonArray("sequence").get(sequence.getAsJsonArray("sequence").size() - 1);
        assertFalse(StageTestPacks.ofType(last, "minecraft:y_above").isEmpty());
    }

    @Test
    public void compilationIsDeterministic() throws Exception {
        Map<String, String> biomes = new LinkedHashMap<>();
        biomes.put("plains", layers("{\"palette\":[{\"block\":\"grass_block\"},{\"block\":\"moss_block\"}],\"minHeight\":1,\"maxHeight\":2}",
                "{\"palette\":[{\"block\":\"dirt\"},{\"block\":\"gravel\"}],\"style\":{\"style\":\"PERLIN\"},\"minHeight\":2,\"maxHeight\":3}"));
        biomes.put("cave", "{\"layers\":[{\"palette\":[{\"block\":\"stone\"}]}],\"caveCeilingLayers\":[{\"palette\":[{\"block\":\"tuff\"}]}],"
                + "\"wall\":{\"palette\":[{\"block\":\"tuff\",\"weight\":2},{\"block\":\"calcite\"},{\"block\":\"diorite\"},{\"block\":\"granite\"}]}}");
        MaterialProgram first = compile(DIMENSION, biomes, DataVersion.V26_3, new PackCompileContext[1], context -> context.setCaveField(CAVE_FIELD, WALL_BAND));
        MaterialProgram second = compile(DIMENSION, biomes, DataVersion.V26_3, new PackCompileContext[1], context -> context.setCaveField(CAVE_FIELD, WALL_BAND));

        assertEquals(first.rule().toString(), second.rule().toString());
        assertEquals(1, StageTestPacks.ofType(first.rule(), "minecraft:ore_vein").size());
        JsonObject vein = StageTestPacks.ofType(first.rule(), "minecraft:ore_vein").getFirst();
        assertEquals("minecraft:tuff", vein.getAsJsonObject("filler_block").get("id").getAsString());
        assertEquals(0.5f, vein.get("richness").getAsFloat(), 1.0e-6f);
        assertEquals(0.5f, vein.get("raw_ore_chance").getAsFloat(), 1.0e-6f);
    }

    @Test
    public void deepCaveOpeningLeadsTheRule() throws Exception {
        Map<String, String> biomes = new LinkedHashMap<>();
        biomes.put("plains", layers("{\"palette\":[{\"block\":\"grass_block\"}],\"minHeight\":1,\"maxHeight\":1}"));
        MaterialProgram program = compile(DIMENSION, biomes, DataVersion.V26_3, new PackCompileContext[1], context -> context.setDeepCaves(DEEP_OPENING));

        JsonObject head = program.rule().getAsJsonObject().getAsJsonArray("sequence").get(0).getAsJsonObject();
        assertEquals("minecraft:ore_vein", head.get("type").getAsString());
        assertEquals("iris_export:" + DEEP_OPENING.name(), head.get("density").getAsString());
        for (String slot : List.of("ore_block", "raw_ore_block", "filler_block")) {
            assertEquals("minecraft:air", head.getAsJsonObject(slot).get("id").getAsString());
        }
        assertEquals(0.0, head.get("richness").getAsDouble(), 0.0);
        assertEquals(1, StageTestPacks.ofType(program.rule(), "minecraft:ore_vein").size());
    }

    @Test
    public void deepCaveRuleCountsTowardTheOreVeinBudget() throws Exception {
        Map<String, String> biomes = new LinkedHashMap<>();
        for (String wall : List.of("stone_bricks", "mossy_cobblestone", "tuff", "calcite", "deepslate", "andesite", "diorite", "granite", "basalt")) {
            biomes.put("cave_" + wall, "{\"layers\":[{\"palette\":[{\"block\":\"stone\"}]}],\"wall\":{\"palette\":[{\"block\":\"" + wall + "\"}]}}");
        }
        PackCompileContext[] holder = new PackCompileContext[1];
        MaterialProgram program = compile(NO_BEDROCK, biomes, DataVersion.V26_3, holder, context -> {
            context.setCaveField(CAVE_FIELD, WALL_BAND);
            context.setDeepCaves(DEEP_OPENING);
        });

        assertEquals(MaterialProgramCompiler.ORE_VEIN_BUDGET, StageTestPacks.ofType(program.rule(), "minecraft:ore_vein").size());
        assertEquals(biomes.size() - MaterialProgramCompiler.ORE_VEIN_BUDGET + 1, count(holder[0], ExportLimit.WALL_PALETTE));
    }

    private MaterialProgram compile(String dimension, Map<String, String> biomes, DataVersion target) throws Exception {
        return compile(dimension, biomes, target, new PackCompileContext[1]);
    }

    private MaterialProgram compile(String dimension, Map<String, String> biomes, DataVersion target, PackCompileContext[] holder) throws Exception {
        return compile(dimension, biomes, target, holder, context -> {
        });
    }

    private MaterialProgram compile(String dimension, Map<String, String> biomes, DataVersion target, PackCompileContext[] holder,
                                    Consumer<PackCompileContext> setup) throws Exception {
        File pack = StageTestPacks.pack(temporaryFolder.newFolder(), dimension, biomes);
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = StageTestPacks.context(data, target, new ArrayList<>(biomes.keySet()));
            setup.accept(context);
            holder[0] = context;
            return new MaterialProgramCompiler(context, BlockStateResolver.forContext(context)).compile();
        } finally {
            data.close();
        }
    }

    private static List<JsonResource> wallShells(PackCompileContext context) {
        String path = "data/iris_export/worldgen/density_function/" + MaterialProgramCompiler.WALL_SHELL + ".json";
        return context.jsonResources().stream().filter(resource -> resource.path().equals(path)).toList();
    }

    private static String layers(String... layers) {
        return "{\"layers\":[" + String.join(",", layers) + "]}";
    }

    private static boolean isBiomeCondition(JsonObject rule) {
        return "minecraft:condition".equals(rule.get("type").getAsString())
                && "minecraft:biome".equals(rule.getAsJsonObject("if_true").get("type").getAsString());
    }

    private static boolean isFloorGuard(JsonObject rule) {
        if (!"minecraft:condition".equals(rule.get("type").getAsString())) {
            return false;
        }
        JsonObject condition = rule.getAsJsonObject("if_true");
        return "minecraft:stone_depth".equals(condition.get("type").getAsString()) && "floor".equals(condition.get("surface_type").getAsString());
    }

    private static List<List<Integer>> bands(JsonObject rule, String biomeId) {
        JsonObject branch = StageTestPacks.ofType(rule, "minecraft:condition").stream()
                .filter(object -> "minecraft:biome".equals(object.getAsJsonObject("if_true").get("type").getAsString())
                && object.getAsJsonObject("if_true").getAsJsonArray("biome_is").size() == 1
                && biomeId.equals(object.getAsJsonObject("if_true").getAsJsonArray("biome_is").get(0).getAsString())).findFirst().orElseThrow();
        List<List<Integer>> bands = new ArrayList<>();
        for (JsonObject depth : StageTestPacks.ofType(branch.get("then_run"), "minecraft:stone_depth")) {
            bands.add(List.of(depth.get("offset").getAsInt(), depth.get("secondary_depth_range").getAsInt()));
        }
        return bands;
    }

    private static double ramp(JsonObject gradient, int y) {
        int trueAt = gradient.getAsJsonObject("true_at_and_below").get("absolute").getAsInt();
        int falseAt = gradient.getAsJsonObject("false_at_and_above").get("absolute").getAsInt();
        if (y <= trueAt) {
            return 1.0;
        }
        if (y >= falseAt) {
            return 0.0;
        }
        return (double) (falseAt - y) / (falseAt - trueAt);
    }

    private static String wall(String palette) {
        return "{\"layers\":[{\"palette\":[{\"block\":\"stone\"}]}],\"wall\":{\"palette\":[" + palette + "]}}";
    }

    private static String wallFiller(MaterialProgram program, String biome) {
        String biomeId = StageTestPacks.biomeId(biome);
        for (JsonObject condition : StageTestPacks.ofType(program.rule(), "minecraft:condition")) {
            JsonObject test = condition.getAsJsonObject("if_true");
            JsonObject rule = condition.getAsJsonObject("then_run");
            if (!"minecraft:biome".equals(test.get("type").getAsString()) || !"minecraft:ore_vein".equals(rule.get("type").getAsString())) {
                continue;
            }
            for (JsonElement member : test.getAsJsonArray("biome_is")) {
                if (member.getAsString().equals(biomeId)) {
                    return rule.getAsJsonObject("filler_block").get("id").getAsString();
                }
            }
        }
        throw new AssertionError("Biome " + biome + " has no wall rule");
    }

    private static boolean hasDiagnostic(PackCompileContext context, ExportLimit limit, String resource) {
        for (ExportDiagnostic diagnostic : context.diagnostics()) {
            if (diagnostic.limit() == limit && diagnostic.resource().equals(resource)) {
                return true;
            }
        }
        return false;
    }

    private static int count(PackCompileContext context, ExportLimit limit) {
        int count = 0;
        for (ExportDiagnostic diagnostic : context.diagnostics()) {
            if (diagnostic.limit() == limit) {
                count++;
            }
        }
        return count;
    }
}
