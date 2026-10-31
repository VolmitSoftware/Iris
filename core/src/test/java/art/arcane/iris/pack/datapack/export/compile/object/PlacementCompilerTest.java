package art.arcane.iris.pack.datapack.export.compile.object;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.TemplateBlock;
import art.arcane.iris.pack.datapack.export.TemplateVolume;
import art.arcane.iris.pack.datapack.export.VanillaBlockState;
import art.arcane.iris.pack.datapack.export.compile.StageTestPacks;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

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
                "minecraft:block_predicate_filter", "minecraft:block_predicate_filter", "minecraft:offset", "minecraft:biome"), types(placed));
        JsonArray modifiers = placed.getAsJsonArray("placement");
        assertEquals(0.5, modifiers.get(0).getAsJsonObject().get("chance").getAsDouble(), 1e-6);
        assertEquals(3, modifiers.get(1).getAsJsonObject().get("count").getAsInt());
        assertEquals("OCEAN_FLOOR_WG", modifiers.get(3).getAsJsonObject().get("heightmap").getAsString());
        assertEquals(1, modifiers.get(6).getAsJsonObject().get("y").getAsInt());
        assertTrue(StageTestPacks.ofType(placed, "minecraft:volume_match").isEmpty());
        assertTrue(diagnostics.isEmpty());
    }

    @Test
    public void surfaceObjectsStandOnTerrainInsteadOfCanopiesOrWater() {
        IrisObjectPlacement placement = placement(ObjectPlaceMode.CENTER_HEIGHT, 1, 1);

        JsonObject placed = placed(compiler(DataVersion.V26_3).compile(input(placement, objects(5), List.of()), new ArrayList<>()));
        JsonObject filter = filters(placed).getFirst();

        assertEquals("OCEAN_FLOOR_WG", StageTestPacks.ofType(placed, "minecraft:heightmap").getFirst().get("heightmap").getAsString());
        List<String> rejected = new ArrayList<>();
        for (JsonObject negated : StageTestPacks.ofType(filter, "minecraft:not")) {
            JsonObject inner = negated.getAsJsonObject("predicate");
            rejected.add(inner.has("tag") ? inner.get("tag").getAsString() : inner.get("type").getAsString());
        }
        assertTrue(rejected.toString(), rejected.contains("minecraft:leaves"));
        assertTrue(rejected.toString(), rejected.contains("minecraft:logs"));
        assertTrue(rejected.toString(), rejected.contains("minecraft:matching_fluids"));
        assertFalse(StageTestPacks.ofType(filter, "minecraft:solid").isEmpty());
    }

    @Test
    public void supportFollowsTheObjectBaseInsteadOfAFlatPlane() {
        IrisObjectPlacement placement = placement(ObjectPlaceMode.CENTER_HEIGHT, 1, 1);
        placement.setSurfaceSupportBuffer(1);
        placement.setSurfaceSupportDepth(2);
        TemplateBlock trunk = new TemplateBlock(4, 0, 4, VanillaBlockState.parse("minecraft:oak_log[axis=y]"), null);
        TemplateBlock crown = new TemplateBlock(0, 5, 0, VanillaBlockState.parse("minecraft:oak_leaves"), null);
        BakedVariant tree = new BakedVariant(NAMESPACE + ":object/tree/v", new TemplateVolume(9, 6, 9, List.of(trunk, crown)), 0, TreeRoots.NONE);
        List<ObjectTemplates> objects = List.of(new ObjectTemplates("tree", List.of(new TemplateChoice(tree, List.of(0, 1, 2, 3), 1))));

        JsonObject support = filters(placed(compiler(DataVersion.V26_3).compile(input(placement, objects, List.of()), new ArrayList<>()))).get(1);

        int reach = 0;
        int depth = 0;
        for (JsonObject solid : StageTestPacks.ofType(support, "minecraft:solid")) {
            JsonArray offset = solid.getAsJsonArray("offset");
            reach = Math.max(reach, Math.max(Math.abs(offset.get(0).getAsInt()), Math.abs(offset.get(2).getAsInt())));
            depth = Math.min(depth, offset.get(1).getAsInt());
        }
        assertEquals(1, reach);
        assertEquals(-16, depth);
        assertFalse(StageTestPacks.ofType(support, "minecraft:any_of").isEmpty());
    }

    @Test
    public void supportAcceptsSteepGroundAnywhereInTheScanWindow() {
        IrisObjectPlacement placement = placement(ObjectPlaceMode.CENTER_HEIGHT, 1, 1);
        placement.setSurfaceSupportBuffer(2);
        placement.setSurfaceSupportDepth(2);
        TemplateBlock trunk = new TemplateBlock(0, 0, 4, VanillaBlockState.parse("minecraft:spruce_log[axis=y]"), null);
        BakedVariant tree = new BakedVariant(NAMESPACE + ":object/tree/v", new TemplateVolume(9, 20, 9, List.of(trunk)), 0, TreeRoots.NONE);
        List<ObjectTemplates> objects = List.of(new ObjectTemplates("tree", List.of(new TemplateChoice(tree, List.of(0, 1, 2, 3), 1))));

        JsonObject support = filters(placed(compiler(DataVersion.V26_3).compile(input(placement, objects, List.of()), new ArrayList<>()))).get(1);

        Set<String> corners = new HashSet<>();
        for (JsonObject solid : StageTestPacks.ofType(support, "minecraft:solid")) {
            JsonArray offset = solid.getAsJsonArray("offset");
            corners.add(offset.get(0).getAsInt() + "," + offset.get(2).getAsInt());
        }
        assertEquals(Set.of("-6,-6", "-6,6", "6,-6", "6,6"), corners);
        Set<String> steepSlope = new HashSet<>();
        Set<String> thinLedge = new HashSet<>();
        for (String corner : corners) {
            for (int y = -12; y >= -40; y--) {
                steepSlope.add(cell(corner, y));
            }
            thinLedge.add(cell(corner, -12));
        }
        JsonObject predicate = support.getAsJsonObject("predicate");
        assertTrue("ground 11 blocks below every corner must support the object", supported(predicate, steepSlope));
        assertFalse("a one block ledge is thinner than surfaceSupportDepth", supported(predicate, thinLedge));
        assertFalse("open air below every corner must not support the object", supported(predicate, Set.of()));
    }

    @Test
    public void stiltedObjectsRelyOnTheirStiltsInsteadOfCornerSupport() {
        IrisObjectPlacement placement = placement(ObjectPlaceMode.FAST_MIN_STILT, 1, 1);
        PlacementInput input = new PlacementInput(placement, PlacementInput.Anchor.SURFACE, true, objects(25), List.of(),
                List.of(VanillaBlockState.parse("minecraft:dark_oak_wood[axis=y]")), "willow");

        List<JsonObject> filters = filters(placed(compiler(DataVersion.V26_3).compile(input, new ArrayList<>())));

        assertEquals(1, filters.size());
        assertFalse(StageTestPacks.ofType(filters.getFirst(), "minecraft:matching_block_tag").isEmpty());
    }

    @Test
    public void slopeConditionRejectsSteepAnchors() {
        IrisObjectPlacement placement = placement(ObjectPlaceMode.CENTER_HEIGHT, 1, 1);
        placement.setRequireSurfaceSupport(false);
        placement.getSlopeCondition().setMaximumSlope(1.5);
        List<ExportDiagnostic> diagnostics = new ArrayList<>();

        List<JsonObject> filters = filters(placed(compiler(DataVersion.V26_3).compile(input(placement, objects(5), List.of()), diagnostics)));

        assertTrue(diagnostics.stream().anyMatch(diagnostic -> diagnostic.limit() == ExportLimit.SLOPE));
        assertEquals(2, filters.size());
        JsonObject slope = filters.get(1).getAsJsonObject("predicate");
        Set<String> flat = new HashSet<>();
        Set<String> cliff = new HashSet<>();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                for (int y = -20; y <= -1; y++) {
                    flat.add(x + "," + y + "," + z);
                    cliff.add(x + "," + y + "," + z);
                }
            }
        }
        for (int y = 0; y <= 4; y++) {
            cliff.add("3," + y + ",0");
        }
        assertTrue("flat ground must pass the slope limit", supported(slope, flat));
        assertFalse("a wall 3 blocks higher 3 blocks away is steeper than slope 1.5", supported(slope, cliff));
    }

    @Test
    public void forcePlaceIgnoresSlopeCondition() {
        IrisObjectPlacement placement = placement(ObjectPlaceMode.CENTER_HEIGHT, 1, 1);
        placement.setForcePlace(true);
        placement.getSlopeCondition().setMinimumSlope(2);

        List<JsonObject> filters = filters(placed(compiler(DataVersion.V26_3).compile(input(placement, objects(5), List.of()), new ArrayList<>())));

        assertEquals(1, filters.size());
    }

    private static String cell(String corner, int y) {
        String[] parts = corner.split(",");
        return parts[0] + "," + y + "," + parts[1];
    }

    private static boolean supported(JsonObject predicate, Set<String> solids) {
        String type = predicate.get("type").getAsString();
        return switch (type) {
            case "minecraft:solid" -> {
                JsonArray offset = predicate.getAsJsonArray("offset");
                yield solids.contains(offset.get(0).getAsInt() + "," + offset.get(1).getAsInt() + "," + offset.get(2).getAsInt());
            }
            case "minecraft:all_of" -> {
                for (JsonElement child : predicate.getAsJsonArray("predicates")) {
                    if (!supported(child.getAsJsonObject(), solids)) {
                        yield false;
                    }
                }
                yield true;
            }
            case "minecraft:any_of" -> {
                for (JsonElement child : predicate.getAsJsonArray("predicates")) {
                    if (supported(child.getAsJsonObject(), solids)) {
                        yield true;
                    }
                }
                yield false;
            }
            case "minecraft:not" -> !supported(predicate.getAsJsonObject("predicate"), solids);
            default -> throw new AssertionError("Unexpected support predicate " + type);
        };
    }

    @Test
    public void underwaterObjectsNeedWaterAndStayBelowTheSurface() {
        IrisObjectPlacement placement = placement(ObjectPlaceMode.CENTER_HEIGHT, 1, 1);
        placement.setUnderwater(true);
        placement.setTranslate(new IrisObjectTranslate().setY(2));

        JsonObject placed = placed(compiler(DataVersion.V26_3).compile(input(placement, columns(7), List.of()), new ArrayList<>()));

        List<JsonObject> filters = filters(placed);
        assertEquals(2, filters.size());
        assertTrue(StageTestPacks.ofType(filters.getFirst(), "minecraft:not").stream()
                .noneMatch(negated -> !StageTestPacks.ofType(negated, "minecraft:matching_fluids").isEmpty()));
        JsonObject water = StageTestPacks.ofType(filters.getFirst(), "minecraft:matching_fluids").getFirst();
        assertFalse(water.toString(), water.has("offset"));
        assertEquals(DIMENSION.fluidHeight() - 3 * (7 / 2) - 2, ceiling(placed));
    }

    @Test
    public void underwaterObjectsOfDifferentHeightsKeepTheirOwnDepth() {
        IrisObjectPlacement placement = placement(ObjectPlaceMode.CENTER_HEIGHT, 0.6, 2);
        placement.setUnderwater(true);

        CompiledPlacement compiled = compiler(DataVersion.V26_3).compile(input(placement, columns(4, 9, 4), List.of()), new ArrayList<>());

        assertEquals(2, compiled.placedFeatures().size());
        List<Integer> ceilings = new ArrayList<>();
        double perChunk = 0;
        for (JsonResource resource : compiled.resources()) {
            if (resource.path().contains("worldgen/placed_feature/")) {
                JsonObject placed = resource.json().getAsJsonObject();
                ceilings.add(ceiling(placed));
                perChunk += expectedPerChunk(placed);
            }
        }
        ceilings.sort(Integer::compareTo);
        assertEquals(List.of(DIMENSION.fluidHeight() - 3 * (9 / 2), DIMENSION.fluidHeight() - 3 * (4 / 2)), ceilings);
        assertEquals(0.6 * 2, perChunk, 1e-6);
    }

    @Test
    public void onwaterObjectsStandOnTheWaterSurface() {
        IrisObjectPlacement placement = placement(ObjectPlaceMode.CENTER_HEIGHT, 1, 1);
        placement.setOnwater(true);

        JsonObject placed = placed(compiler(DataVersion.V26_3).compile(input(placement, objects(5), List.of()), new ArrayList<>()));

        assertEquals("WORLD_SURFACE_WG", StageTestPacks.ofType(placed, "minecraft:heightmap").getFirst().get("heightmap").getAsString());
        List<JsonObject> filters = filters(placed);
        assertEquals(1, filters.size());
        assertTrue(StageTestPacks.ofType(filters.getFirst(), "minecraft:solid").isEmpty());
        JsonObject water = StageTestPacks.ofType(filters.getFirst(), "minecraft:matching_fluids").getFirst();
        assertEquals("[0,-1,0]", water.get("offset").toString());
        assertTrue(StageTestPacks.ofType(placed, "minecraft:not").isEmpty());
    }

    @Test
    public void onwaterPaintDropsOntoTheWaterSurface() {
        IrisObjectPlacement placement = placement(ObjectPlaceMode.PAINT, 1, 1);
        placement.setOnwater(true);
        placement.setTranslate(new IrisObjectTranslate().setY(1));

        CompiledPlacement compiled = compiler(DataVersion.V26_3).compile(input(placement, objects(1), List.of()), new ArrayList<>());

        JsonObject gravity = feature(compiled, "worldgen/feature/").getAsJsonObject("processors").getAsJsonArray("processors").get(0).getAsJsonObject();
        assertEquals("WORLD_SURFACE_WG", gravity.get("heightmap").getAsString());
        assertEquals(0, gravity.get("offset").getAsInt());
        assertEquals("WORLD_SURFACE_WG", StageTestPacks.ofType(placed(compiled), "minecraft:heightmap").getFirst().get("heightmap").getAsString());
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
        for (ObjectPlaceMode mode : List.of(ObjectPlaceMode.CENTER_HEIGHT, ObjectPlaceMode.STILT, ObjectPlaceMode.PAINT,
                ObjectPlaceMode.MIN_HEIGHT, ObjectPlaceMode.FAST_MAX_HEIGHT)) {
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
        assertEquals("OCEAN_FLOOR_WG", gravity.get("heightmap").getAsString());
        assertEquals(-1, gravity.get("offset").getAsInt());
    }

    @Test
    public void stiltsUseOverlayPillarsOn26_3() {
        IrisObjectPlacement placement = placement(ObjectPlaceMode.CENTER_STILT, 1, 1);
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

    @Test
    public void minHeightScansDownUntilTheWholeFootprintHasGround() {
        IrisObjectPlacement placement = placement(ObjectPlaceMode.MIN_HEIGHT, 1, 1);

        CompiledPlacement compiled = compiler(DataVersion.V26_3).compile(input(placement, objects(9), List.of()), new ArrayList<>());

        JsonObject placed = placed(compiled);
        assertEquals(List.of("minecraft:in_square", "minecraft:heightmap", "minecraft:block_predicate_filter",
                "minecraft:block_predicate_filter", "minecraft:biome"), types(placed));
        JsonObject feature = feature(compiled, "worldgen/feature/");
        assertEquals("minecraft:weighted_random_selector", feature.get("type").getAsString());
        JsonArray branches = feature.getAsJsonArray("features");
        assertEquals(1, branches.size());
        JsonObject branch = branches.get(0).getAsJsonObject().getAsJsonObject("data");
        assertEquals("minecraft:template", branch.getAsJsonObject("feature").get("type").getAsString());
        assertEquals(List.of("minecraft:environment_scan", "minecraft:offset"), types(branch));
        JsonObject scan = branch.getAsJsonArray("placement").get(0).getAsJsonObject();
        assertEquals("down", scan.get("direction_of_search").getAsString());
        assertEquals(32, scan.get("max_steps").getAsInt());
        assertEquals(List.of("-4,-1,-4..4,-1,4"), boxes(scan));
        JsonObject match = StageTestPacks.ofType(scan, "minecraft:volume_match").getFirst().getAsJsonObject("match");
        assertFalse(StageTestPacks.ofType(match, "minecraft:solid").isEmpty());
        assertFalse(StageTestPacks.ofType(match, "minecraft:matching_fluids").isEmpty());
        assertEquals(-1, branch.getAsJsonArray("placement").get(1).getAsJsonObject().get("y").getAsInt());
    }

    @Test
    public void maxHeightScansUpUntilEveryColumnIsClear() {
        IrisObjectPlacement placement = placement(ObjectPlaceMode.MAX_HEIGHT, 1, 1);
        placement.setUnderwater(true);

        CompiledPlacement compiled = compiler(DataVersion.V26_3).compile(input(placement, objects(9), List.of()), new ArrayList<>());

        JsonObject scan = branchScan(feature(compiled, "worldgen/feature/"), 0);
        assertEquals("up", scan.get("direction_of_search").getAsString());
        assertEquals(List.of("-4,0,-4..4,0,4"), boxes(scan));
        JsonObject match = StageTestPacks.ofType(scan, "minecraft:volume_match").getFirst().getAsJsonObject("match");
        assertEquals("minecraft:not", match.get("type").getAsString());
        assertTrue(StageTestPacks.ofType(match, "minecraft:matching_fluids").isEmpty());
    }

    @Test
    public void fastHeightModesProbeTheRuntimeSamplePoints() {
        IrisObjectPlacement placement = placement(ObjectPlaceMode.FAST_MIN_HEIGHT, 1, 1);

        CompiledPlacement compiled = compiler(DataVersion.V26_3).compile(input(placement, objects(9), List.of()), new ArrayList<>());

        JsonObject scan = branchScan(feature(compiled, "worldgen/feature/"), 0);
        assertEquals(List.of("-4,-1,-4..-4,-1,-4", "-4,-1,1..-4,-1,1", "1,-1,-4..1,-1,-4", "1,-1,1..1,-1,1"), boxes(scan));
    }

    @Test
    public void heightScanCoversEveryAllowedQuarter() {
        TemplateBlock block = new TemplateBlock(0, 0, 0, VanillaBlockState.parse("minecraft:stone"), null);
        BakedVariant wall = new BakedVariant(NAMESPACE + ":object/wall/v", new TemplateVolume(5, 4, 9, List.of(block)), 0, TreeRoots.NONE);
        IrisObjectPlacement placement = placement(ObjectPlaceMode.MIN_HEIGHT, 1, 1);

        JsonObject turning = feature(compiler(DataVersion.V26_3).compile(input(placement, List.of(new ObjectTemplates("wall",
                List.of(new TemplateChoice(wall, List.of(0, 1, 2, 3), 1)))), List.of()), new ArrayList<>()), "worldgen/feature/");
        JsonObject fixed = feature(compiler(DataVersion.V26_3).compile(input(placement, List.of(new ObjectTemplates("wall",
                List.of(new TemplateChoice(wall, List.of(0), 1)))), List.of()), new ArrayList<>()), "worldgen/feature/");

        assertEquals(List.of("-2,-1,-4..2,-1,4", "-4,-1,-2..4,-1,2"), boxes(branchScan(turning, 0)));
        assertEquals(List.of("-2,-1,-4..2,-1,4"), boxes(branchScan(fixed, 0)));
    }

    @Test
    public void everyTemplateAnchorsOnItsOwnFootprint() {
        IrisObjectPlacement placement = placement(ObjectPlaceMode.MIN_HEIGHT, 1, 1);

        JsonObject feature = feature(compiler(DataVersion.V26_3).compile(input(placement, objects(5, 9), List.of()), new ArrayList<>()),
                "worldgen/feature/");

        JsonArray branches = feature.getAsJsonArray("features");
        assertEquals(2, branches.size());
        assertEquals(branches.get(0).getAsJsonObject().get("weight"), branches.get(1).getAsJsonObject().get("weight"));
        assertEquals(List.of("-2,-1,-2..2,-1,2"), boxes(branchScan(feature, 0)));
        assertEquals(List.of("-4,-1,-4..4,-1,4"), boxes(branchScan(feature, 1)));
    }

    @Test
    public void maxStiltsAnchorHighThenStilt() {
        IrisObjectPlacement placement = placement(ObjectPlaceMode.STILT, 1, 1);
        PlacementInput input = new PlacementInput(placement, PlacementInput.Anchor.SURFACE, false, objects(20), List.of(),
                List.of(VanillaBlockState.parse("minecraft:oak_log[axis=y]")), "hut");

        JsonObject feature = feature(compiler(DataVersion.V26_3).compile(input, new ArrayList<>()), "worldgen/feature/");

        JsonObject branch = feature.getAsJsonArray("features").get(0).getAsJsonObject().getAsJsonObject("data");
        assertEquals("up", branchScan(feature, 0).get("direction_of_search").getAsString());
        JsonObject stilts = branch.getAsJsonObject("feature");
        assertEquals("minecraft:overlay", stilts.get("type").getAsString());
        assertEquals(5, stilts.getAsJsonArray("features").size());
        assertEquals("minecraft:single_block_pillar", stilts.getAsJsonArray("features").get(1).getAsJsonObject()
                .getAsJsonObject("feature").get("type").getAsString());
    }

    @Test
    public void groundedTreesExtendRootsToTheTerrain() {
        IrisObjectPlacement placement = placement(ObjectPlaceMode.CENTER_HEIGHT, 1, 1);

        CompiledPlacement compiled = compiler(DataVersion.V26_3).compile(tree(placement, rootedTree()), new ArrayList<>());

        JsonObject feature = feature(compiled, "worldgen/feature/");
        assertEquals("minecraft:weighted_random_selector", feature.get("type").getAsString());
        JsonArray branches = feature.getAsJsonArray("features");
        assertEquals(4, branches.size());
        List<String> rotations = new ArrayList<>();
        List<String> roots = new ArrayList<>();
        List<String> gaps = new ArrayList<>();
        String pillar = null;
        for (JsonElement element : branches) {
            assertEquals(branches.get(0).getAsJsonObject().get("weight"), element.getAsJsonObject().get("weight"));
            JsonObject branch = element.getAsJsonObject().getAsJsonObject("data");
            assertEquals(List.of("minecraft:offset", "minecraft:block_predicate_filter"), types(branch));
            gaps.addAll(boxes(branch.getAsJsonArray("placement").get(1)));
            JsonObject overlay = branch.getAsJsonObject("feature");
            assertEquals("minecraft:overlay", overlay.get("type").getAsString());
            JsonArray parts = overlay.getAsJsonArray("features");
            assertEquals(2, parts.size());
            JsonObject template = parts.get(0).getAsJsonObject().getAsJsonObject("feature");
            rotations.add(template.getAsJsonArray("templates").get(0).getAsJsonObject().getAsJsonObject("data")
                    .getAsJsonArray("rotations").get(0).getAsString());
            JsonObject root = parts.get(1).getAsJsonObject();
            pillar = root.get("feature").getAsString();
            JsonObject offset = root.getAsJsonArray("placement").get(0).getAsJsonObject();
            roots.add(offset.get("x").getAsInt() + "," + offset.get("y").getAsInt() + "," + offset.get("z").getAsInt());
        }
        assertEquals(List.of("none", "counterclockwise_90", "180", "clockwise_90"), rotations);
        assertEquals(List.of("1,-1,0", "0,-1,-1", "-1,-1,0", "0,-1,1"), roots);
        assertEquals(List.of("1,-4,0..1,-1,0", "0,-4,-1..0,-1,-1", "-1,-4,0..-1,-1,0", "0,-4,1..0,-1,1"), gaps);
        JsonObject shared = resourceAt(compiled, pillar);
        JsonObject gated = shared.getAsJsonArray("features").get(0).getAsJsonObject();
        assertEquals("minecraft:block_predicate_filter", gated.getAsJsonArray("placement").get(0).getAsJsonObject().get("type").getAsString());
        JsonObject column = gated.getAsJsonObject("feature");
        assertEquals("minecraft:single_block_pillar", column.get("type").getAsString());
        assertEquals("down", column.get("direction").getAsString());
        assertEquals("minecraft:spruce_wood", column.getAsJsonObject("block").get("id").getAsString());
        JsonObject cap = column.getAsJsonObject("cap_feature");
        assertEquals("minecraft:simple_block", cap.getAsJsonObject("feature").get("type").getAsString());
        assertEquals(-1, cap.getAsJsonArray("placement").get(0).getAsJsonObject().get("y").getAsInt());
    }

    @Test
    public void groundedRotationBranchesKeepTheTemplateOdds() {
        BakedVariant turning = rootedTree().getFirst().choices().getFirst().variant();
        BakedVariant fixed = new BakedVariant(NAMESPACE + ":object/tree/fixed", turning.volume(), 0, turning.roots());
        List<ObjectTemplates> objects = List.of(new ObjectTemplates("tree", List.of(
                new TemplateChoice(turning, List.of(0, 1, 2, 3), 1),
                new TemplateChoice(fixed, List.of(2), 1))));

        JsonObject feature = feature(compiler(DataVersion.V26_3).compile(tree(placement(ObjectPlaceMode.CENTER_HEIGHT, 1, 1), objects),
                new ArrayList<>()), "worldgen/feature/");

        List<Integer> weights = new ArrayList<>();
        for (JsonElement branch : feature.getAsJsonArray("features")) {
            weights.add(branch.getAsJsonObject().get("weight").getAsInt());
        }
        assertEquals(List.of(1, 1, 1, 1, 4), weights);
    }

    @Test
    public void groundedTreesReplaceStiltsWithRoots() {
        IrisObjectPlacement placement = placement(ObjectPlaceMode.CENTER_STILT, 1, 1);

        JsonObject feature = feature(compiler(DataVersion.V26_3).compile(tree(placement, rootedTree()), new ArrayList<>()), "worldgen/feature/");

        for (JsonElement element : feature.getAsJsonArray("features")) {
            JsonObject overlay = element.getAsJsonObject().getAsJsonObject("data").getAsJsonObject("feature");
            assertTrue(StageTestPacks.ofType(overlay, "minecraft:cuboid").isEmpty());
            assertEquals(2, overlay.getAsJsonArray("features").size());
        }
    }

    @Test
    public void forcedTreesKeepTheirBuiltRoots() {
        IrisObjectPlacement placement = placement(ObjectPlaceMode.CENTER_HEIGHT, 1, 1);
        placement.setForcePlace(true);

        JsonObject feature = feature(compiler(DataVersion.V26_3).compile(tree(placement, rootedTree()), new ArrayList<>()), "worldgen/feature/");

        assertEquals("minecraft:template", feature.get("type").getAsString());
    }

    @Test
    public void rootedHeightScansStayInsideTheFeatureWindow() {
        IrisObjectPlacement placement = placement(ObjectPlaceMode.FAST_MAX_HEIGHT, 1, 1);
        placement.setTranslate(new IrisObjectTranslate().setX(3).setZ(-3));
        List<ObjectTemplates> objects = rootedTree();

        CompiledPlacement compiled = compiler(DataVersion.V26_3).compile(tree(placement, objects), new ArrayList<>());

        assertWithinWindow(compiled, objects, "rooted fast max");
    }

    private static List<ObjectTemplates> rootedTree() {
        List<TemplateBlock> blocks = new ArrayList<>();
        for (int y = 1; y < 4; y++) {
            blocks.add(new TemplateBlock(2, y, 2, VanillaBlockState.parse("minecraft:oak_log[axis=y]"), null));
        }
        TemplateBlock root = new TemplateBlock(3, 0, 2, VanillaBlockState.parse("minecraft:spruce_wood[axis=y]"), null);
        blocks.add(root);
        BakedVariant variant = new BakedVariant(NAMESPACE + ":object/tree/rooted", new TemplateVolume(5, 4, 5, blocks), 0,
                new TreeRoots(List.of(root), 3));
        return List.of(new ObjectTemplates("tree", List.of(new TemplateChoice(variant, List.of(0, 1, 2, 3), 1))));
    }

    private static PlacementInput tree(IrisObjectPlacement placement, List<ObjectTemplates> objects) {
        return new PlacementInput(placement, PlacementInput.Anchor.SURFACE, true, objects, List.of(),
                List.of(VanillaBlockState.parse("minecraft:oak_log[axis=y]")), "tree");
    }

    private static JsonObject branchScan(JsonObject selector, int index) {
        JsonObject branch = selector.getAsJsonArray("features").get(index).getAsJsonObject().getAsJsonObject("data");
        return StageTestPacks.ofType(branch, "minecraft:environment_scan").getFirst();
    }

    private static List<String> boxes(JsonElement root) {
        List<String> boxes = new ArrayList<>();
        for (JsonObject volume : StageTestPacks.ofType(root, "minecraft:volume_match")) {
            JsonArray min = volume.getAsJsonArray("min");
            JsonArray max = volume.getAsJsonArray("max");
            boxes.add(min.get(0).getAsInt() + "," + min.get(1).getAsInt() + "," + min.get(2).getAsInt() + ".."
                    + max.get(0).getAsInt() + "," + max.get(1).getAsInt() + "," + max.get(2).getAsInt());
        }
        return boxes;
    }

    private static JsonObject resourceAt(CompiledPlacement compiled, String id) {
        String path = "/worldgen/feature/" + id.substring(NAMESPACE.length() + 1) + ".json";
        for (JsonResource resource : compiled.resources()) {
            if (resource.path().endsWith(path)) {
                return resource.json().getAsJsonObject();
            }
        }
        throw new AssertionError("No resource for " + id);
    }

    private static List<JsonObject> filters(JsonObject placed) {
        List<JsonObject> filters = new ArrayList<>();
        for (JsonElement element : placed.getAsJsonArray("placement")) {
            if (element.getAsJsonObject().get("type").getAsString().equals("minecraft:block_predicate_filter")) {
                filters.add(element.getAsJsonObject());
            }
        }
        return filters;
    }

    private static void assertWithinWindow(CompiledPlacement compiled, List<ObjectTemplates> objects, String label) {
        Map<String, Integer> footprints = new HashMap<>();
        for (ObjectTemplates object : objects) {
            for (TemplateChoice choice : object.choices()) {
                footprints.put(choice.variant().id(), choice.variant().volume().footprint());
            }
        }
        int[] offset = probeWindow(placed(compiled).getAsJsonArray("placement"), 0, 0, label);
        JsonObject feature = feature(compiled, null);
        if (!feature.get("type").getAsString().equals("minecraft:weighted_random_selector")) {
            assertFeatureWithinWindow(feature, footprints, offset[0], offset[1], label);
            return;
        }
        for (JsonElement entry : feature.getAsJsonArray("features")) {
            JsonObject branch = entry.getAsJsonObject().getAsJsonObject("data");
            int[] branchOffset = probeWindow(branch.getAsJsonArray("placement"), offset[0], offset[1], label);
            assertFeatureWithinWindow(branch.getAsJsonObject("feature"), footprints, branchOffset[0], branchOffset[1], label);
        }
    }

    private static int[] probeWindow(JsonArray modifiers, int startX, int startZ, String label) {
        int offsetX = startX;
        int offsetZ = startZ;
        for (JsonElement element : modifiers) {
            JsonObject modifier = element.getAsJsonObject();
            String type = modifier.get("type").getAsString();
            if (type.equals("minecraft:offset")) {
                offsetX += modifier.get("x").getAsInt();
                offsetZ += modifier.get("z").getAsInt();
            } else if (type.equals("minecraft:random_offset")) {
                offsetX += modifier.get("xz_spread").getAsInt();
                offsetZ += modifier.get("xz_spread").getAsInt();
            } else if (type.equals("minecraft:block_predicate_filter") || type.equals("minecraft:environment_scan")) {
                for (JsonObject probe : StageTestPacks.find(modifier, object -> object.has("offset") || object.has("min"))) {
                    for (String key : List.of("offset", "min", "max")) {
                        if (!probe.has(key)) {
                            continue;
                        }
                        JsonArray vector = probe.getAsJsonArray(key);
                        for (int axis = 0; axis < 3; axis++) {
                            assertTrue(label + " predicate " + key + " " + vector, Math.abs(vector.get(axis).getAsInt()) <= 16);
                        }
                        assertRange(label + " probe x", vector.get(0).getAsInt() + offsetX, vector.get(0).getAsInt() + offsetX);
                        assertRange(label + " probe z", vector.get(2).getAsInt() + offsetZ, vector.get(2).getAsInt() + offsetZ);
                    }
                }
            }
        }
        return new int[]{offsetX, offsetZ};
    }

    private static void assertFeatureWithinWindow(JsonObject feature, Map<String, Integer> footprints, int offsetX, int offsetZ, String label) {
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
        if (!type.equals("minecraft:overlay")) {
            return;
        }
        JsonArray features = body.getAsJsonArray("features");
        for (int index = 1; index < features.size(); index++) {
            JsonArray part = features.get(index).getAsJsonObject().getAsJsonArray("placement");
            int partX = offsetX;
            int partZ = offsetZ;
            int size = 0;
            for (JsonElement element : part) {
                JsonObject modifier = element.getAsJsonObject();
                String modifierType = modifier.get("type").getAsString();
                if (modifierType.equals("minecraft:offset")) {
                    partX += modifier.get("x").getAsInt();
                    partZ += modifier.get("z").getAsInt();
                } else if (modifierType.equals("minecraft:cuboid")) {
                    size = modifier.get("xz_size").getAsInt();
                }
            }
            assertRange(label + " part x", partX, partX + size);
            assertRange(label + " part z", partZ, partZ + size);
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
                    new TemplateVolume(footprint, 4, footprint, List.of(block)), 0, TreeRoots.NONE);
            objects.add(new ObjectTemplates("o" + index, List.of(new TemplateChoice(variant, List.of(0, 1, 2, 3), 1))));
        }
        return objects;
    }

    private static List<ObjectTemplates> columns(int... heights) {
        List<ObjectTemplates> objects = new ArrayList<>();
        for (int index = 0; index < heights.length; index++) {
            List<TemplateBlock> blocks = new ArrayList<>();
            for (int y = 1; y <= heights[index]; y++) {
                blocks.add(new TemplateBlock(1, y, 1, VanillaBlockState.parse("minecraft:tube_coral_block"), null));
            }
            BakedVariant variant = new BakedVariant(NAMESPACE + ":object/c" + index + "/h" + heights[index],
                    new TemplateVolume(3, heights[index] + 1, 3, blocks), 0, TreeRoots.NONE);
            objects.add(new ObjectTemplates("c" + index, List.of(new TemplateChoice(variant, List.of(0, 1, 2, 3), 1))));
        }
        return objects;
    }

    private static int ceiling(JsonObject placed) {
        List<JsonObject> ranges = new ArrayList<>();
        for (JsonObject filter : filters(placed)) {
            ranges.addAll(StageTestPacks.ofType(filter, "minecraft:height_range"));
        }
        assertEquals(placed.toString(), 1, ranges.size());
        return ranges.getFirst().getAsJsonObject("max_inclusive").get("absolute").getAsInt();
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
