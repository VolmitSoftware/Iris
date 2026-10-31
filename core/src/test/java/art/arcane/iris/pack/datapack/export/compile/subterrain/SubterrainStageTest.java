package art.arcane.iris.pack.datapack.export.compile.subterrain;

import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.compile.structure.PlacementTags;
import art.arcane.iris.pack.datapack.export.emit.UnusedResourcePruner;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.util.nbt.io.NBTUtil;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.volmlib.util.nbt.tag.ListTag;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SubterrainStageTest {
    private static final String POOLS_STRUCTURE = "data/iris_export/worldgen/structure/subterrain/pools.json";
    private static final String POOLS_SET = "data/iris_export/worldgen/structure_set/subterrain/pools.json";
    private static final String POOLS_POOL = "data/iris_export/worldgen/template_pool/subterrain/pools.json";
    private static final String TUBES_STRUCTURE = "data/iris_export/worldgen/structure/subterrain/tubes.json";
    private static final String TUBES_SET = "data/iris_export/worldgen/structure_set/subterrain/tubes.json";
    private static final String TUBES_POOL = "data/iris_export/worldgen/template_pool/subterrain/tubes.json";
    private static final String DIMENSION_TAG = "data/iris_export/tags/worldgen/biome/placement/dimension.json";
    private static final String SECOND_TAG = "data/iris_export/tags/worldgen/biome/placement/region/second.json";
    private static final String ANCHOR = "iris_export:subterrain/anchor";

    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExportTestPacks.BoundPlatform platform = new ExportTestPacks.BoundPlatform();

    @Test
    public void roomsExportAsCenteredUndergroundJigsawStructures() throws Exception {
        IrisData data = IrisData.openRuntime(SubterrainTestPacks.pack(temporaryFolder.newFolder("pack"), SubterrainTestPacks.CENOTE));
        try {
            PackCompileContext context = SubterrainTestPacks.context(data, DataVersion.V26_3);

            new SubterrainStage().apply(context);

            SortedMap<String, JsonElement> json = SubterrainTestPacks.json(context);
            JsonObject structure = json.get(POOLS_STRUCTURE).getAsJsonObject();
            assertEquals("minecraft:jigsaw", structure.get("type").getAsString());
            assertEquals("#iris_export:placement/dimension", structure.get("biomes").getAsString());
            assertEquals("underground_structures", structure.get("step").getAsString());
            assertEquals("iris_export:subterrain/pools", structure.get("start_pool").getAsString());
            assertEquals(ANCHOR, structure.get("start_jigsaw_name").getAsString());
            assertEquals("none", structure.get("terrain_adaptation").getAsString());
            assertFalse(structure.has("project_start_to_heightmap"));
            assertEquals(10, structure.get("max_distance_from_center").getAsInt());
            int baseDepth = (int) Math.ceil(Math.min(24, 8 * 0.45) * 0.5);
            assertEquals(JsonParser.parseString("{\"type\":\"minecraft:uniform\",\"min_inclusive\":{\"absolute\":"
                    + (-60 + 12 / 2 + 3 + baseDepth + 1) + "},\"max_inclusive\":{\"absolute\":" + (40 - (12 - 12 / 2 + 3) + 1) + "}}"),
                    structure.get("start_height"));
            assertEquals(JsonParser.parseString("{\"placement\":{\"type\":\"minecraft:random_spread\",\"salt\":" + salt("pools")
                            + ",\"separation\":2,\"spacing\":4},\"structures\":[{\"structure\":\"iris_export:subterrain/pools\",\"weight\":1}]}"),
                    json.get(POOLS_SET));
            JsonArray dimensionBiomes = json.get(DIMENSION_TAG).getAsJsonObject().getAsJsonArray("values");
            assertEquals(SubterrainTestPacks.EXPORTED.size(), dimensionBiomes.size());

            JsonArray elements = json.get(POOLS_POOL).getAsJsonObject().getAsJsonArray("elements");
            assertEquals(SubterrainBaker.VARIANTS, elements.size());
            SortedMap<String, byte[]> binaries = SubterrainTestPacks.binaries(context);
            for (int index = 0; index < elements.size(); index++) {
                JsonObject element = elements.get(index).getAsJsonObject().getAsJsonObject("element");
                assertEquals("minecraft:list_pool_element", element.get("element_type").getAsString());
                JsonArray parts = element.getAsJsonArray("elements");
                assertEquals(2, parts.size());
                String core = "iris_export:subterrain/pools/" + index + "_core";
                String shell = "iris_export:subterrain/pools/" + index + "_shell";
                assertEquals(core, parts.get(0).getAsJsonObject().get("location").getAsString());
                assertEquals(shell, parts.get(1).getAsJsonObject().get("location").getAsString());
                assertEquals(0, parts.get(0).getAsJsonObject().getAsJsonObject("processors").getAsJsonArray("processors").size());
                assertEquals(guard("{\"id\":\"minecraft:cave_air\"}", "{\"id\":\"minecraft:water\"}", "{\"id\":\"minecraft:lava\"}"),
                        parts.get(1).getAsJsonObject().get("processors"));
                CompoundTag coreTemplate = template(binaries, "data/iris_export/structure/subterrain/pools/" + index + "_core.nbt");
                assertEquals(DataVersion.V26_3.getWorldDataVersion(), coreTemplate.getInt("DataVersion"));
                CompoundTag jigsaw = anchor(coreTemplate);
                assertEquals(ANCHOR, jigsaw.getString("name"));
                assertEquals("minecraft:empty", jigsaw.getString("pool"));
                assertNotNull(template(binaries, "data/iris_export/structure/subterrain/pools/" + index + "_shell.nbt"));
            }
            assertEquals(1, matching(context, ExportLimit.SUBTERRAIN_VARIANT, "pools"));
            assertEquals(1, matching(context, ExportLimit.SUBTERRAIN_TERRAIN, "pools"));
            assertEquals(0, matching(context, ExportLimit.SUBTERRAIN_BIOME, "pools"));
        } finally {
            data.close();
        }
    }

    @Test
    public void regionRestrictedRoomsUseTheRegionPlacementTag() throws Exception {
        IrisData data = IrisData.openRuntime(SubterrainTestPacks.pack(temporaryFolder.newFolder("pack"), SubterrainTestPacks.TUBE));
        try {
            PackCompileContext context = SubterrainTestPacks.context(data, DataVersion.V26_3);

            new SubterrainStage().apply(context);

            SortedMap<String, JsonElement> json = SubterrainTestPacks.json(context);
            JsonObject structure = json.get(TUBES_STRUCTURE).getAsJsonObject();
            assertEquals("#iris_export:placement/region/second", structure.get("biomes").getAsString());
            assertEquals(JsonParser.parseString("{\"replace\":false,\"values\":[\"" + SubterrainTestPacks.DESERT + "\",\""
                    + SubterrainTestPacks.OCEAN + "\"]}"), json.get(SECOND_TAG));
            assertFalse(json.containsKey(DIMENSION_TAG));
            JsonObject placement = json.get(TUBES_SET).getAsJsonObject().getAsJsonObject("placement");
            assertEquals(20, placement.get("spacing").getAsInt());
            assertEquals(10, placement.get("separation").getAsInt());
            assertEquals(0.5f, placement.get("frequency").getAsFloat(), 0.0f);
            assertEquals(1, matching(context, ExportLimit.STRUCTURE_PLACEMENT, "tubes"));
            assertEquals(1, matching(context, ExportLimit.OBJECT_BOUNDS, "tubes"));
        } finally {
            data.close();
        }
    }

    @Test
    public void existingPlacementTagIsReused() throws Exception {
        IrisData data = IrisData.openRuntime(SubterrainTestPacks.pack(temporaryFolder.newFolder("pack"), SubterrainTestPacks.TUBE));
        try {
            PackCompileContext context = SubterrainTestPacks.context(data, DataVersion.V26_3);
            PlacementTags.collect(context).regionTag("second").orElseThrow();
            int before = context.jsonResources().size();

            new SubterrainStage().apply(context);

            SortedMap<String, JsonElement> json = SubterrainTestPacks.json(context);
            assertTrue(json.containsKey(SECOND_TAG));
            assertEquals(before + 3, context.jsonResources().size());
        } finally {
            data.close();
        }
    }

    @Test
    public void roomBiomeIsReported() throws Exception {
        IrisData data = IrisData.openRuntime(SubterrainTestPacks.pack(temporaryFolder.newFolder("pack"), SubterrainTestPacks.ROOM_CENOTE));
        try {
            PackCompileContext context = SubterrainTestPacks.context(data, DataVersion.V26_3);

            new SubterrainStage().apply(context);

            assertEquals(1, matching(context, ExportLimit.SUBTERRAIN_BIOME, "grottos"));
            for (ExportDiagnostic diagnostic : SubterrainTestPacks.diagnostics(context.diagnostics(), ExportLimit.SUBTERRAIN_BIOME)) {
                assertTrue(diagnostic.message(), diagnostic.message().contains("'room'"));
            }
        } finally {
            data.close();
        }
    }

    @Test
    public void disabledAndUnplaceableFeaturesExportNothing() throws Exception {
        IrisData data = IrisData.openRuntime(SubterrainTestPacks.pack(temporaryFolder.newFolder("pack"),
                SubterrainTestPacks.DISABLED, SubterrainTestPacks.SHALLOW));
        try {
            PackCompileContext context = SubterrainTestPacks.context(data, DataVersion.V26_3);

            new SubterrainStage().apply(context);

            assertTrue(SubterrainTestPacks.binaries(context).isEmpty());
            for (String path : SubterrainTestPacks.json(context).keySet()) {
                assertFalse(path, path.contains("/subterrain/"));
            }
            assertEquals(1, matching(context, ExportLimit.STRUCTURE_PLACEMENT, "shallow"));
            assertEquals(0, matching(context, ExportLimit.SUBTERRAIN_VARIANT, "off"));
        } finally {
            data.close();
        }
    }

    @Test
    public void dimensionWithoutCarvingExportsNoRooms() throws Exception {
        IrisData data = IrisData.openRuntime(SubterrainTestPacks.packWith(temporaryFolder.newFolder("pack"), "\"carvingEnabled\": false",
                SubterrainTestPacks.CENOTE));
        try {
            PackCompileContext context = SubterrainTestPacks.context(data, DataVersion.V26_3);

            new SubterrainStage().apply(context);

            assertTrue(context.jsonResources().isEmpty());
            assertTrue(context.binaryResources().isEmpty());
            assertTrue(context.diagnostics().isEmpty());
        } finally {
            data.close();
        }
    }

    @Test
    public void prunerKeepsPlacedRoomsAndDropsUnplacedRooms() throws Exception {
        IrisData data = IrisData.openRuntime(SubterrainTestPacks.pack(temporaryFolder.newFolder("pack"),
                SubterrainTestPacks.CENOTE, SubterrainTestPacks.TUBE));
        try {
            PackCompileContext context = SubterrainTestPacks.context(data, DataVersion.V26_3);
            new SubterrainStage().apply(context);
            SortedMap<String, byte[]> files = files(context);
            List<String> rooms = rooms(files);
            assertEquals(2 * (3 + 2 * SubterrainBaker.VARIANTS) + 2, rooms.size());

            UnusedResourcePruner.Pruned placed = UnusedResourcePruner.prune(files);

            for (String path : rooms) {
                assertTrue(path, placed.files().containsKey(path));
            }

            files.put(SECOND_TAG, "{\"replace\":false,\"values\":[]}".getBytes(StandardCharsets.UTF_8));
            UnusedResourcePruner.Pruned unplaced = UnusedResourcePruner.prune(files);

            for (String path : rooms) {
                boolean tubes = path.contains("/subterrain/tubes") || path.equals(SECOND_TAG);
                assertEquals(path, !tubes, unplaced.files().containsKey(path));
            }
        } finally {
            data.close();
        }
    }

    private static int salt(String id) {
        long salt = 0xcbf29ce484222325L;
        for (int index = 0; index < id.length(); index++) {
            salt = (salt ^ id.charAt(index)) * 0x100000001b3L;
        }
        return (int) (salt ^ (salt >>> 32)) & Integer.MAX_VALUE;
    }

    private static JsonElement guard(String air, String water, String lava) {
        return JsonParser.parseString("{\"processors\":[{\"processor_type\":\"minecraft:rule\",\"rules\":["
                + rule("{\"predicate_type\":\"minecraft:tag_match\",\"tag\":\"minecraft:air\"}", air) + ","
                + rule("{\"predicate_type\":\"minecraft:block_match\",\"block\":\"minecraft:water\"}", water) + ","
                + rule("{\"predicate_type\":\"minecraft:block_match\",\"block\":\"minecraft:lava\"}", lava) + "]}]}");
    }

    private static String rule(String location, String output) {
        return "{\"input_predicate\":{\"predicate_type\":\"minecraft:always_true\"},\"location_predicate\":" + location
                + ",\"output_state\":" + output + "}";
    }

    private static CompoundTag template(SortedMap<String, byte[]> binaries, String path) throws IOException {
        byte[] bytes = binaries.get(path);
        assertNotNull(path + " in " + binaries.keySet(), bytes);
        return (CompoundTag) NBTUtil.read(new ByteArrayInputStream(bytes), true).getTag();
    }

    private static CompoundTag anchor(CompoundTag template) {
        ListTag<CompoundTag> palette = template.getListTag("palette").asCompoundTagList();
        CompoundTag found = null;
        for (CompoundTag block : template.getListTag("blocks").asCompoundTagList()) {
            CompoundTag state = palette.get(block.getInt("state"));
            String name = state.containsKey("Name") ? state.getString("Name") : state.getString("id");
            if (name.equals("minecraft:jigsaw")) {
                assertTrue(found == null);
                found = block.getCompoundTag("nbt");
            }
        }
        assertNotNull(found);
        return found;
    }

    private static int matching(PackCompileContext context, ExportLimit limit, String feature) {
        int count = 0;
        for (ExportDiagnostic diagnostic : SubterrainTestPacks.diagnostics(context.diagnostics(), limit)) {
            if (diagnostic.resource().endsWith("/subterrainFeatures/" + feature)) {
                count++;
            }
        }
        return count;
    }

    private static SortedMap<String, byte[]> files(PackCompileContext context) {
        SortedMap<String, byte[]> files = new TreeMap<>();
        files.put("pack.mcmeta", "{\"pack\":{\"description\":\"test\",\"min_format\":121,\"max_format\":121}}".getBytes(StandardCharsets.UTF_8));
        for (Map.Entry<String, JsonElement> entry : SubterrainTestPacks.json(context).entrySet()) {
            files.put(entry.getKey(), entry.getValue().toString().getBytes(StandardCharsets.UTF_8));
        }
        files.putAll(SubterrainTestPacks.binaries(context));
        return files;
    }

    private static List<String> rooms(SortedMap<String, byte[]> files) {
        return files.keySet().stream().filter(path -> path.contains("/subterrain/") || path.contains("/placement/")).toList();
    }
}
