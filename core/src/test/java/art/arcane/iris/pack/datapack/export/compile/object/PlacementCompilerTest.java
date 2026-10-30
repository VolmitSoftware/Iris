package art.arcane.iris.pack.datapack.export.compile.object;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.TemplateBlock;
import art.arcane.iris.pack.datapack.export.TemplateVolume;
import art.arcane.iris.pack.datapack.export.VanillaBlockState;
import art.arcane.iris.pack.datapack.export.compile.decoration.GenerationStep;
import art.arcane.iris.pack.datapack.export.ir.DimensionDef;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.JsonResource;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.structure.object.IrisObjectTranslate;
import art.arcane.iris.structure.object.ObjectPlaceMode;
import art.arcane.volmlib.util.collection.KList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class PlacementCompilerTest {
    private static final String NAMESPACE = "iris_export";
    private static final int WINDOW_MIN = -16;
    private static final int WINDOW_MAX = 31;
    private static final int CHUNK = 16;
    private static final DimensionDef DIMENSION = new DimensionDef("main", -64, 384, 384, 63,
            "minecraft:stone", "minecraft:water", new JsonObject());

    @Test
    public void narrowObjectUsesTemplateFeature() {
        IrisObjectPlacement placement = placement(ObjectPlaceMode.CENTER_HEIGHT, 0.5, 3);
        placement.setTranslate(new IrisObjectTranslate().setY(2));
        List<ExportDiagnostic> diagnostics = new ArrayList<>();

        CompiledPlacement compiled = compiler(DataVersion.V26_3).compile(input(placement, objects(5, 9), List.of()), diagnostics);

        assertSame(GenerationStep.SURFACE_STRUCTURES, compiled.step());
        assertEquals(1, compiled.placedFeatures().size());
        assertTrue(compiled.structures().isEmpty());
        JsonObject feature = feature(compiled, "worldgen/feature/");
        assertEquals("minecraft:template", feature.get("type").getAsString());
        JsonArray templates = feature.getAsJsonArray("templates");
        assertEquals(2, templates.size());
        assertEquals(templates.get(0).getAsJsonObject().get("weight"), templates.get(1).getAsJsonObject().get("weight"));
        assertFalse(templates.get(0).getAsJsonObject().getAsJsonObject("data").has("rotations"));
        JsonObject placed = placed(compiled);
        assertEquals(List.of("minecraft:random_chance", "minecraft:count", "minecraft:in_square", "minecraft:heightmap",
                "minecraft:block_predicate_filter", "minecraft:offset", "minecraft:biome"), types(placed));
        JsonArray modifiers = placed.getAsJsonArray("placement");
        assertEquals(0.5, modifiers.get(0).getAsJsonObject().get("chance").getAsDouble(), 1e-6);
        assertEquals(3, modifiers.get(1).getAsJsonObject().get("count").getAsInt());
        assertEquals("WORLD_SURFACE_WG", modifiers.get(3).getAsJsonObject().get("heightmap").getAsString());
        assertEquals(1, modifiers.get(5).getAsJsonObject().get("y").getAsInt());
        JsonObject support = modifiers.get(4).getAsJsonObject().getAsJsonObject("predicate");
        assertEquals("minecraft:volume_match", support.get("type").getAsString());
        assertTrue(diagnostics.isEmpty());
    }

    @Test
    public void wideObjectBecomesSinglePieceStructure() {
        IrisObjectPlacement placement = placement(ObjectPlaceMode.CENTER_HEIGHT, 0.02, 1);
        List<ExportDiagnostic> diagnostics = new ArrayList<>();

        CompiledPlacement compiled = compiler(DataVersion.V26_3).compile(input(placement, objects(40), List.of()), diagnostics);

        assertTrue(compiled.placedFeatures().isEmpty());
        assertEquals(1, compiled.structures().size());
        JsonObject structure = resource(compiled, "worldgen/structure/");
        assertEquals("minecraft:jigsaw", structure.get("type").getAsString());
        assertEquals(compiled.structures().getFirst(), structure.get("start_pool").getAsString());
        assertTrue(structure.get("biomes").getAsString().startsWith("#iris_export:has_structure/object/"));
        assertEquals("WORLD_SURFACE_WG", structure.get("project_start_to_heightmap").getAsString());
        JsonObject pool = resource(compiled, "worldgen/template_pool/");
        JsonArray elements = pool.getAsJsonArray("elements");
        assertEquals(1, elements.size());
        assertEquals("minecraft:single_pool_element", elements.get(0).getAsJsonObject().getAsJsonObject("element").get("element_type").getAsString());
        JsonObject set = resource(compiled, "worldgen/structure_set/");
        JsonObject spread = set.getAsJsonObject("placement");
        int spacing = spread.get("spacing").getAsInt();
        double frequency = spread.has("frequency") ? spread.get("frequency").getAsDouble() : 1;
        assertEquals(0.02, frequency / (spacing * spacing), 0.001);
        assertTrue(spread.get("separation").getAsInt() < spacing);
        assertEquals(1, diagnostics.size());
        assertSame(ExportLimit.WIDE_OBJECT, diagnostics.getFirst().limit());
    }

    @Test
    public void mixedPlacementSplitsNarrowAndWideShares() {
        IrisObjectPlacement placement = placement(ObjectPlaceMode.CENTER_HEIGHT, 0.4, 2);

        CompiledPlacement compiled = compiler(DataVersion.V26_3).compile(input(placement, objects(8, 50), List.of()), new ArrayList<>());

        assertEquals(1, compiled.placedFeatures().size());
        assertEquals(1, compiled.structures().size());
        assertEquals(0.4 * 2 * 0.5, expectedPerChunk(placed(compiled)), 1e-6);
    }

    @Test
    public void writeWindowNeverExceeded() {
        DataVersion target = DataVersion.V26_3;
        for (ObjectPlaceMode mode : List.of(ObjectPlaceMode.CENTER_HEIGHT, ObjectPlaceMode.STILT, ObjectPlaceMode.PAINT)) {
            for (int footprint = 1; footprint <= 40; footprint++) {
                for (int translate : List.of(0, 3, -5, 12)) {
                    IrisObjectPlacement placement = placement(mode, 1, 1);
                    placement.setTranslate(new IrisObjectTranslate().setX(translate).setZ(-translate));
                    List<ObjectTemplates> objects = objects(footprint);
                    CompiledPlacement compiled = compiler(target).compile(input(placement, objects, List.of()), new ArrayList<>());
                    if (footprint + 2 * Math.abs(translate) > PlacementCompiler.MAX_FEATURE_WIDTH) {
                        assertTrue(compiled.placedFeatures().isEmpty());
                        continue;
                    }
                    assertWithinWindow(compiled, objects, target + " " + mode + " footprint " + footprint + " translate " + translate);
                }
            }
        }
    }

    @Test
    public void placementCountMatchesExpectation() {
        Random random = new Random(1234);
        DataVersion target = DataVersion.V26_3;
        for (double chance : List.of(1.0, 0.75, 0.3, 0.004)) {
            for (int density : List.of(1, 3, 8)) {
                IrisObjectPlacement placement = placement(ObjectPlaceMode.CENTER_HEIGHT, chance, density);
                CompiledPlacement compiled = compiler(target).compile(input(placement, objects(6), List.of()), new ArrayList<>());
                JsonObject placed = placed(compiled);
                double expected = chance * density;

                assertEquals(target + " chance " + chance + " density " + density, expected, expectedPerChunk(placed), expected * 0.05);
                double simulated = simulatePerChunk(placed, random, 20_000);
                assertEquals(target + " simulated chance " + chance + " density " + density, expected, simulated,
                        Math.max(expected * 0.05, 4 * Math.sqrt(expected * density / 20_000.0)));
            }
        }
    }

    @Test
    public void paintUsesTemplateProcessors() {
        IrisObjectPlacement placement = placement(ObjectPlaceMode.PAINT, 1, 1);

        CompiledPlacement compiled = compiler(DataVersion.V26_3).compile(input(placement, objects(9), List.of(rule())), new ArrayList<>());

        JsonObject feature = feature(compiled, "worldgen/feature/");
        JsonArray processors = feature.getAsJsonObject("processors").getAsJsonArray("processors");
        assertEquals(2, processors.size());
        assertEquals("minecraft:rule", processors.get(0).getAsJsonObject().get("processor_type").getAsString());
        JsonObject gravity = processors.get(1).getAsJsonObject();
        assertEquals("minecraft:gravity", gravity.get("processor_type").getAsString());
        assertEquals("WORLD_SURFACE_WG", gravity.get("heightmap").getAsString());
        assertEquals(-1, gravity.get("offset").getAsInt());
    }

    @Test
    public void stiltsUseOverlayPillarsOn26_3() {
        IrisObjectPlacement placement = placement(ObjectPlaceMode.STILT, 1, 1);
        PlacementInput input = new PlacementInput(placement, PlacementInput.Anchor.SURFACE, false, objects(20), List.of(),
                List.of(VanillaBlockState.parse("minecraft:oak_log[axis=y]")), "hut");

        CompiledPlacement compiled = compiler(DataVersion.V26_3).compile(input, new ArrayList<>());

        JsonObject feature = feature(compiled, "worldgen/feature/");
        assertEquals("minecraft:overlay", feature.get("type").getAsString());
        JsonArray features = feature.getAsJsonArray("features");
        assertEquals(5, features.size());
        assertEquals("minecraft:template", features.get(0).getAsJsonObject().getAsJsonObject("feature").get("type").getAsString());
        JsonObject tile = features.get(1).getAsJsonObject();
        JsonObject pillar = tile.getAsJsonObject("feature");
        assertEquals("minecraft:single_block_pillar", pillar.get("type").getAsString());
        assertEquals("down", pillar.get("direction").getAsString());
        assertEquals("minecraft:rule_based", pillar.getAsJsonObject("block").get("type").getAsString());
        assertEquals(List.of("minecraft:offset", "minecraft:cuboid", "minecraft:block_predicate_filter"), types(tile));
    }

    @Test
    public void caveAnchorScansForFloor() {
        IrisObjectPlacement placement = placement(ObjectPlaceMode.CENTER_HEIGHT, 1, 2);

        PlacementInput input = new PlacementInput(placement, PlacementInput.Anchor.CAVE, true, objects(5), List.of(), List.of(), "shroom");
        CompiledPlacement compiled = compiler(DataVersion.V26_3).compile(input, new ArrayList<>());

        assertSame(GenerationStep.VEGETAL_DECORATION, compiled.step());
        JsonObject placed = placed(compiled);
        assertEquals(List.of("minecraft:count", "minecraft:in_square", "minecraft:height_range", "minecraft:environment_scan", "minecraft:biome"), types(placed));
        JsonObject scan = placed.getAsJsonArray("placement").get(3).getAsJsonObject();
        assertEquals("down", scan.get("direction_of_search").getAsString());
    }

    @Test
    public void compiledJsonIsDeterministic() {
        IrisObjectPlacement placement = placement(ObjectPlaceMode.STILT, 0.3, 2);

        CompiledPlacement first = compiler(DataVersion.V26_3).compile(input(placement, objects(6, 12, 45), List.of(rule())), new ArrayList<>());
        CompiledPlacement second = compiler(DataVersion.V26_3).compile(input(placement, objects(6, 12, 45), List.of(rule())), new ArrayList<>());

        assertEquals(first, second);
        assertEquals(first.resources().toString(), second.resources().toString());
    }

    private static void assertWithinWindow(CompiledPlacement compiled, List<ObjectTemplates> objects, String label) {
        Map<String, Integer> footprints = new HashMap<>();
        for (ObjectTemplates object : objects) {
            for (TemplateChoice choice : object.choices()) {
                footprints.put(choice.variant().id(), choice.variant().volume().footprint());
            }
        }
        JsonObject placed = placed(compiled);
        int offsetX = 0;
        int offsetZ = 0;
        for (JsonElement element : placed.getAsJsonArray("placement")) {
            JsonObject modifier = element.getAsJsonObject();
            String type = modifier.get("type").getAsString();
            if (type.equals("minecraft:offset")) {
                offsetX += modifier.get("x").getAsInt();
                offsetZ += modifier.get("z").getAsInt();
            } else if (type.equals("minecraft:random_offset")) {
                offsetX += modifier.get("xz_spread").getAsInt();
                offsetZ += modifier.get("xz_spread").getAsInt();
            } else if (type.equals("minecraft:block_predicate_filter")) {
                JsonObject predicate = modifier.getAsJsonObject("predicate");
                if (predicate.get("type").getAsString().equals("minecraft:volume_match")) {
                    assertRange(label + " support", predicate.getAsJsonArray("min").get(0).getAsInt() + offsetX,
                            predicate.getAsJsonArray("max").get(0).getAsInt() + offsetX);
                }
            }
        }
        JsonObject feature = feature(compiled, null);
        String type = feature.get("type").getAsString();
        JsonObject body = feature.has("config") ? feature.getAsJsonObject("config") : feature;
        if (type.equals("minecraft:fossil")) {
            for (JsonElement id : body.getAsJsonArray("fossil_structures")) {
                assertTemplate(label, footprints.get(id.getAsString()), offsetX, offsetZ);
            }
            return;
        }
        JsonObject template = type.equals("minecraft:overlay")
                ? body.getAsJsonArray("features").get(0).getAsJsonObject().getAsJsonObject("feature")
                : feature;
        JsonObject templateBody = template.has("config") ? template.getAsJsonObject("config") : template;
        for (JsonElement entry : templateBody.getAsJsonArray("templates")) {
            Integer footprint = footprints.get(entry.getAsJsonObject().getAsJsonObject("data").get("id").getAsString());
            assertNotNull(label, footprint);
            assertTemplate(label, footprint, offsetX, offsetZ);
        }
        if (type.equals("minecraft:overlay")) {
            JsonArray features = body.getAsJsonArray("features");
            for (int index = 1; index < features.size(); index++) {
                JsonArray tile = features.get(index).getAsJsonObject().getAsJsonArray("placement");
                int tileX = tile.get(0).getAsJsonObject().get("x").getAsInt() + offsetX;
                int tileZ = tile.get(0).getAsJsonObject().get("z").getAsInt() + offsetZ;
                int size = tile.get(1).getAsJsonObject().get("xz_size").getAsInt();
                assertRange(label + " stilt tile x", tileX, tileX + size);
                assertRange(label + " stilt tile z", tileZ, tileZ + size);
            }
        }
    }

    private static void assertTemplate(String label, int footprint, int offsetX, int offsetZ) {
        int low = -(footprint / 2);
        int high = footprint - 1 - footprint / 2;
        assertRange(label + " template x", low + offsetX, high + offsetX);
        assertRange(label + " template z", low + offsetZ, high + offsetZ);
    }

    private static void assertRange(String label, int low, int high) {
        assertTrue(label + " starts at " + low, low >= WINDOW_MIN);
        assertTrue(label + " ends at " + (high + CHUNK - 1), high + CHUNK - 1 <= WINDOW_MAX);
    }

    private static double expectedPerChunk(JsonObject placed) {
        double expected = 1;
        for (JsonElement element : placed.getAsJsonArray("placement")) {
            JsonObject modifier = element.getAsJsonObject();
            switch (modifier.get("type").getAsString()) {
                case "minecraft:random_chance" -> expected *= modifier.get("chance").getAsDouble();
                case "minecraft:count" -> expected *= expectedCount(modifier.get("count"));
                default -> {
                }
            }
        }
        return expected;
    }

    private static double expectedCount(JsonElement count) {
        if (count.isJsonPrimitive()) {
            return count.getAsInt();
        }
        double total = 0;
        double weighted = 0;
        for (JsonElement entry : count.getAsJsonObject().getAsJsonArray("distribution")) {
            int weight = entry.getAsJsonObject().get("weight").getAsInt();
            total += weight;
            weighted += weight * entry.getAsJsonObject().get("data").getAsInt();
        }
        return weighted / total;
    }

    private static double simulatePerChunk(JsonObject placed, Random random, int chunks) {
        long positions = 0;
        for (int chunk = 0; chunk < chunks; chunk++) {
            int stream = 1;
            for (JsonElement element : placed.getAsJsonArray("placement")) {
                JsonObject modifier = element.getAsJsonObject();
                String type = modifier.get("type").getAsString();
                if (type.equals("minecraft:random_chance")) {
                    int kept = 0;
                    for (int index = 0; index < stream; index++) {
                        if (random.nextFloat() < modifier.get("chance").getAsFloat()) {
                            kept++;
                        }
                    }
                    stream = kept;
                } else if (type.equals("minecraft:count")) {
                    int expanded = 0;
                    for (int index = 0; index < stream; index++) {
                        expanded += sampleCount(modifier.get("count"), random);
                    }
                    stream = expanded;
                }
            }
            positions += stream;
        }
        return positions / (double) chunks;
    }

    private static int sampleCount(JsonElement count, Random random) {
        if (count.isJsonPrimitive()) {
            return count.getAsInt();
        }
        JsonArray distribution = count.getAsJsonObject().getAsJsonArray("distribution");
        int total = 0;
        for (JsonElement entry : distribution) {
            total += entry.getAsJsonObject().get("weight").getAsInt();
        }
        int roll = random.nextInt(total);
        for (JsonElement entry : distribution) {
            roll -= entry.getAsJsonObject().get("weight").getAsInt();
            if (roll < 0) {
                return entry.getAsJsonObject().get("data").getAsInt();
            }
        }
        throw new IllegalStateException("Weighted list roll fell through");
    }

    private static PlacementCompiler compiler(DataVersion target) {
        return new PlacementCompiler(NAMESPACE, ExportCapabilities.forTarget(target), DIMENSION);
    }

    private static IrisObjectPlacement placement(ObjectPlaceMode mode, double chance, int density) {
        IrisObjectPlacement placement = new IrisObjectPlacement();
        placement.setPlace(new KList<>(List.of("objects/a")));
        placement.setMode(mode);
        placement.setChance(chance);
        placement.setDensity(density);
        return placement;
    }

    private static PlacementInput input(IrisObjectPlacement placement, List<ObjectTemplates> objects, List<JsonObject> processors) {
        return new PlacementInput(placement, PlacementInput.Anchor.SURFACE, false, objects, processors, List.of(), "thing");
    }

    private static List<ObjectTemplates> objects(int... footprints) {
        List<ObjectTemplates> objects = new ArrayList<>();
        for (int index = 0; index < footprints.length; index++) {
            int footprint = footprints[index];
            TemplateBlock block = new TemplateBlock(0, 0, 0, VanillaBlockState.parse("minecraft:stone"), null);
            BakedVariant variant = new BakedVariant(NAMESPACE + ":object/o" + index + "/v" + footprint,
                    new TemplateVolume(footprint, 4, footprint, List.of(block)), 0);
            objects.add(new ObjectTemplates("o" + index, List.of(new TemplateChoice(variant, List.of(0, 1, 2, 3), 1))));
        }
        return objects;
    }

    private static JsonObject rule() {
        JsonObject rule = new JsonObject();
        rule.addProperty("processor_type", "minecraft:rule");
        rule.add("rules", new JsonArray());
        return rule;
    }

    private static JsonObject feature(CompiledPlacement compiled, String folder) {
        String featureId = placed(compiled).get("feature").getAsString();
        String path = featureId.substring(NAMESPACE.length() + 1) + ".json";
        for (JsonResource resource : compiled.resources()) {
            if (resource.path().endsWith("/" + path) && !resource.path().contains("placed_feature")
                    && (folder == null || resource.path().contains(folder))) {
                return resource.json().getAsJsonObject();
            }
        }
        throw new AssertionError("No feature " + featureId + " in " + folder);
    }

    private static JsonObject placed(CompiledPlacement compiled) {
        return resource(compiled, "worldgen/placed_feature/");
    }

    private static JsonObject resource(CompiledPlacement compiled, String folder) {
        JsonObject found = null;
        for (JsonResource resource : compiled.resources()) {
            if (resource.path().contains(folder)) {
                assertNull("Two resources in " + folder, found);
                found = resource.json().getAsJsonObject();
            }
        }
        assertNotNull("No resource in " + folder, found);
        return found;
    }

    private static List<String> types(JsonObject placed) {
        List<String> types = new ArrayList<>();
        for (JsonElement element : placed.getAsJsonArray("placement")) {
            types.add(element.getAsJsonObject().get("type").getAsString());
        }
        return types;
    }
}
