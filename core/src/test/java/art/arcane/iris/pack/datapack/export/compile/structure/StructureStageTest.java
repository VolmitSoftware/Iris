package art.arcane.iris.pack.datapack.export.compile.structure;

import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.ir.BinaryResource;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.JsonResource;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.testsupport.CompatTestPlatform;
import art.arcane.iris.testsupport.FakeVanillaDataSource;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.util.nbt.io.NBTUtil;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;

public class StructureStageTest {
    private static final String TOWER = """
            {"type": "minecraft:jigsaw", "biomes": "#bundle:has_structure/tower", "step": "surface_structures",
             "spawn_overrides": {}, "start_pool": "bundle:tower/start", "size": 4, "start_height": {"absolute": 0},
             "project_start_to_heightmap": "WORLD_SURFACE_WG", "max_distance_from_center": 80, "use_expansion_hack": false}
            """;
    private static final String MINESHAFT = """
            {"type": "minecraft:mineshaft", "biomes": "#minecraft:has_structure/mineshaft", "step": "underground_structures",
             "spawn_overrides": {}, "mineshaft_type": "normal"}
            """;
    private static final String VILLAGE = """
            {"type": "minecraft:jigsaw", "biomes": "#minecraft:has_structure/village_plains", "step": "surface_structures",
             "spawn_overrides": {}, "start_pool": "minecraft:village/plains/town_centers", "size": 6, "start_height": {"absolute": 0},
             "project_start_to_heightmap": "WORLD_SURFACE_WG", "max_distance_from_center": 80, "use_expansion_hack": true}
            """;

    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExportTestPacks.BoundPlatform platform = new ExportTestPacks.BoundPlatform();

