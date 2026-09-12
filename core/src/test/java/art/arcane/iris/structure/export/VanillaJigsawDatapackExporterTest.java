package art.arcane.iris.structure.export;

import art.arcane.iris.structure.graph.StructureGraphResolver;
import art.arcane.iris.pack.value.IrisDirection;
import art.arcane.iris.structure.jigsaw.IrisJigsawBranchFailurePolicy;
import art.arcane.iris.structure.jigsaw.IrisJigsawCompatibility;
import art.arcane.iris.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.jigsaw.IrisJigsawPieceEntry;
import art.arcane.iris.structure.jigsaw.IrisJigsawPieceRules;
import art.arcane.iris.structure.jigsaw.IrisJigsawPool;
import art.arcane.iris.structure.jigsaw.IrisJigsawThemeSet;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectReplace;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.structure.placement.IrisStructure;
import art.arcane.iris.structure.object.ObjectPlaceMode;
import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.nbt.io.NBTUtil;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.volmlib.util.nbt.tag.ListTag;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class VanillaJigsawDatapackExporterTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private static PlatformBlockState stone;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @BeforeClass
    public static void bindPlatform() {
        IrisPlatforms.unbind();
        stone = blockState("minecraft:stone");
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.block(anyString())).thenReturn(stone);
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
    }

    @AfterClass
    public static void unbindPlatform() {
        IrisPlatforms.unbind();
    }

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
        PlatformBlockState air = blockState("minecraft:air");
        IrisObject object = graph.objects.get("objects/start");
        object.setUnsigned(0, 1, 1, air);
        IrisJigsawPiece piece = graph.pieces.get("pieces/start");
        piece.getConnectors().add(new IrisJigsawConnector()
                .setPosition(new IrisPosition(0, 1, 1))
                .setDirection(IrisDirection.WEST_NEGATIVE_X)
                .setTop(IrisDirection.UP_POSITIVE_Y)
                .setPool("pools/terminal")
                .setName("studio:air")
                .setTargetName("studio:door")
                .setFinalState("minecraft:air"));
        piece.getConnectors().add(new IrisJigsawConnector()
                .setPosition(new IrisPosition(2, 1, 1))
                .setDirection(IrisDirection.EAST_POSITIVE_X)
                .setTop(IrisDirection.UP_POSITIVE_Y)
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
        graph.objects.get("objects/start").setUnsignedTile(0, 0, 0, mock(TileData.class));
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
        graph.structure.getEdit().add(new IrisObjectReplace());
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
                graph);
        return VanillaJigsawExportRequest.builder(source, output);
    }

    private static TestGraph connectorGraph() {
        TestGraph graph = new TestGraph();
        IrisObject object = new IrisObject(3, 3, 3);
        object.setUnsigned(1, 1, 0, stone);
        IrisJigsawConnector connector = new IrisJigsawConnector()
                .setPosition(new IrisPosition(1, 1, 0))
                .setDirection(IrisDirection.NORTH_NEGATIVE_Z)
                .setTop(IrisDirection.UP_POSITIVE_Y)
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
        IrisObject object = new IrisObject(3, 3, 3);
        object.setUnsigned(1, 1, 1, stone);
        graph.objects.put("objects/start", object);
        graph.pieces.put("pieces/start", piece("objects/start"));
        graph.pools.put("pools/start", pool(entry("pieces/start", 1)));
        graph.structure = structure("pools/start");
        return graph;
    }

    private static IrisStructure structure(String startPool) {
        IrisStructure structure = new IrisStructure();
        structure.setLoadKey("test_structure");
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

    private static PlatformBlockState blockState(String key) {
        PlatformBlockState state = mock(PlatformBlockState.class);
        when(state.key()).thenReturn(key);
        when(state.namespace()).thenReturn(key.substring(0, key.indexOf(':')));
        return state;
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
        private final Map<String, IrisObject> objects = new LinkedHashMap<>();
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
        public IrisObject loadObject(String key) {
            return objects.get(key);
        }
    }
}
