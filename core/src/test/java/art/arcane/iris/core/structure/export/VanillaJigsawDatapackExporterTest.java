package art.arcane.iris.core.structure.export;

import art.arcane.iris.builtin.anchor.ObjectPlaceMode;
import art.arcane.iris.builtin.blocks.ObjectReplace;
import art.arcane.iris.engine.framework.structure.StructureGraphResolver;
import art.arcane.iris.engine.object.structure.jigsaw.JigsawDirection;
import art.arcane.iris.engine.framework.structure.ObjectFrame;
import com.volmit.block.BlockState;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.BlockEntityData;
import com.volmit.resource.ResourceId;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawBranchFailurePolicy;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawCompatibility;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPieceEntry;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPieceRules;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPool;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawThemeSet;
import art.arcane.iris.engine.object.structure.IrisStructure;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.nbt.io.NBTUtil;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.volmlib.util.nbt.tag.ListTag;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class VanillaJigsawDatapackExporterTest {
    private static final BlockState STONE = new BlockState("minecraft:stone");
    private static final BlockState AIR = new BlockState("minecraft:air");

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void exportsComplete26_2DirectoryAndConnectorTemplate() throws Exception {
        TestGraph graph = connectorGraph();
        Path output = temporaryFolder.getRoot().toPath().resolve("village-pack");
        VanillaJigsawExportRequest request = request(graph, output)
                .namespace("studio")
                .resourcePath("village/test")
                .build();

        VanillaJigsawDatapackExporter exporter = new VanillaJigsawDatapackExporter();
        VanillaJigsawExportValidation validation = exporter.validate(request);
        assertTrue(validation.diagnostics().toString(), validation.isExportable());
        assertEquals(8, validation.plannedResources().size());
        assertFalse(Files.exists(output));

        VanillaJigsawExportResult result = exporter.export(request);

        assertTrue(result.diagnostics().toString(), result.isSuccess());
        assertEquals(8, result.resources().size());
        assertTrue(Files.isRegularFile(output.resolve("pack.mcmeta")));
        assertTrue(Files.isRegularFile(output.resolve(
                "data/studio/worldgen/template_pool/village/test/pool/pools/start.json")));
        assertTrue(Files.isRegularFile(output.resolve(
                "data/studio/worldgen/processor_list/village/test/empty.json")));

        JsonObject pack = json(output.resolve("pack.mcmeta")).getAsJsonObject("pack");
        assertEquals(107, pack.get("min_format").getAsJsonArray().get(0).getAsInt());
        assertEquals(1, pack.get("min_format").getAsJsonArray().get(1).getAsInt());
        assertEquals(107, pack.get("max_format").getAsInt());

        JsonObject structure = json(output.resolve(
                "data/studio/worldgen/structure/village/test.json"));
        assertEquals("minecraft:jigsaw", structure.get("type").getAsString());
        assertEquals("#studio:village/test", structure.get("biomes").getAsString());
        assertEquals(64, structure.getAsJsonObject("max_distance_from_center")
                .get("horizontal").getAsInt());
        assertEquals(4064, structure.getAsJsonObject("max_distance_from_center")
                .get("vertical").getAsInt());
        assertEquals("studio:village/test/pool/pools/start", structure.get("start_pool").getAsString());

        Path templatePath = output.resolve(
                "data/studio/structure/village/test/piece/pieces/start.nbt");
        CompoundTag template = (CompoundTag) NBTUtil.read(templatePath.toFile()).getTag();
        assertEquals(4903, template.getInt("DataVersion"));
        ListTag<?> blocks = template.getListTag("blocks");
        assertEquals(1, blocks.size());
        CompoundTag block = (CompoundTag) blocks.get(0);
        CompoundTag connector = block.getCompoundTag("nbt");
        assertNotNull(connector);
        assertEquals("minecraft:jigsaw", connector.getString("id"));
        assertEquals("studio:source", connector.getString("name"));
        assertEquals("studio:door", connector.getString("target"));
        assertEquals("studio:village/test/pool/pools/terminal", connector.getString("pool"));
        assertEquals("minecraft:stone", connector.getString("final_state"));
        assertEquals(-3, connector.getInt("selection_priority"));
        assertEquals(-2, connector.getInt("placement_priority"));

        CompoundTag paletteEntry = (CompoundTag) template.getListTag("palette").get(0);
        assertEquals("minecraft:jigsaw", paletteEntry.getString("Name"));
        assertEquals("north_up", paletteEntry.getCompoundTag("Properties").getString("orientation"));
    }

    @Test
    public void exportsZipWithPackFilesAtArchiveRoot() throws Exception {
        TestGraph graph = terminalGraph();
        Path output = temporaryFolder.getRoot().toPath().resolve("stronghold.zip");
        VanillaJigsawExportRequest request = request(graph, output)
                .namespace("studio")
                .resourcePath("stronghold")
                .format(VanillaJigsawExportFormat.ZIP)
                .build();

        VanillaJigsawExportResult result = new VanillaJigsawDatapackExporter().export(request);

        assertTrue(result.diagnostics().toString(), result.isSuccess());
        assertTrue(Files.isRegularFile(output));
        try (ZipFile zip = new ZipFile(output.toFile())) {
            assertNotNull(zip.getEntry("pack.mcmeta"));
            assertNotNull(zip.getEntry("data/studio/worldgen/structure/stronghold.json"));
            assertNotNull(zip.getEntry("data/studio/structure/stronghold/piece/pieces/start.nbt"));
            Enumeration<? extends ZipEntry> entries = zip.entries();
            List<String> names = new ArrayList<>();
            while (entries.hasMoreElements()) {
                names.add(entries.nextElement().getName());
            }
            assertFalse(names.stream().anyMatch(name -> name.startsWith("stronghold/")));
        }
    }

    @Test
    public void preservesExplicitAirAndStructureVoidConnectorFinalStates() throws Exception {
        TestGraph graph = terminalGraph();
        graph.objects.put("objects/start", ObjectContents.plain(3, 3, 3,
                Map.of(new BlockPosition(1, 1, 1), STONE, new BlockPosition(0, 1, 1), AIR), Map.of()));
        IrisJigsawPiece piece = graph.pieces.get("pieces/start");
        piece.getConnectors().add(new IrisJigsawConnector()
                .setPosition(new BlockPosition(0, 1, 1))
                .setDirection(JigsawDirection.WEST_NEGATIVE_X)
                .setTop(JigsawDirection.UP_POSITIVE_Y)
                .setPool("pools/terminal")
                .setName("studio:air")
                .setTargetName("studio:door")
                .setFinalState("minecraft:air"));
        piece.getConnectors().add(new IrisJigsawConnector()
                .setPosition(new BlockPosition(2, 1, 1))
                .setDirection(JigsawDirection.EAST_POSITIVE_X)
                .setTop(JigsawDirection.UP_POSITIVE_Y)
                .setPool("pools/terminal")
                .setName("studio:void")
                .setTargetName("studio:door")
                .setFinalState("minecraft:structure_void"));
        graph.pools.put("pools/terminal", pool(emptyEntry(1)));
        Path output = temporaryFolder.getRoot().toPath().resolve("final-state-pack");

        VanillaJigsawExportResult result = new VanillaJigsawDatapackExporter().export(
                request(graph, output).build());

        assertTrue(result.diagnostics().toString(), result.isSuccess());
        CompoundTag template = (CompoundTag) NBTUtil.read(output.resolve(
                "data/iris/structure/test_structure/piece/pieces/start.nbt").toFile()).getTag();
        ListTag<?> blocks = template.getListTag("blocks");
        List<String> finalStates = new ArrayList<>();
        for (Object value : blocks) {
            CompoundTag block = (CompoundTag) value;
            CompoundTag nbt = block.getCompoundTag("nbt");
            if (nbt != null && "minecraft:jigsaw".equals(nbt.getString("id"))) {
                finalStates.add(nbt.getString("final_state"));
            }
        }
        assertTrue(finalStates.contains("minecraft:air"));
        assertTrue(finalStates.contains("minecraft:structure_void"));
    }

    @Test
    public void rejectsTilePayloadWithoutPublishingPartialOutput() {
        TestGraph graph = terminalGraph();
        graph.objects.put("objects/start", ObjectContents.plain(3, 3, 3, Map.of(new BlockPosition(1, 1, 1), STONE),
                Map.of(new BlockPosition(0, 0, 0), new BlockEntityData(ResourceId.parse("test:tile"), 0, new byte[0]))));
        Path output = temporaryFolder.getRoot().toPath().resolve("rejected-pack");

        VanillaJigsawExportResult result = new VanillaJigsawDatapackExporter().export(
                request(graph, output).build());

        assertEquals(VanillaJigsawExportResult.Status.REJECTED, result.status());
        assertTrue(hasCode(result, VanillaJigsawExportDiagnostic.Code.UNSUPPORTED_TILE_DATA));
        assertFalse(Files.exists(output));
        assertTrue(result.resources().isEmpty());
    }

    @Test
    public void rejectsStrictBranchFailurePolicyAsNonPortableGraphMetadata() {
        TestGraph graph = terminalGraph();
        graph.structure.setBranchFailurePolicy(IrisJigsawBranchFailurePolicy.FAIL_ASSEMBLY);
        Path output = temporaryFolder.getRoot().toPath().resolve("strict-policy-pack");

        VanillaJigsawExportResult result = new VanillaJigsawDatapackExporter().export(
                request(graph, output).build());

        assertEquals(VanillaJigsawExportResult.Status.REJECTED, result.status());
        assertTrue(result.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code() == VanillaJigsawExportDiagnostic.Code.GRAPH_VALIDATION
                        && "NON_PORTABLE_METADATA".equals(diagnostic.resource())));
        assertFalse(Files.exists(output));
    }

    @Test
    public void existingOutputIsPreservedUnlessReplacementIsExplicit() throws Exception {
        TestGraph graph = terminalGraph();
        Path output = temporaryFolder.newFolder("existing-pack").toPath();
        Path sentinel = output.resolve("owner-data.txt");
        Files.writeString(sentinel, "preserve", StandardCharsets.UTF_8);

        VanillaJigsawExportResult rejected = new VanillaJigsawDatapackExporter().export(
                request(graph, output).build());

        assertEquals(VanillaJigsawExportResult.Status.REJECTED, rejected.status());
        assertTrue(hasCode(rejected, VanillaJigsawExportDiagnostic.Code.OUTPUT_EXISTS));
        assertEquals("preserve", Files.readString(sentinel, StandardCharsets.UTF_8));

        VanillaJigsawExportResult replaced = new VanillaJigsawDatapackExporter().export(
                request(graph, output).replaceExisting(true).build());

        assertTrue(replaced.diagnostics().toString(), replaced.isSuccess());
        assertFalse(Files.exists(sentinel));
        assertTrue(Files.isRegularFile(output.resolve("pack.mcmeta")));
    }

    @Test
    public void reportsEveryMaterialVanillaCompatibilityGateTogether() {
        TestGraph graph = connectorGraph();
        graph.structure.setCompatibility(IrisJigsawCompatibility.IRIS_EXTENDED);
        graph.structure.setPlaceMode(ObjectPlaceMode.CENTER_HEIGHT);
        graph.structure.setMaxDepth(21);
        graph.structure.setMaxSizeChunks(9);
        graph.structure.getEdit().add(new ObjectReplace());
        graph.structure.getLoot().add("loot/test");
        graph.structure.getThemeSets().add(new IrisJigsawThemeSet("variant-1", 1));
        graph.structure.setRequireCaps(true);
        graph.pieces.get("pieces/start")
                .setRotatable(false)
                .setCollidable(false)
                .setRules(new IrisJigsawPieceRules().setTerminal(true));
        graph.pieces.get("pieces/start").getThemes().add("variant-1");
        graph.pieces.get("pieces/start").getConnectors().getFirst()
                .setChannel("iris-only")
                .setFinalState("minecraft:air");
        graph.pools.get("pools/start").setMandatoryFallback(true)
                .getPieces().getFirst().setWeight(151).setChance(0.5D);
        Path output = temporaryFolder.getRoot().toPath().resolve("incompatible-pack");

        VanillaJigsawExportResult result = new VanillaJigsawDatapackExporter().export(
                request(graph, output).build());

        assertEquals(VanillaJigsawExportResult.Status.REJECTED, result.status());
        assertTrue(hasCode(result, VanillaJigsawExportDiagnostic.Code.UNSUPPORTED_COMPATIBILITY));
        assertTrue(hasCode(result, VanillaJigsawExportDiagnostic.Code.UNSUPPORTED_PLACE_MODE));
        assertTrue(hasCode(result, VanillaJigsawExportDiagnostic.Code.UNSUPPORTED_EDIT));
        assertTrue(hasCode(result, VanillaJigsawExportDiagnostic.Code.UNSUPPORTED_LOOT));
        assertTrue(hasCode(result, VanillaJigsawExportDiagnostic.Code.UNSUPPORTED_THEME_METADATA));
        assertTrue(hasCode(result, VanillaJigsawExportDiagnostic.Code.UNSUPPORTED_CHANCE));
        assertTrue(hasCode(result, VanillaJigsawExportDiagnostic.Code.UNSUPPORTED_PIECE_RULES));
        assertTrue(hasCode(result, VanillaJigsawExportDiagnostic.Code.UNSUPPORTED_REQUIRED_CAPS));
        assertTrue(hasCode(result, VanillaJigsawExportDiagnostic.Code.INVALID_MAX_DEPTH));
        assertTrue(hasCode(result, VanillaJigsawExportDiagnostic.Code.INVALID_MAX_DISTANCE));
        assertTrue(hasCode(result, VanillaJigsawExportDiagnostic.Code.UNSUPPORTED_FIXED_ROTATION));
        assertTrue(hasCode(result, VanillaJigsawExportDiagnostic.Code.UNSUPPORTED_NON_COLLIDABLE_PIECE));
        assertTrue(hasCode(result, VanillaJigsawExportDiagnostic.Code.UNSUPPORTED_CHANNEL));
        assertTrue(hasCode(result, VanillaJigsawExportDiagnostic.Code.INVALID_POOL_WEIGHT));
        assertTrue(hasCode(result, VanillaJigsawExportDiagnostic.Code.INVALID_CONNECTOR_FINAL_STATE));
        assertFalse(Files.exists(output));
    }

    private VanillaJigsawExportRequest.Builder request(TestGraph graph, Path output) {
        VanillaJigsawExportSource source = VanillaJigsawExportSource.forStructure(
                "test_structure",
                graph.structure,
                graph,
                graph.objects::get);
        return VanillaJigsawExportRequest.builder(source, output);
    }

    private static TestGraph connectorGraph() {
        TestGraph graph = new TestGraph();
        ObjectContents object = ObjectContents.plain(3, 3, 3, Map.of(new BlockPosition(1, 1, 0), STONE), Map.of());
        IrisJigsawConnector connector = new IrisJigsawConnector()
                .setPosition(new BlockPosition(1, 1, 0))
                .setDirection(JigsawDirection.NORTH_NEGATIVE_Z)
                .setTop(JigsawDirection.UP_POSITIVE_Y)
                .setPool("pools/terminal")
                .setName("studio:source")
                .setTargetName("studio:door")
                .setFinalState("minecraft:stone")
                .setSelectionPriority(-3)
                .setPlacementPriority(-2);
        graph.objects.put("objects/start", object);
        graph.pieces.put("pieces/start", piece("objects/start", connector));
        graph.pools.put("pools/start", pool(entry("pieces/start", 1)));
        graph.pools.put("pools/terminal", pool(emptyEntry(1)));
        graph.structure = structure("pools/start");
        return graph;
    }

    private static TestGraph terminalGraph() {
        TestGraph graph = new TestGraph();
        graph.objects.put("objects/start", ObjectContents.plain(3, 3, 3, Map.of(new BlockPosition(1, 1, 1), STONE), Map.of()));
        graph.pieces.put("pieces/start", piece("objects/start"));
        graph.pools.put("pools/start", pool(entry("pieces/start", 1)));
        graph.structure = structure("pools/start");
        return graph;
    }

    private static IrisStructure structure(String startPool) {
        IrisStructure structure = new IrisStructure();
        structure.setStartPool(startPool);
        structure.setMaxDepth(4);
        structure.setMaxSizeChunks(4);
        structure.setCompatibility(IrisJigsawCompatibility.VANILLA_PORTABLE);
        structure.setBranchFailurePolicy(IrisJigsawBranchFailurePolicy.TERMINATE_BRANCH);
        return structure;
    }

    private static IrisJigsawPiece piece(String object, IrisJigsawConnector... connectors) {
        return new IrisJigsawPiece()
                .setObject(object)
                .setConnectors(new KList<>(connectors))
                .setRotatable(true);
    }

    private static IrisJigsawPool pool(IrisJigsawPieceEntry... entries) {
        return new IrisJigsawPool().setPieces(new KList<>(entries));
    }

    private static IrisJigsawPieceEntry entry(String piece, int weight) {
        return new IrisJigsawPieceEntry().setPiece(piece).setWeight(weight);
    }

    private static IrisJigsawPieceEntry emptyEntry(int weight) {
        return new IrisJigsawPieceEntry().setEmpty(true).setWeight(weight);
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static boolean hasCode(
            VanillaJigsawExportResult result,
            VanillaJigsawExportDiagnostic.Code code
    ) {
        return result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code() == code);
    }

    private static final class TestGraph implements StructureGraphResolver {
        private final Map<String, IrisJigsawPool> pools = new LinkedHashMap<>();
        private final Map<String, IrisJigsawPiece> pieces = new LinkedHashMap<>();
        private final Map<String, ObjectContents> objects = new LinkedHashMap<>();
        private IrisStructure structure;

        @Override
        public IrisJigsawPool loadPool(String key) {
            return pools.get(key);
        }

        @Override
        public IrisJigsawPiece loadPiece(String key) {
            return pieces.get(key);
        }

        @Override
        public ObjectFrame loadObject(String key) {
            ObjectContents contents = objects.get(key);
            return contents == null ? null : new ObjectFrame(contents.width(), contents.height(), contents.depth());
        }
    }
}