    @Test
    public void exportedBiomesJoinDerivativeTags() throws Exception {
        runningVanilla(Map.of(
                "data/minecraft/tags/worldgen/biome/has_structure/village_plains.json", "{\"values\": [\"minecraft:plains\"]}",
                "data/minecraft/tags/worldgen/biome/has_structure/pillager_outpost.json", "{\"values\": [\"minecraft:plains\", \"minecraft:desert\"]}"));
        File towns = StructureTestPacks.zip(temporaryFolder.newFolder("imports"), "towns.zip", Map.of(
                "pack.mcmeta", StructureTestPacks.metadata(121, 121),
                "data/towns/tags/worldgen/biome/has_structure/village_meadow.json", "{\"values\": [\"minecraft:plains\", \"#minecraft:is_forest\"]}",
                "data/minecraft/tags/worldgen/biome/has_structure/village_plains.json", "{\"values\": [\"towns:grove\"]}",
                "data/minecraft/tags/worldgen/biome/has_structure/pillager_outpost.json", "{\"replace\": true, \"values\": [\"minecraft:desert\"]}"));
        File pack = StructureTestPacks.pack(temporaryFolder.newFolder("pack"), StructureTestPacks.dimension(List.of(towns), ""), "[]");
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = StructureTestPacks.context(data);
            assertEquals(StructureTestPacks.parse("{\"replace\": false, \"values\": [\"" + StructureTestPacks.MAPPED + "\"]}"),
                    StructureTestPacks.json(context).get("data/minecraft/tags/worldgen/biome/has_structure/village_plains.json"));

            new StructureStage().apply(context);

            SortedMap<String, JsonElement> json = StructureTestPacks.json(context);
            assertEquals(StructureTestPacks.parse("{\"replace\": false, \"values\": [\"minecraft:plains\", \"#minecraft:is_forest\", \""
                            + StructureTestPacks.MAPPED + "\"]}"),
                    json.get("data/towns/tags/worldgen/biome/has_structure/village_meadow.json"));
            assertEquals(StructureTestPacks.parse("{\"replace\": false, \"values\": [\"" + StructureTestPacks.MAPPED + "\", \"towns:grove\"]}"),
                    json.get("data/minecraft/tags/worldgen/biome/has_structure/village_plains.json"));
            assertEquals(StructureTestPacks.parse("{\"replace\": true, \"values\": [\"minecraft:desert\", \"" + StructureTestPacks.DESERT + "\"]}"),
                    json.get("data/minecraft/tags/worldgen/biome/has_structure/pillager_outpost.json"));
        } finally {
            data.close();
        }
    }

    @Test
    public void disabledStructurePointsAtEmptyTag() throws Exception {
        runningVanilla(Map.of(
                "data/minecraft/worldgen/structure/village_plains.json", VILLAGE,
                "data/minecraft/worldgen/structure/mineshaft.json", MINESHAFT));
        File bundle = StructureTestPacks.zip(temporaryFolder.newFolder("imports"), "bundle.zip", Map.of(
                "pack.mcmeta", StructureTestPacks.metadata(121, 121),
                "data/bundle/worldgen/structure/tower.json", TOWER,
                "data/bundle/worldgen/structure/tower_big.json", TOWER,
                "data/bundle/worldgen/structure/towers.json", TOWER));
        File pack = StructureTestPacks.pack(temporaryFolder.newFolder("pack"), StructureTestPacks.dimension(List.of(bundle),
                "\"importedStructures\": {\"disabled\": [\"bundle:tower\", \"minecraft:village\"]}"), "[]");
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = StructureTestPacks.compiled(data);

            SortedMap<String, JsonElement> json = StructureTestPacks.json(context);
            SortedMap<String, String> binaries = StructureTestPacks.binaries(context);
            String disabledTag = "#" + StructureTestPacks.NAMESPACE + ":" + NativeStructureOverrides.DISABLED_TAG;
            for (String path : List.of("data/bundle/worldgen/structure/tower.json", "data/bundle/worldgen/structure/tower_big.json",
                    "data/minecraft/worldgen/structure/village_plains.json")) {
                assertEquals(path, disabledTag, json.get(path).getAsJsonObject().get("biomes").getAsString());
                assertFalse(path, binaries.containsKey(path));
            }
            assertEquals("bundle:tower/start", json.get("data/bundle/worldgen/structure/tower.json").getAsJsonObject().get("start_pool").getAsString());
            assertTrue(binaries.containsKey("data/bundle/worldgen/structure/towers.json"));
            assertFalse(json.containsKey("data/minecraft/worldgen/structure/mineshaft.json"));
            assertEquals(StructureTestPacks.parse("{\"replace\": false, \"values\": []}"),
                    json.get("data/iris_export/tags/worldgen/biome/" + NativeStructureOverrides.DISABLED_TAG + ".json"));
        } finally {
            data.close();
        }
    }

    @Test
    public void adjustmentsShiftJigsawStartsAndReportTheRest() throws Exception {
        runningVanilla(Map.of("data/minecraft/worldgen/structure/mineshaft.json", MINESHAFT));
        File bundle = StructureTestPacks.zip(temporaryFolder.newFolder("imports"), "bundle.zip", Map.of(
                "pack.mcmeta", StructureTestPacks.metadata(121, 121),
                "data/bundle/worldgen/structure/tower.json", TOWER));
        File pack = StructureTestPacks.pack(temporaryFolder.newFolder("pack"), StructureTestPacks.dimension(List.of(bundle), """
                "importedStructures": {"adjustments": [
                  {"match": ["bundle:"], "yShift": -8},
                  {"match": ["minecraft:mineshaft"], "yShift": 4, "stilt": {}}]}
                """), "[]");
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = StructureTestPacks.compiled(data);

            JsonObject tower = StructureTestPacks.json(context).get("data/bundle/worldgen/structure/tower.json").getAsJsonObject();
            assertEquals(StructureTestPacks.parse("{\"absolute\": -8}"), tower.get("start_height"));
            assertEquals("#bundle:has_structure/tower", tower.get("biomes").getAsString());
            assertFalse(StructureTestPacks.json(context).containsKey("data/minecraft/worldgen/structure/mineshaft.json"));
            List<String> dropped = new ArrayList<>();
            for (ExportDiagnostic diagnostic : StructureTestPacks.diagnostics(context, ExportLimit.STRUCTURE_ADJUSTMENT)) {
                assertEquals("minecraft:mineshaft", diagnostic.resource());
                dropped.add(diagnostic.message());
            }
            assertEquals(dropped.toString(), 2, dropped.size());
        } finally {
            data.close();
        }
    }

    @Test
    public void nativePlacementScopedToRegionBiomes() throws Exception {
        File bundle = StructureTestPacks.zip(temporaryFolder.newFolder("imports"), "bundle.zip", Map.of(
                "pack.mcmeta", StructureTestPacks.metadata(121, 121),
                "data/bundle/worldgen/structure/tower.json", TOWER));
        File pack = StructureTestPacks.pack(temporaryFolder.newFolder("pack"), StructureTestPacks.dimension(List.of(bundle), ""), """
                [{"nativeStructures": [{"structure": "bundle:tower", "weight": 3}], "spacing": 24, "separation": 6, "salt": 776215551}]
                """);
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = StructureTestPacks.compiled(data);

            SortedMap<String, JsonElement> json = StructureTestPacks.json(context);
            String copyId = "iris_export:placement/region/second/0/bundle/tower";
            JsonObject copy = json.get("data/iris_export/worldgen/structure/placement/region/second/0/bundle/tower.json").getAsJsonObject();
            assertEquals("#iris_export:placement/region/second", copy.get("biomes").getAsString());
            assertEquals("bundle:tower/start", copy.get("start_pool").getAsString());
            assertEquals(StructureTestPacks.parse("{\"replace\": false, \"values\": [\"" + StructureTestPacks.DESERT + "\", \""
                    + StructureTestPacks.OCEAN + "\"]}"), json.get("data/iris_export/tags/worldgen/biome/placement/region/second.json"));
            assertEquals(StructureTestPacks.parse("""
                    {"structures": [{"structure": "%s", "weight": 3}],
                     "placement": {"type": "minecraft:random_spread", "spacing": 24, "separation": 6, "salt": 776215551}}
                    """.formatted(copyId)), json.get("data/iris_export/worldgen/structure_set/placement/region/second/0.json"));
            assertTrue(StructureTestPacks.binaries(context).containsKey("data/bundle/worldgen/structure/tower.json"));
            List<ExportDiagnostic> scope = StructureTestPacks.diagnostics(context, ExportLimit.STRUCTURE_PLACEMENT);
            assertEquals(scope.toString(), 1, scope.size());
            assertEquals("regions/second/structures[0]", scope.getFirst().resource());
            assertTrue(scope.getFirst().message(), scope.getFirst().message().contains(StructureTestPacks.OCEAN));
        } finally {
            data.close();
        }
    }

    @Test
    public void jigsawPlacementUsesIrisGrid() throws Exception {
        File pack = StructureTestPacks.pack(temporaryFolder.newFolder("pack"), StructureTestPacks.dimension(List.of(), """
                "structures": [{"structures": ["test_structure"], "spacing": 40, "separation": 10, "salt": 91834401}]
                """), "[]");
        writeJigsawStructure(pack);
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = StructureTestPacks.compiled(data);

            SortedMap<String, JsonElement> json = StructureTestPacks.json(context);
            String resourcePath = "placement/dimension/0/test_structure";
            assertEquals(StructureTestPacks.parse("""
                    {"structures": [{"structure": "iris_export:%s", "weight": 1}],
                     "placement": {"type": "minecraft:random_spread", "spacing": 40, "separation": 10, "salt": 91834401}}
                    """.formatted(resourcePath)), json.get("data/iris_export/worldgen/structure_set/placement/dimension/0.json"));
            JsonObject structure = json.get("data/iris_export/worldgen/structure/" + resourcePath + ".json").getAsJsonObject();
            assertEquals("minecraft:jigsaw", structure.get("type").getAsString());
            assertEquals("#iris_export:placement/dimension", structure.get("biomes").getAsString());
            assertEquals("iris_export:" + resourcePath + "/pool/pools/start", structure.get("start_pool").getAsString());
            assertEquals("WORLD_SURFACE_WG", structure.get("project_start_to_heightmap").getAsString());
            assertFalse(json.containsKey("data/iris_export/worldgen/structure_set/" + resourcePath + ".json"));
            assertFalse(json.containsKey("data/iris_export/tags/worldgen/biome/" + resourcePath + ".json"));
            assertTrue(json.containsKey("data/iris_export/worldgen/template_pool/" + resourcePath + "/pool/pools/start.json"));
            assertEquals(StructureTestPacks.parse("{\"replace\": false, \"values\": [\"" + StructureTestPacks.DESERT + "\", \""
                    + StructureTestPacks.FOREST + "\", \"" + StructureTestPacks.MAPPED + "\", \"" + StructureTestPacks.OCEAN + "\"]}"),
                    json.get("data/iris_export/tags/worldgen/biome/placement/dimension.json"));
            BinaryResource template = binary(context, "data/iris_export/structure/" + resourcePath + "/piece/pieces/start.nbt");
            CompoundTag nbt = (CompoundTag) NBTUtil.read(new ByteArrayInputStream(template.bytes()), true).getTag();
            assertEquals(5023, nbt.getInt("DataVersion"));
            assertTrue(StructureTestPacks.diagnostics(context, ExportLimit.JIGSAW_STRUCTURE).toString(),
                    StructureTestPacks.diagnostics(context, ExportLimit.JIGSAW_STRUCTURE).isEmpty());
        } finally {
            data.close();
        }
    }

    @Test
    public void stageOutputIsDeterministic() throws Exception {
        File bundle = StructureTestPacks.zip(temporaryFolder.newFolder("imports"), "bundle.zip", Map.of(
                "pack.mcmeta", StructureTestPacks.metadata(121, 121),
                "data/bundle/worldgen/structure/tower.json", TOWER,
                "data/bundle/worldgen/structure/towers.json", TOWER,
                "data/bundle/structure/tower.nbt", "template",
                "data/bundle/tags/worldgen/biome/has_structure/tower.json", "{\"values\": [\"minecraft:desert\", \"minecraft:plains\"]}"));
        File pack = StructureTestPacks.pack(temporaryFolder.newFolder("pack"), StructureTestPacks.dimension(List.of(bundle), """
                "importedStructures": {"disabled": ["bundle:towers"]},
                "structures": [{"structures": ["test_structure"], "distribution": "DENSITY", "density": 0.05, "underground": true,
                                "minHeight": -40, "maxHeight": 10}]
                """), """
                [{"nativeStructures": [{"structure": "bundle:tower"}], "distribution": "CONCENTRIC_RINGS"}]
                """);
        writeJigsawStructure(pack);

        List<String> first = snapshot(pack);
        List<String> second = snapshot(pack);

        assertEquals(first, second);
        assertTrue(first.toString(), first.stream().anyMatch(line -> line.startsWith("data/iris_export/worldgen/structure_set/placement/dimension/0.json")));
        assertTrue(first.toString(), first.stream().anyMatch(line -> line.startsWith("data/iris_export/worldgen/structure_set/placement/region/second/0.json")));
    }

    private List<String> snapshot(File pack) {
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = StructureTestPacks.compiled(data);
            List<String> lines = new ArrayList<>();
            for (JsonResource resource : context.jsonResources()) {
                lines.add(resource.path() + " " + resource.json());
            }
            for (BinaryResource resource : context.binaryResources()) {
                lines.add(resource.path() + " " + Arrays.hashCode(resource.bytes()));
            }
            for (ExportDiagnostic diagnostic : context.diagnostics()) {
                lines.add(diagnostic.toString());
            }
            return lines;
        } finally {
            data.close();
        }
    }

    private void runningVanilla(Map<String, String> files) {
        doReturn("26.3").when(IrisPlatforms.get()).minecraftVersion();
        doReturn(FakeVanillaDataSource.of(files)).when(IrisPlatforms.get()).vanillaData();
    }

    private static void writeJigsawStructure(File pack) throws Exception {
        CompatTestPlatform.write(pack, "structures/test_structure.json", """
                {"startPool": "pools/start", "maxDepth": 4, "maxSizeChunks": 4,
                 "compatibility": "VANILLA_PORTABLE", "branchFailurePolicy": "TERMINATE_BRANCH"}
                """);
        CompatTestPlatform.write(pack, "jigsaw-pools/pools/start.json", "{\"pieces\": [{\"piece\": \"pieces/start\", \"weight\": 1}]}");
        CompatTestPlatform.write(pack, "jigsaw-pieces/pieces/start.json", "{\"object\": \"start\"}");
        CompatTestPlatform.writeObjectWithBlocks(pack, "start", "minecraft:stone");
    }

    private static BinaryResource binary(PackCompileContext context, String path) {
        for (BinaryResource resource : context.binaryResources()) {
            if (resource.path().equals(path)) {
                return resource;
            }
        }
        throw new AssertionError("Missing binary resource " + path);
    }
}
